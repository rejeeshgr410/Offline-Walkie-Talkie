package com.example.offlinewalkietalkie.group

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** Everything the manager needs from the radio layer. Kept narrow so tests can fake it. */
interface GroupTransportPort {
    suspend fun connect(endpointId: String)
    suspend fun acceptConnection(endpointId: String)
    suspend fun rejectConnection(endpointId: String)
    suspend fun disconnect(endpointId: String)
    suspend fun sendControl(endpointId: String, payload: ByteArray)
    /** Republish who we are, so peers see our group id and role in discovery. */
    suspend fun publishAdvert(advert: GroupAdvert)

    /**
     * Stop claiming group membership in discovery. Without this a device that left keeps
     * advertising the old group id and its former peers dial it forever.
     */
    suspend fun clearAdvert()
}

/**
 * Owns group membership and the connection state machine for every peer.
 *
 * Deliberately free of Android and coroutine-timer dependencies: time enters through
 * [tick] and randomness through the id factory, so multi-node scenarios are reproducible
 * in unit tests.
 */
class GroupSessionManager(
    private val transport: GroupTransportPort,
    private val localDisplayName: String,
    localMemberId: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val idFactory: (Int) -> String = { len -> randomId(len) }
) {
    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val RETRY_BACKOFF_MS = 3_000L
        const val MAX_ATTEMPTS = 3
        const val HEARTBEAT_INTERVAL_MS = 3_000L

        /** Four missed beats before we consider the coordinator gone. */
        const val COORDINATOR_TIMEOUT_MS = 12_000L

        private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomId(length: Int): String =
            (1..length).map { ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)] }.joinToString("")
    }

    val localMemberId: String = localMemberId ?: idFactory(GroupAdvert.MEMBER_ID_LENGTH)

    private val _state = MutableStateFlow(GroupState(localMemberId = this.localMemberId))
    val state: StateFlow<GroupState> = _state.asStateFlow()

    /** memberId -> when the in-flight connection attempt started. */
    private val attemptStartedAt = mutableMapOf<String, Long>()

    /** memberId -> earliest time a retry may fire. */
    private val retryNotBefore = mutableMapOf<String, Long>()

    private var lastCoordinatorBeatAt = 0L
    private var lastHeartbeatSentAt = 0L

    /**
     * Whether we have ever actually reached the coordinator. A joiner that never got a
     * link must not stage a coup — that turns one failed join into two rival groups.
     */
    private var hasSyncedWithCoordinator = false

    // ---------------------------------------------------------------- group lifecycle

    /** Become the coordinator of a brand new group. */
    suspend fun createGroup(name: String) {
        val group = GroupIdentity(
            groupId = idFactory(GroupAdvert.GROUP_ID_LENGTH),
            name = name,
            coordinatorId = localMemberId,
            epoch = 1
        )
        val me = GroupMember(
            memberId = localMemberId,
            displayName = localDisplayName,
            role = GroupRole.COORDINATOR,
            state = MemberState.CONNECTED,
            lastSeenEpochMs = now()
        )
        _state.value = GroupState(
            group = group,
            localMemberId = localMemberId,
            members = listOf(me),
            isForming = true
        )
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        publishAdvert()
    }

    /**
     * Join a group we saw in discovery. The link to the coordinator is opened here; the
     * roster and the rest of the mesh follow once it is up.
     */
    suspend fun joinGroup(advert: GroupAdvert, endpointId: String) {
        val group = GroupIdentity(
            groupId = advert.groupId,
            name = advert.displayName,
            coordinatorId = advert.memberId,
            epoch = 1
        )
        val me = GroupMember(
            memberId = localMemberId,
            displayName = localDisplayName,
            role = GroupRole.MEMBER,
            state = MemberState.CONNECTED,
            lastSeenEpochMs = now()
        )
        val target = GroupMember(
            memberId = advert.memberId,
            displayName = advert.displayName,
            endpointId = endpointId,
            role = advert.role,
            state = MemberState.CONNECTING,
            lastSeenEpochMs = now()
        )
        _state.value = GroupState(
            group = group,
            localMemberId = localMemberId,
            members = listOf(me, target),
            isForming = true
        )
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = false
        attemptStartedAt[advert.memberId] = now()
        publishAdvert()
        transport.connect(endpointId)
    }

    /** Deliberate departure: tell everyone, then drop every link. */
    suspend fun leaveGroup() {
        val snapshot = _state.value
        val payload = GroupMessage.Leave(localMemberId).encode()
        snapshot.connected.forEach { member ->
            member.endpointId?.let { runCatchingSend(it, payload) }
        }
        snapshot.others.mapNotNull { it.endpointId }.forEach { transport.disconnect(it) }
        attemptStartedAt.clear()
        retryNotBefore.clear()
        transport.clearAdvert()
        hasSyncedWithCoordinator = false
        _state.value = GroupState(localMemberId = localMemberId)
    }

    // ------------------------------------------------------------------ radio events

    /**
     * A peer appeared in discovery. Only adverts for our own group are acted on; the
     * rest are surfaced separately for the "join a group" picker.
     */
    suspend fun onEndpointDiscovered(endpointId: String, advert: GroupAdvert) {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        if (advert.groupId != group.groupId) return
        if (advert.memberId == localMemberId) return

        val existing = snapshot.member(advert.memberId)
        if (existing?.state?.isLive == true) return

        updateMember(advert.memberId) { current ->
            (current ?: GroupMember(advert.memberId, advert.displayName)).copy(
                displayName = advert.displayName,
                endpointId = endpointId,
                role = advert.role,
                state = if (current?.state == MemberState.FAILED) MemberState.FAILED
                else current?.state ?: MemberState.DISCOVERED,
                lastSeenEpochMs = now()
            )
        }
        maybeInitiate(advert.memberId)
    }

    fun onEndpointLost(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        if (member.state.isLive) return
        if (member.state == MemberState.DISCOVERED) {
            removeMember(member.memberId)
        }
    }

    /**
     * Someone is dialling us. Members of our own group are let straight in — asking a
     * user to confirm every pairwise link is what made 3+ devices unusable. Strangers
     * still need an explicit decision.
     */
    suspend fun onConnectionRequest(endpointId: String, advert: GroupAdvert?) {
        val snapshot = _state.value
        val group = snapshot.group
        if (advert == null || group == null || advert.groupId != group.groupId) {
            if (advert != null) {
                updateMember(advert.memberId) { current ->
                    (current ?: GroupMember(advert.memberId, advert.displayName)).copy(
                        endpointId = endpointId,
                        displayName = advert.displayName,
                        state = MemberState.PENDING_APPROVAL,
                        lastSeenEpochMs = now()
                    )
                }
            }
            return
        }

        val known = snapshot.member(advert.memberId) != null
        val trusted = snapshot.isCoordinator || known
        updateMember(advert.memberId) { current ->
            (current ?: GroupMember(advert.memberId, advert.displayName)).copy(
                endpointId = endpointId,
                displayName = advert.displayName,
                role = advert.role,
                state = if (trusted) MemberState.CONNECTING else MemberState.PENDING_APPROVAL,
                lastSeenEpochMs = now()
            )
        }
        if (trusted) {
            attemptStartedAt[advert.memberId] = now()
            transport.acceptConnection(endpointId)
        }
    }

    /** User answered a PENDING_APPROVAL prompt. */
    suspend fun respondToRequest(memberId: String, accept: Boolean) {
        val member = _state.value.member(memberId) ?: return
        val endpointId = member.endpointId ?: return
        if (accept) {
            attemptStartedAt[memberId] = now()
            updateMember(memberId) { it?.copy(state = MemberState.CONNECTING) }
            transport.acceptConnection(endpointId)
        } else {
            transport.rejectConnection(endpointId)
            removeMember(memberId)
        }
    }

    suspend fun onConnected(endpointId: String) {
        val snapshot = _state.value
        val member = snapshot.memberByEndpoint(endpointId) ?: return
        attemptStartedAt.remove(member.memberId)
        retryNotBefore.remove(member.memberId)

        updateMember(member.memberId) {
            it?.copy(
                state = MemberState.CONNECTED,
                attempts = 0,
                failureReason = null,
                isDirect = true,
                lastSeenEpochMs = now()
            )
        }
        _state.value = _state.value.copy(isForming = false, lastError = null)

        val group = _state.value.group ?: return
        if (_state.value.isCoordinator) {
            // We own the roster, so the newcomer and everyone else need the update.
            broadcastRoster()
            sendControl(endpointId, GroupMessage.Welcome(
                groupId = group.groupId,
                groupName = group.name,
                coordinatorId = group.coordinatorId,
                epoch = group.epoch
            ))
        } else if (member.memberId == group.coordinatorId) {
            lastCoordinatorBeatAt = now()
            hasSyncedWithCoordinator = true
            sendControl(endpointId, GroupMessage.JoinRequest(localMemberId, localDisplayName))
        }
    }

    suspend fun onDisconnected(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        attemptStartedAt.remove(member.memberId)

        if (member.state == MemberState.LEFT) {
            removeMember(member.memberId)
            return
        }
        // Still on the roster, so treat it as a blip and retry rather than forgetting them.
        retryNotBefore[member.memberId] = now() + RETRY_BACKOFF_MS
        updateMember(member.memberId) {
            it?.copy(state = MemberState.RECONNECTING, lastSeenEpochMs = now())
        }
    }

    // --------------------------------------------------------------- control messages

    suspend fun onControlMessage(endpointId: String, message: GroupMessage) {
        when (message) {
            is GroupMessage.Welcome -> applyWelcome(message)
            is GroupMessage.JoinRequest -> applyJoinRequest(endpointId, message)
            is GroupMessage.Roster -> applyRoster(message)
            is GroupMessage.Heartbeat -> applyHeartbeat(message)
            is GroupMessage.CoordinatorClaim -> applyCoordinatorClaim(message)
            is GroupMessage.Leave -> applyLeave(message)
        }
    }

    private suspend fun applyWelcome(message: GroupMessage.Welcome) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (message.epoch < group.epoch) return
        _state.value = _state.value.copy(
            group = group.copy(
                name = message.groupName,
                coordinatorId = message.coordinatorId,
                epoch = message.epoch
            ),
            isForming = false
        )
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        publishAdvert()
    }

    private suspend fun applyJoinRequest(endpointId: String, message: GroupMessage.JoinRequest) {
        if (!_state.value.isCoordinator) return
        updateMember(message.memberId) { current ->
            (current ?: GroupMember(message.memberId, message.displayName)).copy(
                displayName = message.displayName,
                endpointId = endpointId,
                state = MemberState.CONNECTED,
                lastSeenEpochMs = now()
            )
        }
        broadcastRoster()
    }

    /**
     * The coordinator's list is authoritative. Applying it wholesale is what lets a late
     * joiner learn about members it never saw in discovery, then dial them directly.
     */
    private suspend fun applyRoster(message: GroupMessage.Roster) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (message.epoch < group.epoch) return
        if (_state.value.isCoordinator && message.epoch <= group.epoch) return

        message.entries.forEach { entry ->
            if (entry.memberId == localMemberId) return@forEach
            updateMember(entry.memberId) { current ->
                (current ?: GroupMember(entry.memberId, entry.displayName)).copy(
                    displayName = entry.displayName,
                    role = entry.role,
                    // A roster listing is not a link; only mark live if we already had one.
                    state = current?.state ?: MemberState.DISCOVERED,
                    isDirect = current?.endpointId != null
                )
            }
        }
        // Anyone the coordinator dropped is no longer in the group.
        val rostered = message.entries.map { it.memberId }.toSet() + localMemberId
        _state.value.others
            .filter { it.memberId !in rostered }
            .forEach { removeMember(it.memberId) }

        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        connectToMissingMembers()
    }

    private fun applyHeartbeat(message: GroupMessage.Heartbeat) {
        val group = _state.value.group ?: return
        if (message.memberId == group.coordinatorId) {
            lastCoordinatorBeatAt = now()
            hasSyncedWithCoordinator = true
        }
        updateMember(message.memberId) { it?.copy(lastSeenEpochMs = now()) }
    }

    /**
     * Highest epoch wins. Equal epochs go to the lowest member id, so two devices that
     * claim at the same instant still converge instead of fighting.
     */
    private suspend fun applyCoordinatorClaim(message: GroupMessage.CoordinatorClaim) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return

        val accept = message.epoch > group.epoch ||
            (message.epoch == group.epoch && message.memberId < group.coordinatorId)
        if (!accept) return

        _state.value = _state.value.copy(
            group = group.copy(coordinatorId = message.memberId, epoch = message.epoch)
        )
        updateMember(message.memberId) { it?.copy(role = GroupRole.COORDINATOR) }
        if (message.memberId != localMemberId) {
            updateMember(localMemberId) { it?.copy(role = GroupRole.MEMBER) }
        }
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        publishAdvert()
    }

    private suspend fun applyLeave(message: GroupMessage.Leave) {
        val member = _state.value.member(message.memberId) ?: return
        member.endpointId?.let { transport.disconnect(it) }
        removeMember(message.memberId)
        if (_state.value.isCoordinator) broadcastRoster()
    }

    // ------------------------------------------------------------------------- timing

    /**
     * Drives timeouts, retries, heartbeats and failover. The Android layer calls this
     * about once a second; tests call it with explicit timestamps.
     */
    suspend fun tick() {
        val current = now()
        if (_state.value.group == null) return

        expireStalledAttempts(current)
        runScheduledRetries(current)
        sendHeartbeatIfDue(current)
        checkCoordinatorLiveness(current)
    }

    private suspend fun expireStalledAttempts(current: Long) {
        val stalled = attemptStartedAt.filter { (_, started) ->
            current - started > CONNECT_TIMEOUT_MS
        }.keys.toList()

        stalled.forEach { memberId ->
            attemptStartedAt.remove(memberId)
            val member = _state.value.member(memberId) ?: return@forEach
            val attempts = member.attempts + 1
            if (attempts >= MAX_ATTEMPTS) {
                updateMember(memberId) {
                    it?.copy(
                        state = MemberState.FAILED,
                        attempts = attempts,
                        failureReason = "No response after $attempts attempts"
                    )
                }
            } else {
                retryNotBefore[memberId] = current + RETRY_BACKOFF_MS
                updateMember(memberId) {
                    it?.copy(state = MemberState.RECONNECTING, attempts = attempts)
                }
            }
        }
    }

    private suspend fun runScheduledRetries(current: Long) {
        val due = retryNotBefore.filter { (_, at) -> current >= at }.keys.toList()
        due.forEach { memberId ->
            val member = _state.value.member(memberId) ?: run {
                retryNotBefore.remove(memberId)
                return@forEach
            }
            if (member.state.isLive) {
                retryNotBefore.remove(memberId)
                return@forEach
            }
            val endpointId = member.endpointId ?: return@forEach
            retryNotBefore.remove(memberId)
            if (!shouldInitiateTo(memberId)) return@forEach
            attemptStartedAt[memberId] = current
            updateMember(memberId) { it?.copy(state = MemberState.CONNECTING) }
            transport.connect(endpointId)
        }
    }

    private suspend fun sendHeartbeatIfDue(current: Long) {
        if (current - lastHeartbeatSentAt < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatSentAt = current
        val group = _state.value.group ?: return
        val beat = GroupMessage.Heartbeat(localMemberId, group.epoch)
        _state.value.connected.forEach { member ->
            member.endpointId?.let { sendControl(it, beat) }
        }
    }

    private suspend fun checkCoordinatorLiveness(current: Long) {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        if (snapshot.isCoordinator) return
        // Never reached them at all: this is a join that is still failing, not a
        // coordinator that died. Retry logic owns this case.
        if (!hasSyncedWithCoordinator) return
        if (current - lastCoordinatorBeatAt <= COORDINATOR_TIMEOUT_MS) return

        // Only the lowest surviving id claims, so the group does not fragment.
        val candidates = (snapshot.connected.map { it.memberId } + localMemberId)
            .filter { it != group.coordinatorId }
            .sorted()
        if (candidates.firstOrNull() != localMemberId) return

        val claimed = group.copy(coordinatorId = localMemberId, epoch = group.epoch + 1)
        _state.value = snapshot.copy(group = claimed)
        updateMember(localMemberId) { it?.copy(role = GroupRole.COORDINATOR) }
        removeMember(group.coordinatorId)
        lastCoordinatorBeatAt = current
        hasSyncedWithCoordinator = true

        val claim = GroupMessage.CoordinatorClaim(localMemberId, claimed.groupId, claimed.epoch)
        _state.value.connected.forEach { member ->
            member.endpointId?.let { sendControl(it, claim) }
        }
        publishAdvert()
        broadcastRoster()
    }

    // ------------------------------------------------------------------------ helpers

    /**
     * Both devices see each other at once, so without a rule both would dial and collide.
     * The lower member id initiates; the higher waits for the incoming request.
     */
    private fun shouldInitiateTo(memberId: String): Boolean = localMemberId < memberId

    private suspend fun maybeInitiate(memberId: String) {
        val member = _state.value.member(memberId) ?: return
        if (member.state.isLive || member.state == MemberState.CONNECTING) return
        if (member.state == MemberState.FAILED) return
        if (attemptStartedAt.containsKey(memberId)) return
        if (!shouldInitiateTo(memberId)) return
        val endpointId = member.endpointId ?: return

        attemptStartedAt[memberId] = now()
        updateMember(memberId) { it?.copy(state = MemberState.CONNECTING) }
        transport.connect(endpointId)
    }

    private suspend fun connectToMissingMembers() {
        _state.value.others
            .filter { !it.state.isLive && it.state != MemberState.CONNECTING && it.endpointId != null }
            .forEach { maybeInitiate(it.memberId) }
    }

    private suspend fun broadcastRoster() {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        if (!snapshot.isCoordinator) return

        val entries = snapshot.members
            .filter { it.state.isLive || it.memberId == localMemberId }
            .map { RosterEntry(it.memberId, it.displayName, it.role) }
        val roster = GroupMessage.Roster(group.groupId, group.epoch, entries)
        snapshot.connected.forEach { member ->
            member.endpointId?.let { sendControl(it, roster) }
        }
    }

    private suspend fun publishAdvert() {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        transport.publishAdvert(
            GroupAdvert(
                groupId = group.groupId,
                memberId = localMemberId,
                role = snapshot.localRole,
                // Peers browsing for a group need its name, not the device's.
                displayName = if (snapshot.isCoordinator) group.name else localDisplayName
            )
        )
    }

    private suspend fun sendControl(endpointId: String, message: GroupMessage) {
        runCatchingSend(endpointId, message.encode())
    }

    private suspend fun runCatchingSend(endpointId: String, payload: ByteArray) {
        try {
            transport.sendControl(endpointId, payload)
        } catch (e: Throwable) {
            _state.value = _state.value.copy(lastError = "Send failed: ${e.message}")
        }
    }

    private fun updateMember(memberId: String, transform: (GroupMember?) -> GroupMember?) {
        val snapshot = _state.value
        val existing = snapshot.member(memberId)
        val updated = transform(existing) ?: return
        val members = if (existing == null) {
            snapshot.members + updated
        } else {
            snapshot.members.map { if (it.memberId == memberId) updated else it }
        }
        _state.value = snapshot.copy(members = members)
    }

    private fun removeMember(memberId: String) {
        if (memberId == localMemberId) return
        attemptStartedAt.remove(memberId)
        retryNotBefore.remove(memberId)
        _state.value = _state.value.copy(
            members = _state.value.members.filter { it.memberId != memberId }
        )
    }
}
