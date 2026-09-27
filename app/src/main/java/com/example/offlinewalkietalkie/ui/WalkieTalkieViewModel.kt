package com.example.offlinewalkietalkie.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinewalkietalkie.crypto.CryptoManager
import com.example.offlinewalkietalkie.mesh.RoutingManager
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.protocol.Frame
import com.example.offlinewalkietalkie.protocol.FrameType
import com.example.offlinewalkietalkie.transport.ConnectionRequest
import com.example.offlinewalkietalkie.transport.LocalIdentity
import com.example.offlinewalkietalkie.transport.Peer
import com.example.offlinewalkietalkie.transport.TransportManager
import com.example.offlinewalkietalkie.transport.TransportType
import com.example.offlinewalkietalkie.voice.VoiceEngine
import com.example.offlinewalkietalkie.channel.*
import com.example.offlinewalkietalkie.group.DiscoveredGroup
import com.example.offlinewalkietalkie.group.GroupAdvert
import com.example.offlinewalkietalkie.group.GroupMessage
import com.example.offlinewalkietalkie.group.GroupNearbyPort
import com.example.offlinewalkietalkie.group.GroupRole
import com.example.offlinewalkietalkie.group.GroupSessionManager
import com.example.offlinewalkietalkie.group.GroupState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import com.example.offlinewalkietalkie.protocol.FloorController
import com.example.offlinewalkietalkie.protocol.SessionFloorMode
import com.example.offlinewalkietalkie.service.NotificationController
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TL/ViewModel"
        private const val GROUP_TICK_INTERVAL_MS = 1_000L
    }

    private val transport = TransportManager(application)
    private val routingManager = RoutingManager(transport)
    private val voiceEngine = VoiceEngine(context = application)
    private val cryptoManager = CryptoManager()

    private val sessionId: Long = System.currentTimeMillis()

    /** viewModelScope is already cancelled by the time onCleared runs, so shutdown needs its own. */
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val groupPort = GroupNearbyPort(transport, sessionId)

    val groupManager = GroupSessionManager(
        transport = groupPort,
        localDisplayName = Build.MODEL
    )

    /** Authoritative view of the group: roster, roles, per-device connection state. */
    val groupState: StateFlow<GroupState> = groupManager.state

    private val _discoveredGroups = MutableStateFlow<List<DiscoveredGroup>>(emptyList())
    val discoveredGroups = _discoveredGroups.asStateFlow()

    /** Monotonic counter so no two control frames ever share a dedupe identity. */
    private val controlSequence = AtomicInteger(1)

    val localDeviceName: String = Build.MODEL

    /** Exposed so the Messages UI can tell its own messages apart from peers'. */
    val sessionIdForMessages: Long = sessionId

    val floorController = FloorController(
        localMemberId = sessionId,
        mode = SessionFloorMode.DIRECT_1_TO_1,
        scope = viewModelScope
    )
    val floorState: StateFlow<FloorState> = floorController.floorState

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers = _peers.asStateFlow()

    /**
     * Held as state, not a replay-0 event stream: the pairing screen mounts *after* the
     * request arrives, so an event-only flow would leave it waiting forever.
     */
    private val _pendingRequest = MutableStateFlow<ConnectionRequest?>(null)
    val pendingRequest = _pendingRequest.asStateFlow()

    private val _transportError = MutableStateFlow<String?>(null)
    val transportError = _transportError.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    private val _transportType = MutableStateFlow(TransportType.NEARBY_CONNECTIONS)
    val transportType = _transportType.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isOutdoorMode = MutableStateFlow(false)
    val isOutdoorMode = _isOutdoorMode.asStateFlow()

    private val _isRelayModeActive = MutableStateFlow(true)
    val isRelayModeActive = _isRelayModeActive.asStateFlow()

    private val _currentChannelName = MutableStateFlow("Direct Session")
    val currentChannelName = _currentChannelName.asStateFlow()

    private val _speakerphoneEnabled = MutableStateFlow(true)
    val speakerphoneEnabled = _speakerphoneEnabled.asStateFlow()

    /** Starts idle: the dashboard must not claim an active session before one exists. */
    private val _isInSession = MutableStateFlow(false)
    val isInSession = _isInSession.asStateFlow()

    private val _securedPeerCount = MutableStateFlow(0)
    val securedPeerCount = _securedPeerCount.asStateFlow()

    /** Peers we hold a session key for, so the key can be dropped when the link goes away. */
    private val securedPeerIds = mutableSetOf<String>()

    private val _channelHostController = MutableStateFlow<ChannelHostController?>(null)
    val channelHostController = _channelHostController.asStateFlow()

    val replayBuffer = ReplayBuffer(scope = viewModelScope)
    val replayTransmissions = replayBuffer.transmissionList

    private val _textMessages = MutableStateFlow<List<TextMessage>>(emptyList())
    val textMessages = _textMessages.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val channelMembers: StateFlow<List<ChannelMember>> = _channelHostController.flatMapLatest { host ->
        host?.membersFlow ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentKeyEpoch: StateFlow<Int> = _channelHostController.flatMapLatest { host ->
        host?.currentKeyEpoch ?: flowOf(1)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val reliableMessageManager = ReliableMessageManager(
        sendRawPacket = { peerId, bytes ->
            transport.send(peerId, bytes)
            true
        },
        scope = viewModelScope
    )

    init {
        floorController.onTransmitPendingFrames = { frames ->
            frames.forEach { frame ->
                broadcastVoiceFrame(frame.sequence, frame.data)
            }
        }
        floorController.onFloorReleased = {
            _isRecording.value = false
            voiceEngine.stopRecording()
            sendControlFrame(null, FrameType.FLOOR_RELEASE)
        }

        voiceEngine.routeManager?.onInterruptedByCall = {
            floorController.releasePtt()
            _isRecording.value = false
            voiceEngine.stopRecording()
            voiceEngine.stopPlayback()
        }

        transport.lastError.onEach { _transportError.value = it }.launchIn(viewModelScope)
        transport.isAdvertising.onEach { _isAdvertising.value = it }.launchIn(viewModelScope)
        transport.isDiscovering.onEach { _isDiscovering.value = it }.launchIn(viewModelScope)
        transport.activeTransportType.onEach { _transportType.value = it }.launchIn(viewModelScope)

        transport.connectionRequests.onEach { request ->
            val advert = GroupAdvert.decode(request.peerName)
            Log.i(TAG, "Connection request from ${request.peerName} (${request.peerId}) group=${advert?.groupId}")
            if (advert != null) {
                // Group-aware path: same-group peers are admitted without a per-link prompt,
                // which is what makes more than two devices workable.
                groupManager.onConnectionRequest(request.peerId, advert, request.authCode)
            } else {
                // Peer on an older build with no advert: fall back to manual confirmation.
                _pendingRequest.value = request
            }
        }.launchIn(viewModelScope)

        // Drives connection timeouts, retries, heartbeats and coordinator failover.
        viewModelScope.launch {
            while (isActive) {
                delay(GROUP_TICK_INTERVAL_MS)
                groupManager.tick()
            }
        }

        transport.peers.onEach { peerList ->
            val previouslyConnected = _peers.value.count { it.isConnected }
            val previouslyConnectedIds = _peers.value.filter { it.isConnected }.map { it.id }.toSet()
            _peers.value = peerList
            val connected = peerList.filter { it.isConnected }

            feedGroupDiscovery(peerList)
            feedGroupLinkTransitions(peerList, previouslyConnectedIds)
            Log.i(TAG, "Peers updated: ${peerList.size} visible, ${connected.size} connected")

            // A dropped peer's key has to go with it, otherwise the Security tile keeps
            // counting it and a reconnecting endpoint silently reuses a stale key.
            val connectedIds = connected.mapTo(mutableSetOf()) { it.id }
            securedPeerIds.filterNot { connectedIds.contains(it) }.forEach { goneId ->
                cryptoManager.removeSession(goneId)
                securedPeerIds.remove(goneId)
            }

            connected.forEach { peer ->
                if (!cryptoManager.hasSessionKey(peer.id)) {
                    sendAuthFrame(peer.id)
                }
            }
            _securedPeerCount.value = connected.count { cryptoManager.hasSessionKey(it.id) }

            // A live link is what makes a session active; don't require a separate user step.
            if (connected.isNotEmpty() && !_isInSession.value) {
                _currentChannelName.value = connected.first().name
                setInSession(true)
            } else if (connected.isEmpty() && previouslyConnected > 0) {
                setInSession(false)
            }
        }.launchIn(viewModelScope)

        routingManager.incomingFrames.onEach { (peerId, decodedFrame) ->
            var shouldForwardControlFrame = false
            when (decodedFrame.type) {
                FrameType.AUTH -> {
                    val isNew = !cryptoManager.hasSessionKey(peerId)
                    cryptoManager.deriveSessionKey(peerId, decodedFrame.payload)
                    securedPeerIds.add(peerId)
                    _securedPeerCount.value = _peers.value.count { it.isConnected && cryptoManager.hasSessionKey(it.id) }
                    Log.i(TAG, "AUTH from $peerId - session key established (secured=${_securedPeerCount.value})")
                    if (isNew) sendAuthFrame(peerId)
                }
                FrameType.FLOOR_REQUEST -> {
                    handleFloorRequest(peerId)
                    shouldForwardControlFrame = true
                }
                FrameType.FLOOR_GRANT -> {
                    handleFloorGrant(peerId)
                    shouldForwardControlFrame = true
                }
                FrameType.FLOOR_RELEASE -> {
                    handleFloorRelease(peerId)
                    shouldForwardControlFrame = true
                }
                FrameType.VOICE -> {
                    Log.d(TAG, "VOICE frame from $peerId: encrypted=${decodedFrame.isEncrypted()} authTag=${decodedFrame.authTag != null} seq=${decodedFrame.sequence} payload=${decodedFrame.payload.size}B hasKey=${cryptoManager.hasSessionKey(peerId)}")
                    if (decodedFrame.isEncrypted() && decodedFrame.authTag != null) {
                        if (decodedFrame.sequence == 1 || decodedFrame.sequence % 50 == 0) {
                            Log.i(TAG, "Received VOICE seq=${decodedFrame.sequence} payload=${decodedFrame.payload.size}B")
                        }
                        val nonce = generateNonce(decodedFrame.sessionId, decodedFrame.sequence)
                        val dummyForAad = decodedFrame.copy(hopLimit = 0)
                        val headerAad = dummyForAad.toBytes().copyOfRange(0, Frame.HEADER_SIZE)

                        val plaintext = cryptoManager.decrypt(
                            peerId,
                            decodedFrame.payload,
                            decodedFrame.authTag,
                            nonce,
                            headerAad
                        )
                        if (plaintext != null) {
                            Log.d(TAG, "VOICE decrypt OK seq=${decodedFrame.sequence} plaintext=${plaintext.size}B")
                            voiceEngine.handleIncomingFrame(
                                peerId = peerId,
                                sequence = decodedFrame.sequence,
                                encodedOpus = plaintext,
                                senderTimestampMs = decodedFrame.timestamp.toLong()
                            )

                            if (replayBuffer.isEnabled) {
                                replayBuffer.onFrameReceived(plaintext)
                            }

                            if (decodedFrame.hopLimit > 0 && _isRelayModeActive.value) {
                                relayVoiceFrame(decodedFrame, plaintext, ingressPeerId = peerId)
                            }
                        } else {
                            Log.w(TAG, "VOICE decrypt FAILED from $peerId seq=${decodedFrame.sequence} payloadSize=${decodedFrame.payload.size} authTagSize=${decodedFrame.authTag?.size}")
                        }
                    } else {
                        Log.w(TAG, "VOICE frame NOT encrypted or missing authTag from $peerId: encrypted=${decodedFrame.isEncrypted()} authTag=${decodedFrame.authTag}")
                    }
                }
                FrameType.FLOOR_STATUS -> {
                    if (decodedFrame.payload.size >= 8) {
                        val buf = ByteBuffer.wrap(decodedFrame.payload)
                        val holderId = buf.getLong()
                        val holderNameBytes = ByteArray(buf.remaining())
                        buf.get(holderNameBytes)
                        val holderName = String(holderNameBytes, Charsets.UTF_8)
                        if (holderId == 0L) {
                            floorController.onFloorFreed()
                            replayBuffer.onTransmissionCompleted()
                        } else {
                            floorController.onFloorHeldByOther(holderId.toString(), holderName)
                            replayBuffer.onTransmissionStarted(holderId, holderName)
                        }
                    }
                    shouldForwardControlFrame = true
                }
                FrameType.TEXT -> {
                    val content = String(decodedFrame.payload, Charsets.UTF_8)
                    val peerName = _peers.value.find { it.id == peerId }?.name ?: "Peer"
                    Log.i(TAG, "Received TEXT from $peerName seq=${decodedFrame.sequence}: \"${content.take(40)}\"")
                    val msg = TextMessage(
                        sequence = decodedFrame.sequence,
                        senderMemberId = decodedFrame.sessionId,
                        senderName = peerName,
                        content = content,
                        status = TextDeliveryStatus.DELIVERED
                    )
                    _textMessages.value = _textMessages.value + msg
                    val ack = Frame(
                        type = FrameType.ACK,
                        sequence = decodedFrame.sequence,
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis().toInt()
                    )
                    transport.send(peerId, ack.toBytes())
                }
                FrameType.ACK -> {
                    reliableMessageManager.onAckReceived(decodedFrame.sessionId, decodedFrame.sequence)
                }
                FrameType.KEY_UPDATE -> {
                    if (decodedFrame.payload.isNotEmpty()) {
                        cryptoManager.deriveSessionKey(peerId, decodedFrame.payload)
                    }
                }
                FrameType.GROUP_CONTROL -> {
                    handleGroupControl(peerId, decodedFrame.payload)
                }
                else -> {}
            }
            if (shouldForwardControlFrame && !decodedFrame.isEncrypted() && decodedFrame.hopLimit > 0) {
                val fwdBytes = decodedFrame.copy(hopLimit = (decodedFrame.hopLimit - 1).toByte()).toBytes()
                _peers.value.filter { it.isConnected && it.id != peerId }.forEach {
                    transport.send(it.id, fwdBytes)
                }
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun relayVoiceFrame(
        decodedFrame: Frame,
        plaintext: ByteArray,
        ingressPeerId: String
    ) {
        val nextHopLimit = (decodedFrame.hopLimit - 1).toByte()
        val activePeers = _peers.value.filter { it.isConnected && it.id != ingressPeerId }
        activePeers.forEach { targetPeer ->
            if (!cryptoManager.hasSessionKey(targetPeer.id)) return@forEach
            val dummyFwd = Frame(
                type = FrameType.VOICE,
                flags = Frame.FLAG_ENCRYPTED,
                hopLimit = 0,
                sessionId = decodedFrame.sessionId,
                sequence = decodedFrame.sequence,
                timestamp = decodedFrame.timestamp,
                payload = ByteArray(plaintext.size)
            )
            val fwdAad = dummyFwd.toBytes().copyOfRange(0, Frame.HEADER_SIZE)
            val fwdNonce = generateNonce(decodedFrame.sessionId, decodedFrame.sequence)
            val encrypted = cryptoManager.encrypt(targetPeer.id, plaintext, fwdNonce, fwdAad)
            if (encrypted != null) {
                val fwdFrame = dummyFwd.copy(
                    hopLimit = nextHopLimit,
                    payload = encrypted.first,
                    authTag = encrypted.second
                )
                transport.send(targetPeer.id, fwdFrame.toBytes())
            }
        }
    }

    private fun handleFloorRequest(peerId: String) {
        val current = floorController.floorState.value
        val peerName = _peers.value.find { it.id == peerId }?.name ?: "Unknown"
        if (current is FloorState.Idle || (current is FloorState.HeldByOther && current.peerId == peerId)) {
            floorController.onFloorHeldByOther(peerId, peerName)
            sendControlFrame(peerId, FrameType.FLOOR_GRANT)
        } else if (current is FloorState.Talking || current is FloorState.HeldByMe) {
            val peerMemberId = peerId.hashCode().toLong() and 0xFFFFFFFFL
            val localWon = floorController.handleDirectCollision(peerMemberId, System.currentTimeMillis())
            if (!localWon) {
                floorController.onFloorHeldByOther(peerId, peerName)
            }
        }
    }

    private fun handleFloorGrant(peerId: String) {
        // Only a grant we actually asked for may open the microphone. Without this guard a
        // direct 1:1 grant would start a second capture loop on top of the running one.
        if (_isRecording.value) return
        if (floorController.floorState.value !is FloorState.Requesting) return

        floorController.onFloorGranted()
        _isRecording.value = true
        voiceEngine.startRecording { sequence, encodedFrame ->
            broadcastVoiceFrame(sequence, encodedFrame)
        }
    }

    private fun handleFloorRelease(peerId: String) {
        floorController.onFloorFreed()
    }

    private fun broadcastVoiceFrame(sequence: Int, encodedFrame: ByteArray) {
        viewModelScope.launch {
            val activePeers = _peers.value.filter { it.isConnected }
            if (activePeers.isNotEmpty() && (sequence == 1 || sequence % 50 == 0)) {
                Log.i(TAG, "Sending VOICE frame $sequence to ${activePeers.size} peer(s), payload=${encodedFrame.size}B")
            }
            activePeers.forEach { peer ->
                if (cryptoManager.hasSessionKey(peer.id)) {
                    val nonce = generateNonce(sessionId, sequence)
                    val dummyFrame = Frame(
                        type = FrameType.VOICE,
                        flags = Frame.FLAG_ENCRYPTED,
                        hopLimit = 0,
                        sessionId = sessionId,
                        sequence = sequence,
                        timestamp = System.currentTimeMillis().toInt(),
                        payload = ByteArray(encodedFrame.size)
                    )
                    val headerAad = dummyFrame.toBytes().copyOfRange(0, Frame.HEADER_SIZE)

                    val encrypted = cryptoManager.encrypt(peer.id, encodedFrame, nonce, headerAad)
                    if (encrypted != null) {
                        val frame = dummyFrame.copy(
                            hopLimit = 3,
                            payload = encrypted.first,
                            authTag = encrypted.second
                        )
                        transport.send(peer.id, frame.toBytes())
                    }
                }
            }
        }
    }

    private fun sendControlFrame(targetPeerId: String?, type: FrameType) {
        viewModelScope.launch {
            // Control frames carry no sequence of their own, so without a unique one here
            // every FLOOR_RELEASE would hash identically and be dropped as a duplicate
            // after the very first transmission.
            val frame = Frame(
                type = type,
                sessionId = sessionId,
                sequence = controlSequence.getAndIncrement(),
                timestamp = System.currentTimeMillis().toInt(),
                hopLimit = 3
            )
            if (targetPeerId != null) {
                routingManager.sendTo(targetPeerId, frame)
            } else {
                routingManager.markAsSeen(frame)
                _peers.value.filter { it.isConnected }.forEach { peer ->
                    transport.send(peer.id, frame.toBytes())
                }
            }
        }
    }

    private fun sendAuthFrame(peerId: String) {
        viewModelScope.launch {
            val pubKey = cryptoManager.getPublicKeyBytes()
            val frame = Frame(
                type = FrameType.AUTH,
                payload = pubKey,
                sessionId = sessionId,
                sequence = controlSequence.getAndIncrement(),
                timestamp = System.currentTimeMillis().toInt(),
                hopLimit = 1
            )
            Log.i(TAG, "Sending AUTH to $peerId (${pubKey.size} bytes)")
            routingManager.sendTo(peerId, frame)
        }
    }

    // ------------------------------------------------------------------ group plumbing

    /**
     * Turns raw endpoints into group knowledge: peers advertising our group id go to the
     * session manager, everyone else is bucketed into the "groups you can join" list.
     */
    private suspend fun feedGroupDiscovery(peerList: List<Peer>) {
        val adverts = peerList.mapNotNull { peer ->
            GroupAdvert.decode(peer.name)?.let { peer to it }
        }

        val ourGroupId = groupState.value.group?.groupId
        adverts.forEach { (peer, advert) ->
            if (advert.groupId == ourGroupId) {
                groupManager.onEndpointDiscovered(peer.id, advert)
            }
        }

        // Only a coordinator's advert can be joined; members cannot admit newcomers.
        _discoveredGroups.value = adverts
            .filter { (_, advert) -> advert.groupId != ourGroupId }
            .groupBy { (_, advert) -> advert.groupId }
            .mapNotNull { (groupId, entries) ->
                val coordinator = entries.firstOrNull { it.second.role == GroupRole.COORDINATOR }
                    ?: return@mapNotNull null
                DiscoveredGroup(
                    groupId = groupId,
                    name = coordinator.second.displayName,
                    coordinatorMemberId = coordinator.second.memberId,
                    coordinatorEndpointId = coordinator.first.id,
                    devicesVisible = entries.size
                )
            }
    }

    /** Translates appearing/disappearing links into group connection events. */
    private suspend fun feedGroupLinkTransitions(
        peerList: List<Peer>,
        previouslyConnectedIds: Set<String>
    ) {
        val nowConnectedIds = peerList.filter { it.isConnected }.map { it.id }.toSet()

        (nowConnectedIds - previouslyConnectedIds).forEach { groupManager.onConnected(it) }
        (previouslyConnectedIds - nowConnectedIds).forEach { groupManager.onDisconnected(it) }
    }

    private suspend fun handleGroupControl(peerId: String, payload: ByteArray) {
        val message = GroupMessage.decode(payload)
        if (message == null) {
            Log.w(TAG, "undecodable GROUP_CONTROL from $peerId (${payload.size}B)")
            return
        }
        Log.i(TAG, "GROUP_CONTROL ${message::class.simpleName} from $peerId")
        groupManager.onControlMessage(peerId, message)
    }

    // ------------------------------------------------------------------- group actions

    fun createGroup(name: String) {
        viewModelScope.launch {
            Log.i(TAG, "createGroup '$name'")
            _currentChannelName.value = name
            groupManager.createGroup(name)
            transport.startDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun joinGroup(group: DiscoveredGroup) {
        viewModelScope.launch {
            Log.i(TAG, "joinGroup '${group.name}' via ${group.coordinatorEndpointId}")
            _currentChannelName.value = group.name
            groupManager.joinGroup(
                GroupAdvert(
                    groupId = group.groupId,
                    memberId = group.coordinatorMemberId,
                    role = GroupRole.COORDINATOR,
                    displayName = group.name
                ),
                group.coordinatorEndpointId
            )
        }
    }

    fun respondToGroupRequest(memberId: String, accept: Boolean) {
        viewModelScope.launch { groupManager.respondToRequest(memberId, accept) }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            groupManager.leaveGroup()
            setInSession(false)
            securedPeerIds.forEach { cryptoManager.removeSession(it) }
            securedPeerIds.clear()
            _securedPeerCount.value = 0
            transport.stop()
        }
    }

    /** Drops one device from the group without tearing the rest of it down. */
    fun removeGroupMember(memberId: String) {
        viewModelScope.launch {
            val endpointId = groupState.value.member(memberId)?.endpointId ?: return@launch
            cryptoManager.removeSession(endpointId)
            securedPeerIds.remove(endpointId)
            transport.disconnect(endpointId)
        }
    }

    private fun generateNonce(sessId: Long, seq: Int): ByteArray {
        val bb = ByteBuffer.allocate(12)
        bb.putLong(sessId)
        bb.putInt(seq)
        return bb.array()
    }

    fun setTransportType(type: TransportType) {
        _transportType.value = type
        transport.setTransportType(type)
    }

    /**
     * Advertise and discover at the same time so two phones running the same screen find
     * each other without either user having to pick a "host" or "join" role first.
     */
    fun startNearby() {
        viewModelScope.launch {
            transport.startSymmetricDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun switchToBleTransport() {
        Log.i(TAG, "Switching to BLE L2CAP transport (Wi-Fi off fallback)")
        viewModelScope.launch {
            transport.stop()
            transport.setTransportType(TransportType.BLE_L2CAP)
            _transportType.value = TransportType.BLE_L2CAP
            transport.startSymmetricDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun startDiscovery() {
        viewModelScope.launch { transport.startDiscovery(LocalIdentity(localDeviceName)) }
    }

    fun startAdvertising() {
        viewModelScope.launch { transport.startAdvertising(LocalIdentity(localDeviceName)) }
    }

    fun connectToPeer(peerId: String) {
        Log.i(TAG, "User tapped connect -> $peerId")
        viewModelScope.launch { transport.connect(peerId) }
    }

    fun acceptConnection(peerId: String) {
        viewModelScope.launch {
            transport.acceptConnection(peerId)
            _pendingRequest.value = null
        }
    }

    fun rejectConnection(peerId: String) {
        viewModelScope.launch {
            transport.rejectConnection(peerId)
            _pendingRequest.value = null
        }
    }

    fun clearPendingRequest() {
        _pendingRequest.value = null
    }

    fun setLoopbackMode(enabled: Boolean) {
        voiceEngine.isLoopback = enabled
    }

    fun toggleOutdoorMode() {
        _isOutdoorMode.value = !_isOutdoorMode.value
    }

    fun toggleRelayMode() {
        _isRelayModeActive.value = !_isRelayModeActive.value
    }

    fun toggleAudioRoute() {
        val newState = !_speakerphoneEnabled.value
        _speakerphoneEnabled.value = newState
        voiceEngine.speakerphoneEnabled = newState
        voiceEngine.setSpeakerphone(newState)
    }

    fun setInSession(inSession: Boolean) {
        _isInSession.value = inSession
        if (inSession) {
            startBackgroundService()
            // The handshake is over; a leftover request would send the pairing screen
            // straight back up the moment it is dismissed.
            _pendingRequest.value = null
            voiceEngine.routeManager?.requestAudioFocus()
            voiceEngine.setSpeakerphone(_speakerphoneEnabled.value)
            Log.i(TAG, "Session started: speaker=${_speakerphoneEnabled.value}")
        } else {
            stopBackgroundService()
            voiceEngine.routeManager?.clearRouting()
        }
    }

    fun setChannelName(name: String) {
        _currentChannelName.value = name
    }

    fun createChannel(name: String, relayPolicy: RelayPolicy = RelayPolicy.OFF) {
        _currentChannelName.value = name
        val hostCtrl = ChannelHostController(
            channelId = sessionId,
            channelName = name,
            hostFingerprint = cryptoManager.getPublicKeyBytes().take(6).toByteArray().joinToString("") { "%02X".format(it) },
            hostDisplayName = Build.MODEL,
            relayPolicy = relayPolicy,
            scope = viewModelScope
        )
        floorController.mode = SessionFloorMode.CHANNEL_HOST_CONTROLLED
        hostCtrl.onFloorStatusChanged = { holderId, holderName ->
            if (holderId == 0L) {
                floorController.onFloorFreed()
            } else if (holderId != hostCtrl.hostMemberId) {
                floorController.onFloorHeldByOther(holderId.toString(), holderName)
            }
            viewModelScope.launch {
                val nameBytes = holderName.toByteArray(Charsets.UTF_8)
                val payload = ByteBuffer.allocate(8 + nameBytes.size)
                    .putLong(holderId)
                    .put(nameBytes)
                    .array()
                val statusFrame = Frame(
                    type = FrameType.FLOOR_STATUS,
                    payload = payload,
                    sessionId = sessionId,
                    sequence = controlSequence.getAndIncrement(),
                    timestamp = System.currentTimeMillis().toInt()
                )
                _peers.value.filter { it.isConnected }.forEach { transport.send(it.id, statusFrame.toBytes()) }
            }
        }

        hostCtrl.onKeyEpochRotated = { newEpoch, newKey, authorized ->
            viewModelScope.launch {
                authorized.filter { it.role != MemberRole.HOST && it.endpointId != null }.forEach { member ->
                    val frame = Frame(
                        type = FrameType.KEY_UPDATE,
                        payload = newKey,
                        sessionId = sessionId,
                        sequence = newEpoch,
                        timestamp = System.currentTimeMillis().toInt()
                    )
                    member.endpointId?.let { ep -> transport.send(ep, frame.toBytes()) }
                }
            }
        }

        _channelHostController.value = hostCtrl
        startNearby()
        setInSession(true)
    }

    fun admitParticipantBySecret(displayName: String, role: MemberRole, secretOrCode: String): Boolean {
        val host = _channelHostController.value ?: return false
        val member = host.admitMember(displayName, role, null, secretOrCode)
        return member != null
    }

    fun toggleReplayBuffer(enabled: Boolean) {
        replayBuffer.isEnabled = enabled
    }

    fun playLatestReplay() {
        replayBuffer.playLatest()
    }

    fun sendTextMessage(content: String) {
        val seq = controlSequence.getAndIncrement()
        Log.i(TAG, "sendTextMessage \"${content.take(40)}\" to ${_peers.value.count { it.isConnected }} peer(s)")
        val textMsg = TextMessage(
            sequence = seq,
            senderMemberId = sessionId,
            senderName = Build.MODEL,
            content = content,
            status = TextDeliveryStatus.DELIVERED
        )
        _textMessages.value = _textMessages.value + textMsg

        val payload = content.toByteArray(Charsets.UTF_8)
        val frame = Frame(
            type = FrameType.TEXT,
            payload = payload,
            sequence = seq,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis().toInt()
        )
        _peers.value.filter { it.isConnected }.forEach {
            reliableMessageManager.sendReliable(
                memberId = sessionId,
                sequence = seq,
                endpointId = it.id,
                packetBytes = frame.toBytes()
            )
        }
    }

    fun requestPtt() {
        val current = floorController.floorState.value
        if (current !is FloorState.Idle) return
        if (_isRecording.value) return

        val connected = _peers.value.filter { it.isConnected }
        if (connected.isEmpty() || floorController.mode == SessionFloorMode.DIRECT_1_TO_1) {
            Log.i(TAG, "PTT start (direct) -> ${connected.size} peer(s)")
            floorController.requestPtt()
            _isRecording.value = true
            voiceEngine.startRecording { sequence, encodedFrame ->
                broadcastVoiceFrame(sequence, encodedFrame)
            }
        } else {
            Log.i(TAG, "PTT start (channel) -> requesting floor")
            floorController.requestPtt()
            sendControlFrame(null, FrameType.FLOOR_REQUEST)
            _isRecording.value = true
            voiceEngine.startRecording { sequence, encodedFrame ->
                floorController.onAudioCaptured(sequence, System.currentTimeMillis(), encodedFrame)
            }
        }
    }

    fun releasePtt() {
        if (!_isRecording.value && floorController.floorState.value is FloorState.Idle) return
        Log.i(TAG, "PTT stop")
        floorController.releasePtt()
        _isRecording.value = false
        voiceEngine.stopRecording()
        sendControlFrame(null, FrameType.FLOOR_RELEASE)
    }

    private fun startBackgroundService() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, WalkieTalkieForegroundService::class.java).apply {
                action = NotificationController.ACTION_START
                putExtra(NotificationController.EXTRA_SESSION_NAME, _currentChannelName.value)
                putExtra(NotificationController.EXTRA_STATUS, "Encrypted link active")
                putExtra(NotificationController.EXTRA_SPEAKER, "Floor: Idle")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service start failed: ${e.message}")
        }
    }

    private fun stopBackgroundService() {
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, WalkieTalkieForegroundService::class.java).apply {
                action = NotificationController.ACTION_STOP
            }
            app.startService(intent)
        } catch (e: Exception) {
            // Service was not running.
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopBackgroundService()
        voiceEngine.stopRecording()
        voiceEngine.stopPlayback()
        voiceEngine.routeManager?.clearRouting()
        teardownScope.launch { transport.stop() }
    }

    fun leaveSession() {
        voiceEngine.stopRecording()
        voiceEngine.stopPlayback()
        // Routed through setInSession so the audio mode and volumes are handed back;
        // setting the flag directly leaves the device in MODE_IN_COMMUNICATION.
        setInSession(false)
        securedPeerIds.forEach { cryptoManager.removeSession(it) }
        securedPeerIds.clear()
        _securedPeerCount.value = 0
        viewModelScope.launch { transport.stop() }
    }
}
