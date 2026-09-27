package com.example.offlinewalkietalkie.group

import com.example.offlinewalkietalkie.group.GroupProof.toHex
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
 * Trust model:
 * - The first link into a group (joiner <-> coordinator) must be confirmed by people on
 *   both phones comparing the code Nearby shows. Only then does the coordinator hand over
 *   the group secret.
 * - Every later link between rostered members proves the secret with [GroupProof] instead
 *   of asking a person again. A stranger copying the public advert has no secret and
 *   never gets past VERIFYING.
 * - Control messages are only acted on from verified links.
 *
 * Deliberately free of Android and coroutine-timer dependencies: time enters through
 * [tick] and randomness through the factories, so multi-node scenarios are reproducible
 * in unit tests.
 */
class GroupSessionManager(
    private val transport: GroupTransportPort,
    private val localDisplayName: String,
    localMemberId: String? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val idFactory: (Int) -> String = { len -> randomId(len) },
    private val secretFactory: () -> ByteArray = { GroupProof.newSecret() }
) {
    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val RETRY_BACKOFF_MS = 3_000L
        const val MAX_ATTEMPTS = 3
        const val HEARTBEAT_INTERVAL_MS = 3_000L

        /** A person has this long to compare codes before the request is dropped. */
        const val APPROVAL_TIMEOUT_MS = 60_000L

        /** Four missed beats before we consider the coordinator gone. */
        const val COORDINATOR_TIMEOUT_MS = 12_000L

        private const val ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun randomId(length: Int): String =
            (1..length).map { ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)] }.joinToString("")
    }

    val localMemberId: String = localMemberId ?: idFactory(GroupAdvert.MEMBER_ID_LENGTH)

    private val _state = MutableStateFlow(GroupState(localMemberId = this.localMemberId))
    val state: StateFlow<GroupState> = _state.asStateFlow()

    /** Shared by every member once admitted; never leaves an accepted, code-checked link. */
    private var groupSecret: ByteArray? = null

    /** memberId -> when the in-flight connection attempt started. */
    private val attemptStartedAt = mutableMapOf<String, Long>()

    /** memberId -> when we started waiting for a person to confirm the code. */
    private val approvalStartedAt = mutableMapOf<String, Long>()

    /** memberId -> earliest time a retry may fire. */
    private val retryNotBefore = mutableMapOf<String, Long>()

    /** Members whose link is up at the radio level (not necessarily verified yet). */
    private val linkUp = mutableSetOf<String>()

    /** Members that proved the group secret on their current link. */
    private val proofAccepted = mutableSetOf<String>()

    private var lastCoordinatorBeatAt = 0L
    private var lastHeartbeatSentAt = 0L

    /**
     * Whether we have ever actually reached the coordinator. A joiner that never got a
     * link must not stage a coup — that turns one failed join into two rival groups.
     */
    private var hasSyncedWithCoordinator = false

    val holdsGroupSecret: Boolean get() = groupSecret != null

    // ---------------------------------------------------------------- group lifecycle

    /** Become the coordinator of a brand new group. */
    suspend fun createGroup(name: String) {
        resetLinkTracking()
        groupSecret = secretFactory()
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
            onRoster = true,
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
     * Join a group we saw in discovery. The link to the coordinator is opened here; it
     * stays unverified until people on both phones confirm the code.
     */
    suspend fun joinGroup(advert: GroupAdvert, endpointId: String) {
        resetLinkTracking()
        groupSecret = null
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
            onRoster = true,
            lastSeenEpochMs = now()
        )
        val target = GroupMember(
            memberId = advert.memberId,
            displayName = advert.displayName,
            endpointId = endpointId,
            role = GroupRole.COORDINATOR,
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
        resetLinkTracking()
        transport.clearAdvert()
        groupSecret = null
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

        // Advertising our group id proves nothing — anyone can copy it. Only members the
        // coordinator admitted are worth tracking; everyone else must come in by request.
        val existing = snapshot.member(advert.memberId) ?: return
        if (!existing.onRoster) return
        if (existing.state.isLive || existing.state.isTransitional) return

        updateMember(advert.memberId) {
            it?.copy(
                displayName = advert.displayName,
                endpointId = endpointId,
                role = advert.role,
                lastSeenEpochMs = now()
            )
        }
        maybeInitiate(advert.memberId)
    }

    fun onEndpointLost(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        if (member.state.isLive) return
        if (member.state == MemberState.DISCOVERED && !member.onRoster) {
            removeMember(member.memberId)
        }
    }

    /**
     * Nearby raises this on BOTH ends of every new connection, with the same [authCode].
     *
     * A rostered member is let in straight away and then has to prove the group secret.
     * Anyone else — a joiner on first contact, or a stranger — waits for a person to
     * compare the code.
     */
    suspend fun onConnectionRequest(endpointId: String, advert: GroupAdvert?, authCode: String) {
        val snapshot = _state.value
        val group = snapshot.group

        if (advert == null || group == null || advert.groupId != group.groupId) {
            if (advert != null && advert.memberId != localMemberId) {
                markPendingApproval(advert, endpointId, authCode)
            }
            return
        }
        if (advert.memberId == localMemberId) {
            // Someone is advertising our own identity back at us.
            transport.rejectConnection(endpointId)
            return
        }

        val existing = snapshot.member(advert.memberId)
        if (existing != null && existing.state.isLive && existing.endpointId != endpointId) {
            // Second link claiming an identity that is already verified elsewhere: a
            // duplicate at best, an impostor at worst. Either way, keep the one we have.
            transport.rejectConnection(endpointId)
            return
        }

        val canProve = groupSecret != null && existing?.onRoster == true
        if (!canProve) {
            markPendingApproval(advert, endpointId, authCode)
            return
        }

        updateMember(advert.memberId) {
            it?.copy(
                endpointId = endpointId,
                displayName = advert.displayName,
                authCode = authCode,
                humanVerified = false,
                state = MemberState.CONNECTING,
                lastSeenEpochMs = now()
            )
        }
        linkUp.remove(advert.memberId)
        proofAccepted.remove(advert.memberId)
        attemptStartedAt[advert.memberId] = now()
        transport.acceptConnection(endpointId)
    }

    private fun markPendingApproval(advert: GroupAdvert, endpointId: String, authCode: String) {
        updateMember(advert.memberId) { current ->
            (current ?: GroupMember(advert.memberId, advert.displayName)).copy(
                endpointId = endpointId,
                displayName = advert.displayName,
                role = current?.role ?: advert.role,
                authCode = authCode,
                humanVerified = false,
                state = MemberState.PENDING_APPROVAL,
                lastSeenEpochMs = now()
            )
        }
        attemptStartedAt.remove(advert.memberId)
        approvalStartedAt[advert.memberId] = now()
    }

    /** A person compared the codes and answered. */
    suspend fun respondToRequest(memberId: String, accept: Boolean) {
        val member = _state.value.member(memberId) ?: return
        val endpointId = member.endpointId ?: return
        approvalStartedAt.remove(memberId)
        if (accept) {
            attemptStartedAt[memberId] = now()
            updateMember(memberId) {
                it?.copy(state = MemberState.CONNECTING, humanVerified = true)
            }
            transport.acceptConnection(endpointId)
        } else {
            transport.rejectConnection(endpointId)
            removeMember(memberId)
        }
    }

    suspend fun onConnected(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        val memberId = member.memberId
        linkUp.add(memberId)
        retryNotBefore.remove(memberId)

        if (member.humanVerified) {
            promote(memberId)
            val group = _state.value.group ?: return
            if (_state.value.isCoordinator) {
                val secret = groupSecret ?: return
                sendControl(endpointId, GroupMessage.Welcome(
                    groupId = group.groupId,
                    groupName = group.name,
                    coordinatorId = group.coordinatorId,
                    epoch = group.epoch,
                    secretHex = secret.toHex()
                ))
                broadcastRoster()
            } else if (memberId == group.coordinatorId) {
                lastCoordinatorBeatAt = now()
                hasSyncedWithCoordinator = true
                sendControl(endpointId, GroupMessage.JoinRequest(localMemberId, localDisplayName))
            }
            return
        }

        // Rostered member on a new link: both sides prove the secret before voice flows.
        attemptStartedAt[memberId] = now()
        updateMember(memberId) { it?.copy(state = MemberState.VERIFYING, lastSeenEpochMs = now()) }
        sendProof(endpointId, member)
        if (memberId in proofAccepted) promote(memberId)
    }

    /**
     * A connection that never came up: the other side declined, or the radio gave up.
     * Distinct from [onDisconnected], which is a link that was up and dropped.
     */
    fun onConnectionFailed(endpointId: String, reason: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        if (member.state.isLive) return
        attemptStartedAt.remove(member.memberId)
        approvalStartedAt.remove(member.memberId)
        linkUp.remove(member.memberId)
        proofAccepted.remove(member.memberId)
        if (member.onRoster) {
            // Known member: the retry schedule will try again.
            retryNotBefore[member.memberId] = now() + RETRY_BACKOFF_MS
            updateMember(member.memberId) { it?.copy(state = MemberState.RECONNECTING) }
        } else {
            updateMember(member.memberId) {
                it?.copy(state = MemberState.FAILED, failureReason = reason, humanVerified = false)
            }
        }
    }

    suspend fun onDisconnected(endpointId: String) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        val memberId = member.memberId
        attemptStartedAt.remove(memberId)
        approvalStartedAt.remove(memberId)
        linkUp.remove(memberId)
        proofAccepted.remove(memberId)

        if (member.state == MemberState.LEFT) {
            removeMember(memberId)
            return
        }
        if (!member.onRoster) {
            // Never admitted: nothing to reconnect to.
            removeMember(memberId)
            refreshNeighbors()
            return
        }
        // Still on the roster, so treat it as a blip and retry rather than forgetting them.
        retryNotBefore[memberId] = now() + RETRY_BACKOFF_MS
        updateMember(memberId) {
            it?.copy(state = MemberState.RECONNECTING, humanVerified = false, lastSeenEpochMs = now())
        }
        refreshNeighbors()
    }

    // --------------------------------------------------------------- control messages

    suspend fun onControlMessage(endpointId: String, message: GroupMessage) {
        val sender = _state.value.memberByEndpoint(endpointId)

        if (message is GroupMessage.MembershipProof) {
            applyProof(endpointId, message)
            return
        }
        // Everything else changes group state, so it must come from a verified link.
        val trusted = sender != null &&
            (sender.state == MemberState.CONNECTED || sender.humanVerified)
        if (!trusted) return

        when (message) {
            is GroupMessage.Welcome -> applyWelcome(sender!!, message)
            is GroupMessage.JoinRequest -> applyJoinRequest(endpointId, message)
            is GroupMessage.Roster -> applyRoster(sender!!, message)
            is GroupMessage.Heartbeat -> applyHeartbeat(message)
            is GroupMessage.CoordinatorClaim -> applyCoordinatorClaim(message)
            is GroupMessage.Leave -> applyLeave(message)
            is GroupMessage.MembershipProof -> Unit
        }
    }

    private suspend fun applyProof(endpointId: String, message: GroupMessage.MembershipProof) {
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        val secret = groupSecret ?: return
        val code = member.authCode ?: return
        if (member.humanVerified) return
        if (message.memberId != member.memberId) {
            failVerification(member, "Identity mismatch on link")
            return
        }
        val ok = GroupProof.verify(secret, member.memberId, localMemberId, code, message.proofHex)
        if (!ok) {
            failVerification(member, "Could not prove group membership")
            return
        }
        proofAccepted.add(member.memberId)
        if (member.memberId in linkUp) promote(member.memberId)
    }

    private suspend fun failVerification(member: GroupMember, reason: String) {
        attemptStartedAt.remove(member.memberId)
        linkUp.remove(member.memberId)
        proofAccepted.remove(member.memberId)
        updateMember(member.memberId) {
            it?.copy(state = MemberState.FAILED, failureReason = reason, humanVerified = false)
        }
        member.endpointId?.let { transport.disconnect(it) }
    }

    private suspend fun applyWelcome(sender: GroupMember, message: GroupMessage.Welcome) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (message.epoch < group.epoch) return
        // The secret only counts when it arrives on the code-checked link to the coordinator.
        if (!sender.humanVerified || sender.memberId != message.coordinatorId) return
        val secret = GroupProof.fromHex(message.secretHex) ?: return

        groupSecret = secret
        _state.value = _state.value.copy(
            group = group.copy(
                name = message.groupName,
                coordinatorId = message.coordinatorId,
                epoch = message.epoch
            ),
            isForming = false
        )
        updateMember(sender.memberId) { it?.copy(onRoster = true, role = GroupRole.COORDINATOR) }
        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        publishAdvert()
    }

    private suspend fun applyJoinRequest(endpointId: String, message: GroupMessage.JoinRequest) {
        if (!_state.value.isCoordinator) return
        val member = _state.value.memberByEndpoint(endpointId) ?: return
        // Only a link a person confirmed may add someone to the roster.
        if (!member.humanVerified || member.memberId != message.memberId) return
        updateMember(message.memberId) {
            it?.copy(
                displayName = message.displayName,
                onRoster = true,
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
    private suspend fun applyRoster(sender: GroupMember, message: GroupMessage.Roster) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (message.epoch < group.epoch) return
        if (sender.memberId != group.coordinatorId) return
        if (_state.value.isCoordinator && message.epoch <= group.epoch) return

        message.entries.forEach { entry ->
            if (entry.memberId == localMemberId) return@forEach
            updateMember(entry.memberId) { current ->
                (current ?: GroupMember(entry.memberId, entry.displayName)).copy(
                    displayName = entry.displayName,
                    role = entry.role,
                    onRoster = true,
                    // A roster listing is not a link; only mark live if we already had one.
                    state = current?.state ?: MemberState.DISCOVERED,
                    isDirect = current?.state?.isLive == true
                )
            }
        }
        // Anyone the coordinator dropped is no longer in the group.
        val rostered = message.entries.map { it.memberId }.toSet() + localMemberId
        _state.value.others
            .filter { it.memberId !in rostered && it.state != MemberState.PENDING_APPROVAL }
            .forEach { removeMember(it.memberId) }

        lastCoordinatorBeatAt = now()
        hasSyncedWithCoordinator = true
        refreshNeighbors()
        connectToMissingMembers()
    }

    private fun applyHeartbeat(message: GroupMessage.Heartbeat) {
        val group = _state.value.group ?: return
        if (message.memberId == group.coordinatorId) {
            lastCoordinatorBeatAt = now()
            hasSyncedWithCoordinator = true
        }
        updateMember(message.memberId) { it?.copy(lastSeenEpochMs = now()) }
        val neighbors = _state.value.neighbors + (message.memberId to message.neighbors.toSet())
        _state.value = _state.value.copy(neighbors = neighbors)
        refreshNeighbors()
    }

    /**
     * Highest epoch wins. Equal epochs go to the lowest member id, so two devices that
     * claim at the same instant still converge instead of fighting.
     */
    private suspend fun applyCoordinatorClaim(message: GroupMessage.CoordinatorClaim) {
        val group = _state.value.group ?: return
        if (message.groupId != group.groupId) return
        if (_state.value.member(message.memberId)?.onRoster != true &&
            message.memberId != localMemberId
        ) return

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
        refreshNeighbors()
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

        expireUnansweredApprovals(current)
        expireStalledAttempts(current)
        runScheduledRetries(current)
        sendHeartbeatIfDue(current)
        checkCoordinatorLiveness(current)
    }

    private suspend fun expireUnansweredApprovals(current: Long) {
        val expired = approvalStartedAt.filter { (_, started) ->
            current - started > APPROVAL_TIMEOUT_MS
        }.keys.toList()
        expired.forEach { memberId ->
            approvalStartedAt.remove(memberId)
            val member = _state.value.member(memberId) ?: return@forEach
            member.endpointId?.let { transport.rejectConnection(it) }
            updateMember(memberId) {
                it?.copy(state = MemberState.FAILED, failureReason = "Code was not confirmed in time")
            }
        }
    }

    private suspend fun expireStalledAttempts(current: Long) {
        val stalled = attemptStartedAt.filter { (_, started) ->
            current - started > CONNECT_TIMEOUT_MS
        }.keys.toList()

        stalled.forEach { memberId ->
            attemptStartedAt.remove(memberId)
            val member = _state.value.member(memberId) ?: return@forEach
            if (member.state.isLive) return@forEach
            if (member.state == MemberState.VERIFYING) {
                failVerification(member, "Did not prove group membership")
                return@forEach
            }
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
        val beat = GroupMessage.Heartbeat(
            memberId = localMemberId,
            epoch = group.epoch,
            neighbors = _state.value.connected.map { it.memberId }
        )
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
        if (groupSecret == null) return
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
        refreshNeighbors()
        publishAdvert()
        broadcastRoster()
    }

    // ------------------------------------------------------------------------ helpers

    /**
     * Both devices see each other at once, so without a rule both would dial and collide.
     * The lower member id initiates; the higher waits for the incoming request. Until we
     * are admitted, though, we are the only one who can dial — the coordinator does not
     * know we exist yet.
     */
    private fun shouldInitiateTo(memberId: String): Boolean {
        val group = _state.value.group ?: return false
        if (groupSecret == null) return memberId == group.coordinatorId
        return localMemberId < memberId
    }

    private suspend fun maybeInitiate(memberId: String) {
        val member = _state.value.member(memberId) ?: return
        if (member.state.isLive || member.state.isTransitional) return
        if (member.state == MemberState.FAILED) return
        if (!member.onRoster) return
        if (attemptStartedAt.containsKey(memberId)) return
        if (!shouldInitiateTo(memberId)) return
        val endpointId = member.endpointId ?: return

        attemptStartedAt[memberId] = now()
        updateMember(memberId) { it?.copy(state = MemberState.CONNECTING) }
        transport.connect(endpointId)
    }

    private suspend fun connectToMissingMembers() {
        _state.value.others
            .filter { !it.state.isLive && !it.state.isTransitional && it.endpointId != null }
            .forEach { maybeInitiate(it.memberId) }
    }

    private suspend fun promote(memberId: String) {
        attemptStartedAt.remove(memberId)
        updateMember(memberId) {
            it?.copy(
                state = MemberState.CONNECTED,
                attempts = 0,
                failureReason = null,
                isDirect = true,
                reachableViaRelay = false,
                lastSeenEpochMs = now()
            )
        }
        _state.value = _state.value.copy(isForming = false, lastError = null)
        refreshNeighbors()
        if (_state.value.isCoordinator && _state.value.member(memberId)?.onRoster == true) {
            broadcastRoster()
        }
    }

    private suspend fun sendProof(endpointId: String, member: GroupMember) {
        val secret = groupSecret ?: return
        val code = member.authCode ?: return
        val proof = GroupProof.compute(secret, localMemberId, member.memberId, code)
        sendControl(endpointId, GroupMessage.MembershipProof(localMemberId, proof))
    }

    /**
     * Keeps our own neighbour entry current and marks roster members that we cannot reach
     * directly but a neighbour of ours can, so the UI and voice relay agree.
     */
    private fun refreshNeighbors() {
        val snapshot = _state.value
        val mine = snapshot.connected.map { it.memberId }.toSet()
        val neighbors = snapshot.neighbors
            .filterKeys { it == localMemberId || it in mine }
            .plus(localMemberId to mine)

        val members = snapshot.members.map { member ->
            if (member.memberId == localMemberId || member.state.isLive) {
                member.copy(reachableViaRelay = false, isDirect = member.state.isLive || member.isDirect)
            } else {
                val viaRelay = member.onRoster && mine.any { n ->
                    neighbors[n]?.contains(member.memberId) == true
                }
                member.copy(reachableViaRelay = viaRelay, isDirect = false)
            }
        }
        _state.value = snapshot.copy(members = members, neighbors = neighbors)
    }

    private suspend fun broadcastRoster() {
        val snapshot = _state.value
        val group = snapshot.group ?: return
        if (!snapshot.isCoordinator) return

        val entries = snapshot.members
            .filter { it.memberId == localMemberId || (it.onRoster && it.state.isLive) }
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

    private fun resetLinkTracking() {
        attemptStartedAt.clear()
        approvalStartedAt.clear()
        retryNotBefore.clear()
        linkUp.clear()
        proofAccepted.clear()
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
        approvalStartedAt.remove(memberId)
        retryNotBefore.remove(memberId)
        linkUp.remove(memberId)
        proofAccepted.remove(memberId)
        _state.value = _state.value.copy(
            members = _state.value.members.filter { it.memberId != memberId },
            neighbors = _state.value.neighbors - memberId
        )
    }
}
