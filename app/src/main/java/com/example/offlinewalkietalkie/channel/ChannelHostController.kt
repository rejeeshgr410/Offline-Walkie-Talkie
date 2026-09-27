package com.example.offlinewalkietalkie.channel

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 3, Section 6 & Section 19 Channel Host Controller.
 *
 * Runs on the host device to coordinate:
 * - Channel membership (up to 6 tested participants + relays).
 * - Session-scoped 64-bit member IDs (§19.3).
 * - Key epoch rotation on membership changes (§19.2): new voice key to members; relays excluded.
 * - Host-controlled Floor Control with 60s safety timeout and collision denial.
 * - Auto-rotating QR / short code admission verification (§19.10).
 */
class ChannelHostController(
    val channelId: Long,
    val channelName: String,
    val hostFingerprint: String,
    val hostDisplayName: String,
    val relayPolicy: RelayPolicy = RelayPolicy.OFF,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        const val SAFETY_TIMEOUT_MS = 60_000L // 60s PTT safety timeout (§2 & §64)
        const val MAX_MEMBERS = 6 // Target tested size (§2 & §16)
    }

    private val secureRandom = SecureRandom()
    private val _members = ConcurrentHashMap<Long, ChannelMember>()
    private val _membersFlow = MutableStateFlow<List<ChannelMember>>(emptyList())
    val membersFlow: StateFlow<List<ChannelMember>> = _membersFlow.asStateFlow()

    val hostMemberId: Long = generateUniqueMemberId()

    val qrJoinManager = QrJoinManager(channelId, channelName, hostFingerprint)

    private val _currentKeyEpoch = MutableStateFlow(1)
    val currentKeyEpoch: StateFlow<Int> = _currentKeyEpoch.asStateFlow()

    private var currentVoiceKey: ByteArray = generateFreshVoiceKey()

    private val _currentFloorHolder = MutableStateFlow<ChannelMember?>(null)
    val currentFloorHolder: StateFlow<ChannelMember?> = _currentFloorHolder.asStateFlow()

    private var safetyTimerJob: Job? = null

    // Callbacks for transport layer notifications
    var onKeyEpochRotated: ((newEpoch: Int, newVoiceKey: ByteArray, authorizedMembers: List<ChannelMember>) -> Unit)? = null
    var onFloorStatusChanged: ((holderMemberId: Long, holderName: String) -> Unit)? = null
    var onMemberAdmitted: ((member: ChannelMember) -> Unit)? = null
    var onMemberRemoved: ((member: ChannelMember) -> Unit)? = null

    init {
        // Register host as first member
        val hostMember = ChannelMember(
            memberId = hostMemberId,
            displayName = hostDisplayName,
            role = MemberRole.HOST,
            endpointId = "local",
            isDirect = true,
            isOnline = true
        )
        _members[hostMemberId] = hostMember
        updateMembersList()
    }

    /**
     * Admit a new participant into the channel.
     * Validates one-time secret or 6-char short code (§19.10).
     */
    fun admitMember(
        displayName: String,
        role: MemberRole = MemberRole.MEMBER,
        endpointId: String? = null,
        joinSecretOrCode: String
    ): ChannelMember? {
        val isValidSecret = qrJoinManager.verifyAndConsumeSecret(joinSecretOrCode)
        val isValidCode = !isValidSecret && qrJoinManager.verifyAndConsumeShortCode(joinSecretOrCode)

        if (!isValidSecret && !isValidCode) {
            return null // Invalid or expired join token
        }

        val memberId = generateUniqueMemberId()
        val member = ChannelMember(
            memberId = memberId,
            displayName = displayName,
            role = role,
            endpointId = endpointId,
            isDirect = true,
            isOnline = true
        )

        _members[memberId] = member
        updateMembersList()
        onMemberAdmitted?.invoke(member)

        // Rotate key epoch on material membership change (§19.2)
        rotateKeyEpoch()

        return member
    }

    /**
     * Remove a participant from the channel.
     */
    fun removeMember(memberId: Long) {
        val removed = _members.remove(memberId) ?: return
        
        // Release floor if removed member was talking
        if (_currentFloorHolder.value?.memberId == memberId) {
            releaseFloor()
        }

        updateMembersList()
        onMemberRemoved?.invoke(removed)

        // Rotate key epoch to revoke forward secrecy for departed member (§19.2)
        rotateKeyEpoch()
    }

    /**
     * Floor Request handler.
     * Grants floor if free; denies if already held by another.
     */
    fun requestFloor(memberId: Long): Boolean {
        val member = _members[memberId] ?: return false

        synchronized(this) {
            if (_currentFloorHolder.value == null) {
                // Grant floor
                _currentFloorHolder.value = member
                startSafetyTimer()
                onFloorStatusChanged?.invoke(member.memberId, member.displayName)
                return true
            } else {
                // Deny floor: already busy
                return false
            }
        }
    }

    /**
     * Floor Release handler.
     */
    fun releaseFloor(memberId: Long? = null) {
        synchronized(this) {
            val current = _currentFloorHolder.value ?: return
            if (memberId == null || current.memberId == memberId) {
                cancelSafetyTimer()
                _currentFloorHolder.value = null
                onFloorStatusChanged?.invoke(0L, "")
            }
        }
    }

    /**
     * Handle member disconnection: release floor if they held it.
     */
    fun onEndpointDisconnected(endpointId: String) {
        val member = _members.values.find { it.endpointId == endpointId }
        if (member != null) {
            val updated = member.copy(isOnline = false)
            _members[member.memberId] = updated
            updateMembersList()

            if (_currentFloorHolder.value?.memberId == member.memberId) {
                releaseFloor(member.memberId)
            }
        }
    }

    /**
     * Key epoch rotation per §19.2.
     * Generates a new 32-byte channel voice key and distributes to authorized members.
     * Relay-only nodes NEVER receive channel voice keys (§19.1 & §19.2).
     */
    fun rotateKeyEpoch() {
        val nextEpoch = _currentKeyEpoch.value + 1
        _currentKeyEpoch.value = nextEpoch
        currentVoiceKey = generateFreshVoiceKey()

        // Distribute to authorized members only (Host + Members, NOT Relays)
        val authorizedMembers = _members.values.filter { it.role != MemberRole.RELAY }
        onKeyEpochRotated?.invoke(nextEpoch, currentVoiceKey, authorizedMembers)
    }

    fun getCurrentVoiceKey(): ByteArray = currentVoiceKey.clone()

    private fun startSafetyTimer() {
        cancelSafetyTimer()
        safetyTimerJob = scope.launch {
            delay(SAFETY_TIMEOUT_MS)
            // 60-second safety timeout expired: auto-release floor
            releaseFloor()
        }
    }

    private fun cancelSafetyTimer() {
        safetyTimerJob?.cancel()
        safetyTimerJob = null
    }

    private fun updateMembersList() {
        _membersFlow.value = _members.values.toList().sortedBy { it.joinedEpochMs }
    }

    private fun generateUniqueMemberId(): Long {
        var id: Long
        do {
            id = secureRandom.nextLong()
        } while (id == 0L || _members.containsKey(id))
        return id
    }

    private fun generateFreshVoiceKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    fun reset() {
        cancelSafetyTimer()
        _members.clear()
        _currentFloorHolder.value = null
        _membersFlow.value = emptyList()
    }
}
