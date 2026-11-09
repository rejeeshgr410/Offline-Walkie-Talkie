package com.example.offlinewalkietalkie.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinewalkietalkie.crypto.CryptoManager
import com.example.offlinewalkietalkie.history.VoiceHistoryManager
import com.example.offlinewalkietalkie.history.VoiceMessage
import com.example.offlinewalkietalkie.data.OfflineMapArea
import com.example.offlinewalkietalkie.data.OfflineMapRepository
import com.example.offlinewalkietalkie.data.OfflineMapPoint
import com.example.offlinewalkietalkie.mesh.RoutingManager
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.protocol.Frame
import com.example.offlinewalkietalkie.protocol.FrameType
import com.example.offlinewalkietalkie.protocol.PeerLocation
import com.example.offlinewalkietalkie.protocol.PeerTelemetry
import com.example.offlinewalkietalkie.protocol.SosBeacon
import com.example.offlinewalkietalkie.protocol.SosAck
import com.example.offlinewalkietalkie.radar.CompassManager
import com.example.offlinewalkietalkie.radar.LocationProvider
import com.example.offlinewalkietalkie.service.FloatingPttService
import com.example.offlinewalkietalkie.sos.SosManager
import com.example.offlinewalkietalkie.telemetry.TelemetryManager
import com.example.offlinewalkietalkie.transport.ConnectionRequest
import com.example.offlinewalkietalkie.transport.LocalIdentity
import com.example.offlinewalkietalkie.transport.Peer
import com.example.offlinewalkietalkie.transport.TransportManager
import com.example.offlinewalkietalkie.transport.TransportSelector
import com.example.offlinewalkietalkie.transport.TransportType
import com.example.offlinewalkietalkie.voice.AmrNbCodec
import com.example.offlinewalkietalkie.voice.HardwarePttManager
import com.example.offlinewalkietalkie.voice.VoiceEngine
import java.io.ByteArrayOutputStream
import com.example.offlinewalkietalkie.channel.*
import com.example.offlinewalkietalkie.group.DiscoveredGroup
import com.example.offlinewalkietalkie.group.GroupAdvert
import com.example.offlinewalkietalkie.group.GroupMessage
import com.example.offlinewalkietalkie.group.GroupNearbyPort
import com.example.offlinewalkietalkie.group.GroupRole
import com.example.offlinewalkietalkie.group.GroupSessionManager
import com.example.offlinewalkietalkie.group.GroupState
import com.example.offlinewalkietalkie.group.isTransitional
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import com.example.offlinewalkietalkie.protocol.FloorController
import com.example.offlinewalkietalkie.protocol.SessionFloorMode
import com.example.offlinewalkietalkie.service.NotificationController
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TL/ViewModel"
        private const val GROUP_TICK_INTERVAL_MS = 1_000L
    }

    private val transport = TransportManager(application)
    private val routingManager = RoutingManager(transport)
    private val voiceEngine = VoiceEngine(codec = AmrNbCodec(), context = application)
    private val offlineMapRepository = OfflineMapRepository(application)
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

    private val _transportType = MutableStateFlow(TransportType.BLE_GATT)
    val transportType = _transportType.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isOutdoorMode = MutableStateFlow(false)
    val isOutdoorMode = _isOutdoorMode.asStateFlow()

    private val _isSurvivalCodecMode = MutableStateFlow(false)
    val isSurvivalCodecMode = _isSurvivalCodecMode.asStateFlow()

    private val _isRelayModeActive = MutableStateFlow(true)
    val isRelayModeActive = _isRelayModeActive.asStateFlow()

    private val _currentChannelName = MutableStateFlow("Direct Session")
    val currentChannelName = _currentChannelName.asStateFlow()

    private val _speakerphoneEnabled = MutableStateFlow(true)
    val speakerphoneEnabled = _speakerphoneEnabled.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

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

    private val requestPrefs = application.getSharedPreferences("intercom_request_policy", 0)
    private val _allowIncomingRequests = MutableStateFlow(requestPrefs.getBoolean("allow_incoming", true))
    val allowIncomingRequests = _allowIncomingRequests.asStateFlow()

    private val _disturbanceMode = MutableStateFlow(requestPrefs.getBoolean("disturbance_mode", false))
    val disturbanceMode = _disturbanceMode.asStateFlow()

    private val _offlineMapAreas = MutableStateFlow(offlineMapRepository.list())
    val offlineMapAreas = _offlineMapAreas.asStateFlow()
    private val _offlineMapProgressBytes = MutableStateFlow<Long?>(null)
    val offlineMapProgressBytes = _offlineMapProgressBytes.asStateFlow()
    private val _offlineMapError = MutableStateFlow<String?>(null)
    val offlineMapError = _offlineMapError.asStateFlow()
    private val _selectedOfflineMap = MutableStateFlow<OfflineMapArea?>(null)
    val selectedOfflineMap = _selectedOfflineMap.asStateFlow()
    private val _selectedOfflineMapRoads = MutableStateFlow<List<List<OfflineMapPoint>>>(emptyList())
    val selectedOfflineMapRoads = _selectedOfflineMapRoads.asStateFlow()
    private var offlineMapJob: Job? = null

    // --- 1. SOS Emergency Distress Subsystem ---
    val sosManager = SosManager(
        localSenderId = sessionId.toString(),
        localSenderName = localDeviceName,
        broadcastFrame = { beacon, ingressPeerId -> broadcastSosFrame(beacon, ingressPeerId) },
        scope = viewModelScope
    )
    val isBroadcastingSos = sosManager.isBroadcastingSos
    val mySosBeacon = sosManager.mySosBeacon
    val incomingAlerts = sosManager.incomingAlerts
    val isAlarmPlaying = sosManager.isAlarmPlaying
    val acknowledgedResponders = sosManager.acknowledgedResponders

    val offlineMapStorageUsed = MutableStateFlow(offlineMapRepository.getStorageUsedBytes())
    val offlineMapStorageQuota: Long = offlineMapRepository.getStorageQuotaBytes()

    // --- 2. Offline GPS Radar & Directional Compass ---
    val locationProvider = LocationProvider(application)
    val compassManager = CompassManager(application)
    val currentLocation = locationProvider.currentLocation
    val deviceHeading = compassManager.deviceHeading

    private val _peerLocations = MutableStateFlow<Map<String, PeerLocation>>(emptyMap())
    val peerLocations = _peerLocations.asStateFlow()

    private val _selectedRadarPeerId = MutableStateFlow<String?>(null)
    val selectedRadarPeerId = _selectedRadarPeerId.asStateFlow()

    // --- 3. Offline Voice Message History & Replay ---
    val voiceHistoryManager = VoiceHistoryManager(application)
    val voiceHistory = voiceHistoryManager.history
    val isHistoryPlaying = voiceHistoryManager.isPlaying
    val currentlyPlayingHistoryId = voiceHistoryManager.currentlyPlayingId

    private val outgoingAudioBuffer = ByteArrayOutputStream()
    private var outgoingStartTimeMs = 0L

    private val incomingAudioBuffer = ByteArrayOutputStream()
    private var incomingStartTimeMs = 0L
    private var activeIncomingSpeakerId: String? = null
    private var activeIncomingSpeakerName: String? = null

    // --- 4. Peer Battery & Signal Health Telemetry ---
    val telemetryManager = TelemetryManager(
        context = application,
        localPeerId = sessionId.toString(),
        broadcastTelemetry = { telemetry -> broadcastTelemetryFrame(telemetry) },
        scope = viewModelScope
    )
    val localBatteryPercent = telemetryManager.localBatteryPercent
    val isLocalCharging = telemetryManager.isLocalCharging
    val peerTelemetry = telemetryManager.peerTelemetry

    // --- 5. Screen-Off / Pocket & Hardware Key PTT ---
    val hardwarePttManager = HardwarePttManager(
        context = application,
        onPttPressed = { requestPtt() },
        onPttReleased = { releasePtt() }
    )
    val isVolumeKeyPttEnabled = hardwarePttManager.isVolumeKeyPttEnabled
    val isBluetoothScoActive = hardwarePttManager.isBluetoothScoActive

    // --- 6. Floating PTT Bubble Service ---
    private val _isFloatingPttActive = MutableStateFlow(false)
    val isFloatingPttActive = _isFloatingPttActive.asStateFlow()

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

        locationProvider.startTracking()
        compassManager.start()
        telemetryManager.start()

        sosManager.onSendAck = { ack, ingressPeerId ->
            broadcastSosAckFrame(ack, ingressPeerId)
        }

        // Vivo OEM style: Auto-start BLE Long-Range discovery & advertising
        startNearby()

        viewModelScope.launch {
            while (isActive) {
                delay(8000)
                broadcastCurrentLocation()
            }
        }

        voiceEngine.onRawPcmCaptured = { pcm ->
            synchronized(outgoingAudioBuffer) {
                outgoingAudioBuffer.write(pcm)
            }
        }

        voiceEngine.onRawPcmPlayed = { pcm ->
            synchronized(incomingAudioBuffer) {
                incomingAudioBuffer.write(pcm)
            }
        }

        transport.connectionRequests.onEach { request ->
            val advert = GroupAdvert.decode(request.peerName)
            Log.i(TAG, "Connection request from ${request.peerName} (${request.peerId}) group=${advert?.groupId}")
            if (advert != null) {
                // Group-aware path: same-group peers are admitted without a per-link prompt,
                // which is what makes more than two devices workable.
                groupManager.onConnectionRequest(request.peerId, advert, request.authCode)
            } else {
                if (_allowIncomingRequests.value) _pendingRequest.value = request
                else transport.rejectConnection(request.peerId)
            }
        }.launchIn(viewModelScope)

        // Drives connection timeouts, retries, heartbeats and coordinator failover.
        viewModelScope.launch {
            while (isActive) {
                delay(GROUP_TICK_INTERVAL_MS)
                groupManager.tick()
            }
        }

        // Phase B: Periodic transparent ECDH re-auth until session key is derived for connected peers
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val unauthed = _peers.value.filter { it.isConnected && !cryptoManager.hasSessionKey(it.id) }
                unauthed.forEach { peer ->
                    sendAuthFrame(peer.id)
                }
            }
        }

        transport.peers.onEach { rawPeerList ->
            val previouslyAuthenticated = _peers.value.count { it.isConnected && it.isAuthenticated }
            val previouslyConnectedIds = _peers.value.filter { it.isConnected }.map { it.id }.toSet()

            // Decorate peers with authenticated state from CryptoManager
            val peerList = rawPeerList.map { p ->
                p.copy(isAuthenticated = p.isConnected && cryptoManager.hasSessionKey(p.id))
            }
            _peers.value = peerList
            val connected = peerList.filter { it.isConnected }
            val authenticated = peerList.filter { it.isConnected && it.isAuthenticated }

            feedGroupDiscovery(peerList)
            feedGroupLinkTransitions(peerList, previouslyConnectedIds)
            Log.i(TAG, "Peers updated: ${peerList.size} visible, ${connected.size} link-connected, ${authenticated.size} authenticated")

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
            _securedPeerCount.value = authenticated.size

            // Only transition to in-session when cryptographic mutual authentication is complete (§P0-03 & Phase B)
            if (authenticated.isNotEmpty() && !_isInSession.value) {
                _currentChannelName.value = authenticated.first().name
                setInSession(true)
            } else if (authenticated.isEmpty() && previouslyAuthenticated > 0 && _isInSession.value) {
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
                    val authedCount = _peers.value.count { it.isConnected && cryptoManager.hasSessionKey(it.id) }
                    _securedPeerCount.value = authedCount
                    Log.i(TAG, "AUTH from $peerId - session key established (secured=$authedCount)")
                    if (isNew) sendAuthFrame(peerId)

                    // Re-evaluate authenticated peers and start session if needed
                    _peers.value = _peers.value.map { p ->
                        p.copy(isAuthenticated = p.isConnected && cryptoManager.hasSessionKey(p.id))
                    }
                    if (!_isInSession.value) {
                        val authedPeer = _peers.value.firstOrNull { it.id == peerId && it.isConnected && it.isAuthenticated }
                        if (authedPeer != null) {
                            _currentChannelName.value = authedPeer.name
                            setInSession(true)
                        }
                    }
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
                            Log.i(TAG, "Received VOICE seq=${decodedFrame.sequence} payload=${decodedFrame.payload.size}B codec=${decodedFrame.getCodecType()}")
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
                                encodedAudio = plaintext,
                                codecType = decodedFrame.getCodecType(),
                                senderTimestampMs = decodedFrame.timestamp.toLong() and 0xFFFFFFFFL
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
                        // P0-04 Remediation: Fail-closed security. Reject unauthenticated or unencrypted voice frames.
                        Log.w(TAG, "Dropping unauthenticated or unencrypted VOICE frame from $peerId seq=${decodedFrame.sequence}")
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

                            val inPcm = synchronized(incomingAudioBuffer) {
                                val bytes = incomingAudioBuffer.toByteArray()
                                incomingAudioBuffer.reset()
                                bytes
                            }
                            val durationMs = System.currentTimeMillis() - incomingStartTimeMs
                            val speakerId = activeIncomingSpeakerId
                            val speakerName = activeIncomingSpeakerName
                            if (inPcm.isNotEmpty() && durationMs > 300 && speakerId != null) {
                                viewModelScope.launch {
                                    voiceHistoryManager.saveTransmission(
                                        senderId = speakerId,
                                        senderName = speakerName ?: "Peer",
                                        channelName = _currentChannelName.value,
                                        isIncoming = true,
                                        durationMs = durationMs,
                                        audioPcmBytes = inPcm,
                                        sampleRate = voiceEngine.sampleRate
                                    )
                                }
                            }
                            activeIncomingSpeakerId = null
                            activeIncomingSpeakerName = null
                            updateFloatingOverlay(FloatingPttService.STATE_IDLE, "")
                        } else {
                            floorController.onFloorHeldByOther(holderId.toString(), holderName)
                            replayBuffer.onTransmissionStarted(holderId, holderName)

                            activeIncomingSpeakerId = holderId.toString()
                            activeIncomingSpeakerName = holderName
                            incomingStartTimeMs = System.currentTimeMillis()
                            synchronized(incomingAudioBuffer) { incomingAudioBuffer.reset() }
                            updateFloatingOverlay(FloatingPttService.STATE_RECEIVING, holderName)
                        }
                    }
                    shouldForwardControlFrame = true
                }
                FrameType.TEXT -> {
                    val peerName = _peers.value.find { it.id == peerId }?.name ?: "Peer"
                    val content: String? = if (decodedFrame.isEncrypted() && decodedFrame.authTag != null) {
                        val nonce = generateNonce(decodedFrame.sessionId, decodedFrame.sequence)
                        val dummyForAad = decodedFrame.copy(hopLimit = 0)
                        val headerAad = dummyForAad.toBytes().copyOfRange(0, Frame.HEADER_SIZE)
                        val decrypted = cryptoManager.decrypt(peerId, decodedFrame.payload, decodedFrame.authTag, nonce, headerAad)
                        decrypted?.let { String(it, Charsets.UTF_8) }
                    } else null

                    if (content != null) {
                        Log.i(TAG, "Received authenticated encrypted TEXT from $peerName seq=${decodedFrame.sequence} (len=${content.length})")
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
                            sessionId = decodedFrame.sessionId,
                            timestamp = System.currentTimeMillis().toInt()
                        )
                        transport.send(peerId, ack.toBytes())
                    } else {
                        Log.w(TAG, "Dropping unauthenticated or unencrypted TEXT from $peerId seq=${decodedFrame.sequence}")
                    }
                }
                FrameType.ACK -> {
                    reliableMessageManager.onAckReceived(sessionId, decodedFrame.sequence, peerId)
                }
                FrameType.KEY_UPDATE -> {
                    // P0-05 Remediation: Decrypt distributed symmetric channel key via pairwise session key
                    if (decodedFrame.isEncrypted() && decodedFrame.authTag != null) {
                        val nonce = generateNonce(decodedFrame.sessionId, decodedFrame.sequence)
                        val dummyForAad = decodedFrame.copy(hopLimit = 0)
                        val headerAad = dummyForAad.toBytes().copyOfRange(0, Frame.HEADER_SIZE)
                        val channelKeyBytes = cryptoManager.decrypt(peerId, decodedFrame.payload, decodedFrame.authTag, nonce, headerAad)
                        if (channelKeyBytes != null && channelKeyBytes.size >= 16) {
                            cryptoManager.installChannelKey(decodedFrame.sessionId, channelKeyBytes)
                            Log.i(TAG, "Successfully installed distributed channel voice key for channel=${decodedFrame.sessionId} (epoch=${decodedFrame.sequence})")
                        }
                    } else if (decodedFrame.payload.size == 32) {
                        cryptoManager.installChannelKey(decodedFrame.sessionId, decodedFrame.payload)
                        Log.i(TAG, "Installed pre-paired channel voice key for channel=${decodedFrame.sessionId}")
                    }
                }
                FrameType.GROUP_CONTROL -> {
                    handleGroupControl(peerId, decodedFrame.payload)
                }
                FrameType.SOS -> {
                    val beacon = SosBeacon.decode(decodedFrame.payload)
                    if (beacon != null) {
                        sosManager.handleIncomingSos(beacon, ingressPeerId = peerId)
                        if (_disturbanceMode.value) sosManager.silenceAlarm()
                        updateFloatingOverlay(FloatingPttService.STATE_SOS, "SOS!")
                    }
                }
                FrameType.SOS_ACK -> {
                    val ack = SosAck.decode(decodedFrame.payload)
                    if (ack != null) {
                        sosManager.handleIncomingSosAck(ack)
                    }
                }
                FrameType.LOCATION_RADAR -> {
                    val loc = PeerLocation.decode(decodedFrame.payload)
                    if (loc != null) {
                        val map = _peerLocations.value.toMutableMap()
                        map[loc.peerId] = loc
                        _peerLocations.value = map
                    }
                }
                FrameType.TELEMETRY -> {
                    val tel = PeerTelemetry.decode(decodedFrame.payload)
                    if (tel != null) {
                        telemetryManager.handleIncomingTelemetry(tel)
                    }
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

    /**
     * Gates voice on authenticated session keys and group verification, not raw transport connectivity. (P0-03 Fix)
     */
    fun isVoiceTrusted(peerId: String): Boolean {
        if (!cryptoManager.hasSessionKey(peerId)) return false
        val host = _channelHostController.value
        if (host != null) {
            val member = host.membersFlow.value.find { it.endpointId == peerId }
            return member != null
        }
        return true
    }

    fun getPeerSasCode(peerId: String): String {
        return cryptoManager.calculateNumericSas(peerId)
    }

    private suspend fun relayVoiceFrame(
        decodedFrame: Frame,
        plaintext: ByteArray,
        ingressPeerId: String
    ) {
        val nextHopLimit = (decodedFrame.hopLimit - 1).toByte()
        val activePeers = _peers.value.filter {
            it.isConnected && it.id != ingressPeerId && isVoiceTrusted(it.id)
        }
        activePeers.forEach { targetPeer ->
            if (!cryptoManager.hasSessionKey(targetPeer.id)) return@forEach
            val codecFlags = (decodedFrame.flags.toInt() and Frame.FLAG_CODEC_MASK.toInt()).toByte()
            val dummyFwd = Frame(
                type = FrameType.VOICE,
                flags = (Frame.FLAG_ENCRYPTED.toInt() or codecFlags.toInt()).toByte(),
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
        if (floorController.floorState.value !is FloorState.Requesting) return

        floorController.onFloorGranted()
        if (!_isRecording.value) {
            _isRecording.value = true
            voiceEngine.startRecording { sequence, encodedFrame ->
                broadcastVoiceFrame(sequence, encodedFrame)
            }
        }
    }

    private fun handleFloorRelease(peerId: String) {
        floorController.onFloorFreed()
    }

    private fun broadcastVoiceFrame(sequence: Int, encodedFrame: ByteArray) {
        viewModelScope.launch {
            val activePeers = _peers.value.filter { it.isConnected }
            if (activePeers.isNotEmpty() && (sequence == 1 || sequence % 50 == 0)) {
                Log.i(TAG, "Sending encrypted VOICE frame $sequence to ${activePeers.size} peer(s), payload=${encodedFrame.size}B")
            }
            val codecFlag: Byte = if (voiceEngine.isSurvivalCodecActive()) Frame.FLAG_CODEC_CODEC2 else Frame.FLAG_CODEC_AMR_NB

            activePeers.forEach { peer ->
                if (!cryptoManager.hasSessionKey(peer.id)) {
                    // P0-04 Remediation: Fail-closed security. Drop frame rather than transmitting plaintext audio.
                    return@forEach
                }

                val nonce = generateNonce(sessionId, sequence)
                val dummyFrame = Frame(
                    type = FrameType.VOICE,
                    flags = (Frame.FLAG_ENCRYPTED.toInt() or codecFlag.toInt()).toByte(),
                    hopLimit = 0,
                    sessionId = sessionId,
                    sequence = sequence,
                    timestamp = (System.currentTimeMillis() and 0xFFFFFFFFL).toInt(),
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
        val nowIds = peerList.map { it.id }.toSet()
        val nowConnectedIds = peerList.filter { it.isConnected }.map { it.id }.toSet()

        (nowConnectedIds - previouslyConnectedIds).forEach { groupManager.onConnected(it) }
        (previouslyConnectedIds - nowConnectedIds).forEach { groupManager.onDisconnected(it) }

        // A connection that disappears from the transport's peer list without ever
        // reaching isConnected=true was declined, timed out, or the radio dropped it
        // mid-handshake. Without this the group manager's CONNECTING/VERIFYING entry
        // would sit stuck forever instead of retrying or surfacing the failure.
        val everKnown = groupState.value.others
            .filter { it.endpointId != null && it.state.isTransitional }
            .map { it.memberId to it.endpointId!! }
        everKnown.forEach { (_, endpointId) ->
            if (endpointId !in nowIds && endpointId !in nowConnectedIds) {
                groupManager.onConnectionFailed(endpointId, "Connection was lost")
            }
        }
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
        if (!TransportSelector.isTransportAvailable(getApplication<Application>(), type)) {
            val name = TransportSelector.getDisplayName(type)
            _transportError.value = "$name is not supported on this device"
            Log.w(TAG, "Cannot switch to unsupported transport: $type")
            return
        }
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

    fun switchToBleGattTransport() {
        Log.i(TAG, "Switching to BLE Long-Range GATT transport (Vivo-style)")
        viewModelScope.launch {
            transport.stop()
            transport.setTransportType(TransportType.BLE_GATT)
            _transportType.value = TransportType.BLE_GATT
            transport.startSymmetricDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun switchToNearbyTransport() {
        if (!TransportSelector.isTransportAvailable(getApplication<Application>(), TransportType.NEARBY_CONNECTIONS)) {
            _transportError.value = "Google Nearby Connections is not available on this device"
            Log.w(TAG, "Google Nearby Connections not available")
            return
        }
        Log.i(TAG, "Switching to Google Nearby Connections transport")
        viewModelScope.launch {
            transport.stop()
            transport.setTransportType(TransportType.NEARBY_CONNECTIONS)
            _transportType.value = TransportType.NEARBY_CONNECTIONS
            transport.startSymmetricDiscovery(LocalIdentity(localDeviceName))
        }
    }

    fun switchToBleTransport() {
        if (!TransportSelector.isTransportAvailable(getApplication<Application>(), TransportType.BLE_L2CAP)) {
            _transportError.value = "BLE L2CAP CoC requires Android 10+ and hardware support"
            Log.w(TAG, "BLE L2CAP not available")
            return
        }
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

    fun disconnectPeer(peerId: String) {
        Log.i(TAG, "User tapped disconnect -> $peerId")
        viewModelScope.launch {
            cryptoManager.removeSession(peerId)
            securedPeerIds.remove(peerId)
            transport.disconnect(peerId)
        }
    }

    fun acceptConnection(peerId: String) {
        viewModelScope.launch {
            transport.acceptConnection(peerId)
            _pendingRequest.value = null
            sendAuthFrame(peerId)
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

    fun setDisturbanceMode(enabled: Boolean) {
        _disturbanceMode.value = enabled
        requestPrefs.edit().putBoolean("disturbance_mode", enabled).apply()
    }

    fun setAllowIncomingRequests(enabled: Boolean) {
        _allowIncomingRequests.value = enabled
        requestPrefs.edit().putBoolean("allow_incoming", enabled).apply()
    }

    fun downloadOfflineMap(name: String, latitude: Double, longitude: Double, radiusKm: Double) {
        if (offlineMapJob?.isActive == true) return
        _offlineMapError.value = null
        _offlineMapProgressBytes.value = 0L
        offlineMapJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                offlineMapRepository.download(name, latitude, longitude, radiusKm) { bytes ->
                    _offlineMapProgressBytes.value = bytes.takeIf { it >= 0L }
                }
                _offlineMapAreas.value = offlineMapRepository.list()
                _offlineMapProgressBytes.value = null
                refreshOfflineMapStorage()
            } catch (e: CancellationException) {
                _offlineMapProgressBytes.value = null
                throw e
            } catch (e: Exception) {
                if (!isActive) return@launch
                _offlineMapError.value = e.message ?: "Map download failed."
                _offlineMapProgressBytes.value = null
            }
        }
    }

    fun cancelOfflineMapDownload() {
        offlineMapJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) { offlineMapRepository.cancelActiveDownload() }
        offlineMapProgressBytes.value?.let { _offlineMapProgressBytes.value = null }
    }

    fun restoreBundledMapPacks() {
        _offlineMapAreas.value = offlineMapRepository.restoreBundledPacks()
        refreshOfflineMapStorage()
    }

    fun refreshOfflineMapStorage() {
        offlineMapStorageUsed.value = offlineMapRepository.getStorageUsedBytes()
    }

    fun clearAllOfflineMaps() {
        offlineMapRepository.deleteAllDownloadedAreas()
        _offlineMapAreas.value = offlineMapRepository.list()
        _selectedOfflineMap.value = null
        _selectedOfflineMapRoads.value = emptyList()
        refreshOfflineMapStorage()
    }

    fun deleteOfflineMap(area: OfflineMapArea) {
        offlineMapRepository.delete(area)
        _offlineMapAreas.value = offlineMapRepository.list()
        refreshOfflineMapStorage()
        if (_selectedOfflineMap.value?.id == area.id) closeOfflineMap()
    }

    fun openOfflineMap(area: OfflineMapArea) {
        _selectedOfflineMap.value = area
        viewModelScope.launch(Dispatchers.IO) { _selectedOfflineMapRoads.value = offlineMapRepository.readRoads(area) }
    }

    fun closeOfflineMap() {
        _selectedOfflineMap.value = null
        _selectedOfflineMapRoads.value = emptyList()
    }

    fun markTextMessagesRead() {
        _textMessages.value = _textMessages.value.map { msg ->
            if (msg.senderMemberId != sessionId) msg.copy(isRead = true) else msg
        }
    }

    fun markTextMessageRead(messageId: String) {
        _textMessages.value = _textMessages.value.map { msg -> if (msg.messageId == messageId) msg.copy(isRead = true) else msg }
    }

    fun deleteTextMessage(messageId: String) {
        _textMessages.value = _textMessages.value.filterNot { it.messageId == messageId }
    }

    fun resendTextMessage(messageId: String) {
        val old = _textMessages.value.firstOrNull { it.messageId == messageId } ?: return
        if (old.status != TextDeliveryStatus.FAILED) return
        val endpoints = _peers.value.filter { it.isConnected }.map { it.id }
        if (endpoints.isEmpty()) return
        _textMessages.value = _textMessages.value.map {
            if (it.messageId == messageId) it.copy(status = TextDeliveryStatus.SENDING) else it
        }
        val frame = Frame(
            type = FrameType.TEXT,
            payload = old.content.toByteArray(Charsets.UTF_8),
            sequence = old.sequence,
            sessionId = old.senderMemberId,
            timestamp = old.timestampEpochMs.toInt()
        )
        endpoints.forEach { endpoint ->
            reliableMessageManager.sendReliable(
                memberId = old.senderMemberId,
                sequence = old.sequence,
                endpointId = endpoint,
                packetBytes = frame.toBytes(),
                onDelivered = {
                    _textMessages.value = _textMessages.value.map {
                        if (it.messageId == messageId) it.copy(status = TextDeliveryStatus.DELIVERED) else it
                    }
                },
                onFailed = {
                    _textMessages.value = _textMessages.value.map {
                        if (it.messageId == messageId && it.status == TextDeliveryStatus.SENDING) it.copy(status = TextDeliveryStatus.FAILED) else it
                    }
                }
            )
        }
    }

    fun setLoopbackMode(enabled: Boolean) {
        voiceEngine.isLoopback = enabled
    }

    fun toggleOutdoorMode() {
        _isOutdoorMode.value = !_isOutdoorMode.value
    }

    fun toggleSurvivalCodecMode() {
        val next = !_isSurvivalCodecMode.value
        _isSurvivalCodecMode.value = next
        voiceEngine.setSurvivalCodecMode(next)
    }

    fun setSurvivalCodecMode(enabled: Boolean) {
        _isSurvivalCodecMode.value = enabled
        voiceEngine.setSurvivalCodecMode(enabled)
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

    /** Suppress live received playback while keeping PTT and message history available. */
    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        voiceEngine.isRealtimeMuted = _isMuted.value
    }

    fun setInSession(inSession: Boolean) {
        _isInSession.value = inSession
        if (!inSession) {
            _isMuted.value = false
            voiceEngine.isRealtimeMuted = false
        }
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
                    val ep = member.endpointId ?: return@forEach
                    if (!cryptoManager.hasSessionKey(ep)) return@forEach

                    // P0-05 Remediation: Encrypt distributed channel voice key with each member's pairwise session key
                    val nonce = generateNonce(sessionId, newEpoch)
                    val dummyFrame = Frame(
                        type = FrameType.KEY_UPDATE,
                        flags = Frame.FLAG_ENCRYPTED,
                        hopLimit = 0,
                        sessionId = sessionId,
                        sequence = newEpoch,
                        timestamp = (System.currentTimeMillis() and 0xFFFFFFFFL).toInt(),
                        payload = ByteArray(newKey.size)
                    )
                    val headerAad = dummyFrame.toBytes().copyOfRange(0, Frame.HEADER_SIZE)
                    val encrypted = cryptoManager.encrypt(ep, newKey, nonce, headerAad) ?: return@forEach

                    val frame = dummyFrame.copy(
                        hopLimit = 3,
                        payload = encrypted.first,
                        authTag = encrypted.second
                    )
                    transport.send(ep, frame.toBytes())
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
        val connectedPeers = _peers.value.filter { it.isConnected }
        Log.i(TAG, "sendTextMessage (len=${content.length}) to ${connectedPeers.size} peer(s)")
        val textMsg = TextMessage(
            sequence = seq,
            senderMemberId = sessionId,
            senderName = Build.MODEL,
            content = content,
            status = if (connectedPeers.isNotEmpty()) TextDeliveryStatus.SENDING else TextDeliveryStatus.FAILED
        )
        _textMessages.value = _textMessages.value + textMsg

        val payload = content.toByteArray(Charsets.UTF_8)
        if (connectedPeers.isEmpty()) return

        // P0-04 Remediation: Encrypt text messages per-peer with AES-256-GCM session keys
        connectedPeers.forEach { peer ->
            if (!cryptoManager.hasSessionKey(peer.id)) {
                Log.w(TAG, "Cannot send text: peer ${peer.id} has no active session key")
                return@forEach
            }
            val nonce = generateNonce(sessionId, seq)
            val dummyFrame = Frame(
                type = FrameType.TEXT,
                flags = Frame.FLAG_ENCRYPTED,
                hopLimit = 0,
                sessionId = sessionId,
                sequence = seq,
                timestamp = System.currentTimeMillis().toInt(),
                payload = ByteArray(payload.size)
            )
            val headerAad = dummyFrame.toBytes().copyOfRange(0, Frame.HEADER_SIZE)
            val encrypted = cryptoManager.encrypt(peer.id, payload, nonce, headerAad) ?: return@forEach

            val frame = dummyFrame.copy(
                hopLimit = 3,
                payload = encrypted.first,
                authTag = encrypted.second
            )

            reliableMessageManager.sendReliable(
                memberId = sessionId,
                sequence = seq,
                endpointId = peer.id,
                packetBytes = frame.toBytes(),
                onDelivered = {
                    _textMessages.value = _textMessages.value.map { current ->
                        if (current.messageId == textMsg.messageId) current.copy(status = TextDeliveryStatus.DELIVERED) else current
                    }
                },
                onFailed = {
                    _textMessages.value = _textMessages.value.map { current ->
                        if (current.messageId == textMsg.messageId && current.status == TextDeliveryStatus.SENDING) current.copy(status = TextDeliveryStatus.FAILED) else current
                    }
                }
            )
        }
    }

    fun requestPtt() {
        val current = floorController.floorState.value
        if (current !is FloorState.Idle) return
        if (_isRecording.value) return

        synchronized(outgoingAudioBuffer) { outgoingAudioBuffer.reset() }
        outgoingStartTimeMs = System.currentTimeMillis()
        updateFloatingOverlay(FloatingPttService.STATE_TRANSMITTING, "TALKING")

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
                val sendLive = floorController.onAudioCaptured(sequence, System.currentTimeMillis(), encodedFrame)
                if (sendLive) {
                    broadcastVoiceFrame(sequence, encodedFrame)
                }
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

        val pcm = synchronized(outgoingAudioBuffer) { outgoingAudioBuffer.toByteArray() }
        val durationMs = System.currentTimeMillis() - outgoingStartTimeMs
        if (pcm.isNotEmpty() && durationMs > 300) {
            viewModelScope.launch {
                voiceHistoryManager.saveTransmission(
                    senderId = sessionId.toString(),
                    senderName = localDeviceName,
                    channelName = _currentChannelName.value,
                    isIncoming = false,
                    durationMs = durationMs,
                    audioPcmBytes = pcm,
                    sampleRate = voiceEngine.sampleRate
                )
            }
        }
        updateFloatingOverlay(FloatingPttService.STATE_IDLE, "")
    }

    fun startSos(message: String) {
        sosManager.startSos(
            message = message,
            location = currentLocation.value,
            batteryPercent = localBatteryPercent.value
        )
        updateFloatingOverlay(FloatingPttService.STATE_SOS, "SOS!")
    }

    fun stopSos() {
        sosManager.stopSos()
        updateFloatingOverlay(FloatingPttService.STATE_IDLE, "")
    }

    fun silenceAlarm() = sosManager.silenceAlarm()

    fun dismissSosAlert(senderId: String) = sosManager.dismissAlert(senderId)

    private suspend fun broadcastSosAckFrame(ack: SosAck, ingressPeerId: String?) {
        val payload = ack.toBytes()
        val frame = Frame(
            type = FrameType.SOS_ACK,
            flags = 0,
            hopLimit = 3,
            sessionId = sessionId,
            sequence = controlSequence.incrementAndGet(),
            timestamp = (System.currentTimeMillis() and 0xFFFFFFFFL).toInt(),
            payload = payload
        )
        val bytes = frame.toBytes()
        _peers.value.filter { it.isConnected && it.id != ingressPeerId }.forEach {
            transport.send(it.id, bytes)
        }
    }

    private suspend fun broadcastSosFrame(beacon: SosBeacon, ingressPeerId: String?) {
        val payload = beacon.toBytes()
        val frame = Frame(
            type = FrameType.SOS,
            flags = 0,
            hopLimit = beacon.ttl.toByte(),
            sessionId = sessionId,
            sequence = controlSequence.incrementAndGet(),
            timestamp = System.currentTimeMillis().toInt(),
            payload = payload
        )
        val bytes = frame.toBytes()
        _peers.value.filter { it.isConnected && it.id != ingressPeerId }.forEach {
            transport.send(it.id, bytes)
        }
    }

    private suspend fun broadcastCurrentLocation() {
        val loc = currentLocation.value ?: return
        val peerLocation = PeerLocation(
            peerId = sessionId.toString(),
            peerName = localDeviceName,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = loc.altitude,
            batteryPercent = localBatteryPercent.value,
            timestamp = System.currentTimeMillis()
        )
        val payload = peerLocation.toBytes()
        val frame = Frame(
            type = FrameType.LOCATION_RADAR,
            sessionId = sessionId,
            sequence = controlSequence.incrementAndGet(),
            timestamp = System.currentTimeMillis().toInt(),
            payload = payload
        )
        val bytes = frame.toBytes()
        _peers.value.filter { it.isConnected }.forEach {
            transport.send(it.id, bytes)
        }
    }

    private suspend fun broadcastTelemetryFrame(telemetry: PeerTelemetry) {
        val frame = Frame(
            type = FrameType.TELEMETRY,
            sessionId = sessionId,
            sequence = controlSequence.incrementAndGet(),
            timestamp = System.currentTimeMillis().toInt(),
            payload = telemetry.toBytes()
        )
        val bytes = frame.toBytes()
        _peers.value.filter { it.isConnected }.forEach {
            transport.send(it.id, bytes)
        }
    }

    fun selectRadarPeer(peerId: String?) {
        _selectedRadarPeerId.value = peerId
    }

    fun replayMessage(message: VoiceMessage) {
        voiceHistoryManager.replayMessage(message)
    }

    fun replayLastMessage() {
        voiceHistoryManager.replayLastMessage()
    }

    fun stopReplay() {
        voiceHistoryManager.stopReplay()
    }

    fun setVolumeKeyPttEnabled(enabled: Boolean) {
        hardwarePttManager.setVolumeKeyPttEnabled(enabled)
    }

    fun toggleBluetoothSco() {
        if (isBluetoothScoActive.value) {
            hardwarePttManager.stopBluetoothSco()
        } else {
            hardwarePttManager.startBluetoothSco()
        }
    }

    fun startFloatingPtt(): Boolean {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            return false
        }
        val intent = Intent(app, FloatingPttService::class.java).apply {
            action = FloatingPttService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        _isFloatingPttActive.value = true
        return true
    }

    fun stopFloatingPtt() {
        val app = getApplication<Application>()
        val intent = Intent(app, FloatingPttService::class.java).apply {
            action = FloatingPttService.ACTION_STOP
        }
        app.startService(intent)
        _isFloatingPttActive.value = false
    }

    private fun updateFloatingOverlay(state: String, speakerName: String) {
        if (!_isFloatingPttActive.value) return
        try {
            val app = getApplication<Application>()
            val intent = Intent(app, FloatingPttService::class.java).apply {
                action = FloatingPttService.ACTION_UPDATE_STATE
                putExtra(FloatingPttService.EXTRA_STATE, state)
                putExtra(FloatingPttService.EXTRA_SPEAKER_NAME, speakerName)
            }
            app.startService(intent)
        } catch (e: Exception) {
            // Ignore
        }
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
        locationProvider.stopTracking()
        compassManager.stop()
        telemetryManager.stop()
        hardwarePttManager.stopBluetoothSco()
        stopFloatingPtt()
        sosManager.silenceAlarm()
        voiceHistoryManager.stopReplay()
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
