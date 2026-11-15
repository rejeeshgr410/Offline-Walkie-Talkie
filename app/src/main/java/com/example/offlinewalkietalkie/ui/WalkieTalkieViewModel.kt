package com.example.offlinewalkietalkie.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlinewalkietalkie.crypto.CryptoManager
import com.example.offlinewalkietalkie.diagnostics.LinkTelemetry
import com.example.offlinewalkietalkie.diagnostics.LinkTestController
import com.example.offlinewalkietalkie.diagnostics.RedactedDiagnosticsExporter
import com.example.offlinewalkietalkie.history.VoiceHistoryManager
import com.example.offlinewalkietalkie.history.VoiceMessage
import com.example.offlinewalkietalkie.data.OfflineMapArea
import com.example.offlinewalkietalkie.data.OfflineMapRepository
import com.example.offlinewalkietalkie.data.OfflineMapPoint
import com.example.offlinewalkietalkie.crypto.TrustedReconnectProof
import com.example.offlinewalkietalkie.identity.DirectPeerTrust
import com.example.offlinewalkietalkie.data.TrustedContactsRepository
import com.example.offlinewalkietalkie.data.mbtiles.MbTilesPackage
import com.example.offlinewalkietalkie.data.mbtiles.MbTilesRepository
import com.example.offlinewalkietalkie.data.mbtiles.MbTile
import com.example.offlinewalkietalkie.voice.AudioRoute
import android.net.Uri
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
import com.example.offlinewalkietalkie.voice.InstantReplayBuffer
import com.example.offlinewalkietalkie.voice.RogerBeepGenerator
import com.example.offlinewalkietalkie.voice.RogerBeepStyle
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
import com.example.offlinewalkietalkie.service.ForegroundServiceStartGate
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class WalkieTalkieViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "TL/ViewModel"
        private const val GROUP_TICK_INTERVAL_MS = 1_000L
    }

    private val cryptoManager = CryptoManager(application)
    private val trustedContactsRepository = TrustedContactsRepository(application)
    private val transport = TransportManager(application, cryptoManager.getIdentityFingerprintHex())
    private val routingManager = RoutingManager(transport)
    private val voiceEngine = VoiceEngine(codec = AmrNbCodec(), context = application)
    private val offlineMapRepository = OfflineMapRepository(application)
    private val mbTilesRepository = MbTilesRepository(application)
    private val sessionId: Long = System.currentTimeMillis()
    private val meshRouter = com.example.offlinewalkietalkie.mesh.MeshRelayRouter(sessionId)
    private var codecNegotiationJob: Job? = null
    private var codecProposal: com.example.offlinewalkietalkie.voice.CodecProposal? = null
    private val foregroundServiceStartGate = ForegroundServiceStartGate()

    private val linkTestController = LinkTestController(
        sendPing = { peerId, timestampMs ->
            sendEncryptedDiagnosticFrame(
                peerId = peerId,
                type = FrameType.PING,
                payload = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(timestampMs).array()
            )
        },
        scope = viewModelScope
    )
    val linkTelemetry: StateFlow<LinkTelemetry> = linkTestController.telemetry
    private val _isLinkTestRunning = MutableStateFlow(false)
    val isLinkTestRunning = _isLinkTestRunning.asStateFlow()
    private val _linkTestStatus = MutableStateFlow("Connect a verified peer to run an offline link test.")
    val linkTestStatus = _linkTestStatus.asStateFlow()
    private val _diagnosticsReport = MutableStateFlow<String?>(null)
    val diagnosticsReport = _diagnosticsReport.asStateFlow()
    private var linkTestJob: Job? = null

    /** viewModelScope is already cancelled by the time onCleared runs, so shutdown needs its own. */
    private val teardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val groupPort = GroupNearbyPort(transport, sessionId) { endpoint, payload ->
        // Admission secrets must not rely on the bearer being encrypted (GATT is not).
        // Wait for the human-confirmed identity exchange, then use per-link AEAD.
        var attempts = 0
        while (!cryptoManager.hasSessionKey(endpoint) && attempts++ < 50) delay(100)
        if (cryptoManager.hasSessionKey(endpoint) && isPairingApproved(endpoint)) {
            sendEncryptedDiagnosticFrame(endpoint, FrameType.GROUP_CONTROL, payload)
        } else {
            _transportError.value = "Group admission could not establish an encrypted link. Try joining again."
        }
    }
    private val recoveryStore = runCatching { com.example.offlinewalkietalkie.data.SessionRecoveryStore(application) }.getOrNull()
    private val savedGroupCheckpoint = runCatching { recoveryStore?.load() }.getOrNull()

    val groupManager = GroupSessionManager(
        transport = groupPort,
        localDisplayName = Build.MODEL,
        localMemberId = savedGroupCheckpoint?.let { runCatching { org.json.JSONObject(it).getString("local") }.getOrNull() }
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

    // A physical GATT link is deliberately not a TalkLoop session.  Both
    // people must confirm the displayed code before ECDH/auth is allowed.
    private val pairingCodes = mutableMapOf<String, String>()
    private val pairingIdentityKeys = mutableMapOf<String, String>()
    private val locallyConfirmedPairings = mutableSetOf<String>()
    private val remotelyConfirmedPairings = mutableSetOf<String>()
    private val autoConfirmingPairings = mutableSetOf<String>()
    private class PairingAttempt(val request: ConnectionRequest) {
        var decision: DirectPeerTrust.Decision? = null
    }
    private val pairingAttempts = mutableMapOf<String, PairingAttempt>()
    private val reconnectProofs = mutableMapOf<String, TrustedReconnectProof>()
    private val reconnectProofDeadlines = mutableMapOf<String, Job>()
    private val lastAuthReplyAtMs = mutableMapOf<String, Long>()

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

    val rogerBeepGenerator = RogerBeepGenerator(scope = viewModelScope)
    val instantReplayBuffer = InstantReplayBuffer(sampleRate = voiceEngine.sampleRate, scope = viewModelScope)

    private val _textMessages = MutableStateFlow<List<TextMessage>>(emptyList())
    val textMessages = _textMessages.asStateFlow()

    private val requestPrefs = application.getSharedPreferences("intercom_request_policy", 0)
    val adaptiveAudioEnabled = MutableStateFlow(requestPrefs.getBoolean("adaptive_audio", true))
    val relayOnlyJoining = MutableStateFlow(requestPrefs.getBoolean("relay_only_join", false))
    fun setRelayOnlyJoining(enabled: Boolean) {
        if (groupState.value.isInGroup) {
            _transportError.value = "Leave the current group before changing relay role."
            return
        }
        relayOnlyJoining.value = enabled
        requestPrefs.edit().putBoolean("relay_only_join", enabled).apply()
    }

    fun setAdaptiveAudioEnabled(enabled: Boolean) {
        adaptiveAudioEnabled.value = enabled
        requestPrefs.edit().putBoolean("adaptive_audio", enabled).apply()
    }
    private val _allowIncomingRequests = MutableStateFlow(requestPrefs.getBoolean("allow_incoming", true))
    val allowIncomingRequests = _allowIncomingRequests.asStateFlow()

    private val _disturbanceMode = MutableStateFlow(requestPrefs.getBoolean("disturbance_mode", false))
    val disturbanceMode = _disturbanceMode.asStateFlow()

    private val _rogerBeepStyle = MutableStateFlow(
        RogerBeepStyle.fromId(requestPrefs.getString("roger_beep_style", RogerBeepStyle.MOTOROLA.id) ?: RogerBeepStyle.MOTOROLA.id)
    )
    val rogerBeepStyle = _rogerBeepStyle.asStateFlow()

    val canInstantReplay = instantReplayBuffer.hasAudio
    val isInstantReplayPlaying = instantReplayBuffer.isPlaying
    val instantReplayDurationMs = instantReplayBuffer.bufferedDurationMs

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

    // --- MBTiles Offline Vector & Raster Tile Packages ---
    private val _mbTilesPackages = MutableStateFlow(mbTilesRepository.listPackages())
    val mbTilesPackages = _mbTilesPackages.asStateFlow()
    private val _selectedMbTilesPackage = MutableStateFlow<MbTilesPackage?>(null)
    val selectedMbTilesPackage = _selectedMbTilesPackage.asStateFlow()
    private val _selectedTilePreview = MutableStateFlow<MbTile?>(null)
    val selectedTilePreview = _selectedTilePreview.asStateFlow()
    val mbTilesStorageUsed = MutableStateFlow(mbTilesRepository.getStorageUsedBytes())
    val mbTilesStorageQuota: Long = mbTilesRepository.getStorageQuotaBytes()

    // --- 2. Offline GPS Radar & Directional Compass ---
    val locationProvider = LocationProvider(application)
    val compassManager = CompassManager(application)
    val currentLocation = locationProvider.currentLocation
    val deviceHeading = compassManager.deviceHeading

    private val _peerLocations = MutableStateFlow<Map<String, PeerLocation>>(emptyMap())
    val peerLocations = _peerLocations.asStateFlow()

    private val _selectedRadarPeerId = MutableStateFlow<String?>(null)
    val selectedRadarPeerId = _selectedRadarPeerId.asStateFlow()

    // Deduplication cache for incoming text messages (retains up to 1000 latest packet keys)
    private val seenTextMessageKeys = java.util.Collections.synchronizedSet(java.util.LinkedHashSet<String>())

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

    val currentAudioRoute: StateFlow<AudioRoute> = voiceEngine.routeManager?.currentRoute
        ?: MutableStateFlow(AudioRoute.SPEAKER).asStateFlow()
    val availableAudioRoutes: StateFlow<List<AudioRoute>> = voiceEngine.routeManager?.availableRoutes
        ?: MutableStateFlow(listOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE)).asStateFlow()

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
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                if (adaptiveAudioEnabled.value && _isInSession.value &&
                    floorState.value is FloorState.Idle && !_isRecording.value) runLinkTest()
            }
        }
        viewModelScope.launch {
            savedGroupCheckpoint?.let { groupManager.restoreCheckpoint(it) }
            groupState.collect { state ->
                state.others.filter { it.humanVerified && it.endpointId != null }.forEach { member ->
                    if (!cryptoManager.hasSessionKey(member.endpointId!!)) sendAuthFrame(member.endpointId)
                }
                val checkpoint = groupManager.recoveryCheckpoint()
                // Keep the last admitted checkpoint during transient verification.
                if (checkpoint != null || !state.isInGroup) withContext(Dispatchers.IO) {
                    runCatching { recoveryStore?.save(checkpoint) }.onFailure {
                        _transportError.value = "Session recovery could not be saved securely."
                    }
                }
            }
        }
        viewModelScope.launch {
            androidx.work.WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWorkFlow(com.example.offlinewalkietalkie.data.MapDownloadWorker.UNIQUE_NAME)
                .collect { jobs ->
                    val currentId = getApplication<Application>().getSharedPreferences("map_download", 0).getString("work_id", null)
                    val job = jobs.firstOrNull { it.id.toString() == currentId } ?: return@collect
                    _offlineMapProgressBytes.value = if (!job.state.isFinished)
                        job.progress.getLong("bytes", 0L).coerceAtLeast(0) else null
                    if (job.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                        withContext(Dispatchers.IO) {
                            _offlineMapAreas.value = offlineMapRepository.list()
                            refreshOfflineMapStorage()
                        }
                    }
                    if (job.state == androidx.work.WorkInfo.State.FAILED)
                        _offlineMapError.value = job.outputData.getString("error")
                }
        }
        if (requestPrefs.getBoolean("resume_requested", false)) {
            _currentChannelName.value = requestPrefs.getString("resume_name", "Direct Session") ?: "Direct Session"
            // Never restore authenticated flags or link keys from disk. The existing
            // trusted-identity discovery path must perform a fresh handshake first.
            _linkTestStatus.value = "Restoring the previous session; waiting for a verified peer."
        }
        viewModelScope.launch {
            val store = runCatching { withContext(Dispatchers.IO) {
                com.example.offlinewalkietalkie.data.TextMessageStore(getApplication())
            } }.getOrElse {
                _transportError.value = "Encrypted message history is unavailable on this device."
                return@launch
            }
            val saved = runCatching { withContext(Dispatchers.IO) { store.load() } }.getOrDefault(emptyList())
            _textMessages.value = (saved + _textMessages.value).distinctBy { it.messageId }
            _textMessages.collect { messages ->
                runCatching { withContext(Dispatchers.IO) { store.save(messages) } }.onFailure {
                    _transportError.value = "Message history could not be saved securely."
                }
            }
        }
        WalkieTalkieForegroundService.onPttPressFromHeadset = { requestPtt() }
        WalkieTalkieForegroundService.onPttReleaseFromHeadset = { releasePtt() }
        WalkieTalkieForegroundService.onPttToggleFromNotification = {
            if (floorState.value is FloorState.Idle) requestPtt() else releasePtt()
        }
        WalkieTalkieForegroundService.onLeaveFromNotification = { leaveSession() }
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

        transport.lastErrors.onEach { _transportError.value = it }.launchIn(viewModelScope)
        transport.isAdvertising.onEach { _isAdvertising.value = it }.launchIn(viewModelScope)
        transport.isDiscovering.onEach { _isDiscovering.value = it }.launchIn(viewModelScope)
        transport.activeTransportType.onEach { _transportType.value = it }.launchIn(viewModelScope)

        locationProvider.startTracking()
        compassManager.start()
        telemetryManager.start()

        sosManager.onSendAck = { ack, ingressPeerId ->
            broadcastSosAckFrame(ack, ingressPeerId)
        }
        sosManager.onBroadcastCancel = { senderId ->
            broadcastSosCancelFrame(senderId)
        }

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
            instantReplayBuffer.write(pcm)
        }

        voiceEngine.onRawPcmPlayed = { pcm ->
            synchronized(incomingAudioBuffer) {
                incomingAudioBuffer.write(pcm)
            }
            instantReplayBuffer.write(pcm)
        }

        transport.connectionRequests.onEach { request ->
            val advert = GroupAdvert.decode(request.peerName)
            Log.i(TAG, "Connection request from ${request.peerName} (${request.peerId}) group=${advert?.groupId}")
            if (advert != null) {
                // Group-aware path: same-group peers are admitted without a per-link prompt,
                // which is what makes more than two devices workable.
                groupManager.onConnectionRequest(request.peerId, advert, request.authCode)
            } else {
                handlePairingRequest(request)
            }
        }.launchIn(viewModelScope)

        // StateFlow replay closes the race where a GATT service callback wins
        // against collection during cold app startup.
        transport.pendingConnectionRequests.filterNotNull().onEach { request ->
            Log.i(TAG, "Collected durable pairing request from ${request.peerName} (${request.peerId})")
            handlePairingRequest(request)
        }.launchIn(viewModelScope)

        transport.pairingConfirmations.onEach { confirmation ->
            if (pairingCodes[confirmation.peerId] == confirmation.authCode) {
                remotelyConfirmedPairings.add(confirmation.peerId)
                completePairingIfReady(confirmation.peerId)
            }
        }.launchIn(viewModelScope)

        // Install the pairing observer before radio work begins.  A nearby
        // phone can complete GATT service discovery in milliseconds; starting
        // discovery above this collector lost that initial verification event
        // because connectionRequests intentionally has replay=0.
        startNearby()

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
                val unauthed = _peers.value.filter { it.isConnected && isPairingApproved(it.id) && !isSessionAuthenticated(it.id) }
                unauthed.forEach { peer ->
                    sendAuthFrame(peer.id)
                }
            }
        }

        transport.peers.onEach { rawPeerList ->
            val previouslyAuthenticated = _peers.value.count { it.isConnected && it.isAuthenticated }
            val previouslyConnectedIds = _peers.value.filter { it.isConnected }.map { it.id }.toSet()

            // Android BLE privacy can briefly expose one physical handset under
            // both its retiring and its fresh random address.  Never fan voice
            // or control frames out to both: choose the authenticated, connected
            // address for a named handset. Generic scan names remain separate
            // because they cannot be safely identified as the same person.
            val canonicalRawPeers = rawPeerList
                .groupBy { peer ->
                    if (peer.name.startsWith("TalkLoop") || peer.name.startsWith("Peer ") || peer.name.startsWith("Walkie-Talkie")) {
                        peer.id
                    } else {
                        peer.name.lowercase()
                    }
                }
                .values
                .mapNotNull { candidates ->
                    candidates.maxWithOrNull(
                        compareBy<Peer> { isSessionAuthenticated(it.id) }
                            .thenBy { it.isConnected }
                    )
                }

            // Decorate peers with authenticated state from CryptoManager
            val peerList = canonicalRawPeers.map { p ->
                p.copy(isAuthenticated = p.isConnected && isSessionAuthenticated(p.id))
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
            (previouslyConnectedIds - connectedIds).forEach { goneId ->
                clearPeerPairingState(goneId)
            }

            connected.forEach { peer ->
                if (isPairingApproved(peer.id) && !isSessionAuthenticated(peer.id)) {
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
            // Candidate ECDH keys on a trusted reconnect cannot carry application traffic.
            if (decodedFrame.type != FrameType.AUTH && decodedFrame.type != FrameType.AUTH_ACK &&
                decodedFrame.type != FrameType.GROUP_CONTROL && !isSessionAuthenticated(peerId)) return@onEach
            var shouldForwardControlFrame = false
            when (decodedFrame.type) {
                FrameType.AUTH -> {
                    // The explicit CONFIRM control packet is sent without a
                    // transport ACK and can be lost while BLE privacy addresses
                    // rotate. AUTH is only emitted after the sender confirms.
                    // Once this phone's user has also confirmed the displayed
                    // SAS, a received AUTH is safe to treat as the missing
                    // remote confirmation instead of deadlocking forever.
                    if (peerId in pairingCodes &&
                        peerId in locallyConfirmedPairings &&
                        peerId !in remotelyConfirmedPairings
                    ) {
                        Log.i(TAG, "AUTH from $peerId proves remote SAS confirmation")
                        remotelyConfirmedPairings.add(peerId)
                        if (_pendingRequest.value?.peerId == peerId) _pendingRequest.value = null
                    }
                    if (!isPairingApproved(peerId)) {
                        Log.w(TAG, "Ignoring AUTH from $peerId until both pairing confirmations arrive")
                        return@onEach
                    }
                    val receivedIdentityKey = cryptoManager.fingerprintPublicKey(decodedFrame.payload)
                    val advertisedIdentityKey = pairingIdentityKeys[peerId]
                    if (advertisedIdentityKey != null && advertisedIdentityKey != receivedIdentityKey) {
                        Log.e(TAG, "Identity key changed between pairing and AUTH for $peerId")
                        _transportError.value = "Security warning: the peer identity changed during pairing."
                        clearPeerPairingState(peerId)
                        transport.rejectConnection(peerId)
                        return@onEach
                    }
                    pairingIdentityKeys[peerId] = receivedIdentityKey
                    val isNew = !cryptoManager.hasSessionKey(peerId)
                    if (!cryptoManager.deriveSessionKey(peerId, decodedFrame.payload)) {
                        Log.w(TAG, "Ignoring invalid AUTH identity key from $peerId")
                        _transportError.value = "The peer sent an invalid identity key."
                        return@onEach
                    }
                    val now = System.currentTimeMillis()
                    val lastReply = lastAuthReplyAtMs[peerId] ?: 0L
                    if (isNew || now - lastReply >= 2_000L) {
                        lastAuthReplyAtMs[peerId] = now
                        sendAuthFrame(peerId)
                    }
                    val proof = pairingAttempts[peerId]?.let { attempt ->
                        reconnectProofs.getOrPut(peerId) {
                            TrustedReconnectProof(cryptoManager, peerId, receivedIdentityKey, attempt.request.authCode)
                        }
                    }
                    if (peerId in autoConfirmingPairings && proof != null && !proof.verified) {
                        proof.createChallenge()?.let { sendReconnectProof(peerId, it) }
                        return@onEach
                    }
                    finishPeerAuthentication(peerId)
                }
                FrameType.AUTH_ACK -> {
                    if (!isPairingApproved(peerId) || !cryptoManager.hasSessionKey(peerId)) return@onEach
                    val proof = reconnectProofs[peerId] ?: return@onEach
                    when (decodedFrame.payload.firstOrNull()) {
                        TrustedReconnectProof.REQUEST -> proof.replyToChallenge(decodedFrame.payload)?.let {
                            sendReconnectProof(peerId, it)
                        }
                        TrustedReconnectProof.RESPONSE -> if (!proof.verified && proof.verifyResponse(decodedFrame.payload)) {
                            reconnectProofDeadlines.remove(peerId)?.cancel()
                            finishPeerAuthentication(peerId)
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
                FrameType.CODEC_PROPOSE -> {
                    val payload = decryptDiagnosticPayload(peerId, decodedFrame)
                    if (payload?.size == 9 && payload[8].toInt() in 0..1 &&
                        (payload[8].toInt() == 0 || com.example.offlinewalkietalkie.voice.Codec2Native.isLoaded)) {
                        sendEncryptedDiagnosticFrame(peerId, FrameType.CODEC_ACCEPT, payload)
                    }
                }
                FrameType.CODEC_ACCEPT -> {
                    val payload = decryptDiagnosticPayload(peerId, decodedFrame)
                    if (payload != null) codecProposal?.acknowledge(peerId, payload)
                }
                FrameType.MESH -> {
                    configureMeshLinks()
                    when (val result = meshRouter.processPacket(peerId, decodedFrame.payload)) {
                        is com.example.offlinewalkietalkie.mesh.MeshOutcome.DeliveredLocally -> deliverMeshVoice(result.innerPacket)
                        is com.example.offlinewalkietalkie.mesh.MeshOutcome.DeliveredAndForwarded -> {
                            deliverMeshVoice(result.innerPacket)
                            sendMeshPackets(result.rewrappedPackets)
                        }
                        is com.example.offlinewalkietalkie.mesh.MeshOutcome.Forwarded -> sendMeshPackets(result.rewrappedPackets)
                        else -> Unit
                    }
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
                            Log.w(TAG, "VOICE decrypt FAILED from $peerId seq=${decodedFrame.sequence} payloadSize=${decodedFrame.payload.size} authTagSize=${decodedFrame.authTag.size}")
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
                            rogerBeepGenerator.playOutroTone(_rogerBeepStyle.value)

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
                    val msgKey = "${decodedFrame.sessionId}:${decodedFrame.sequence}"
                    val plaintext = decryptDiagnosticPayload(peerId, decodedFrame) ?: return@onEach

                    val isDuplicate = synchronized(seenTextMessageKeys) {
                        if (seenTextMessageKeys.contains(msgKey) || _textMessages.value.any { it.messageId == msgKey }) {
                            true
                        } else {
                            if (seenTextMessageKeys.size > 1000) {
                                val it = seenTextMessageKeys.iterator()
                                if (it.hasNext()) { it.next(); it.remove() }
                            }
                            seenTextMessageKeys.add(msgKey)
                            false
                        }
                    }

                    // Acknowledge receipt to peer
                    sendEncryptedDiagnosticFrame(peerId, FrameType.TEXT_ACK,
                        ByteBuffer.allocate(12).putLong(decodedFrame.sessionId).putInt(decodedFrame.sequence).array())

                    if (isDuplicate) {
                        Log.i(TAG, "Duplicate text message $msgKey from $peerName received; dropped from UI, ACK sent")
                    } else {
                        val content: String? = String(plaintext, Charsets.UTF_8)

                        if (content != null) {
                            Log.i(TAG, "Received authenticated encrypted TEXT from $peerName seq=${decodedFrame.sequence} (len=${content.length})")
                            val msg = TextMessage(
                                messageId = msgKey,
                                sequence = decodedFrame.sequence,
                                senderMemberId = decodedFrame.sessionId,
                                senderName = peerName,
                                content = content,
                                status = TextDeliveryStatus.DELIVERED
                            )
                            _textMessages.value = _textMessages.value + msg
                        } else {
                            Log.w(TAG, "Dropping unauthenticated or unencrypted TEXT from $peerId seq=${decodedFrame.sequence}")
                        }
                    }
                }
                FrameType.ACK -> {
                    Log.w(TAG, "Ignoring legacy unauthenticated delivery receipt")
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
                    if (decodedFrame.isEncrypted()) {
                        decryptDiagnosticPayload(peerId, decodedFrame)?.let { handleGroupControl(peerId, it) }
                    } else if (GroupMessage.decode(decodedFrame.payload) is GroupMessage.MembershipProof) {
                        handleGroupControl(peerId, decodedFrame.payload)
                    }
                }
                FrameType.TEXT_ACK -> {
                    val data = decryptDiagnosticPayload(peerId, decodedFrame)
                    if (data?.size == 12) {
                        val buffer = ByteBuffer.wrap(data)
                        reliableMessageManager.onAckReceived(buffer.long, buffer.int, peerId)
                    }
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
                FrameType.SOS_CANCEL -> {
                    val senderId = String(decodedFrame.payload, Charsets.UTF_8)
                    if (senderId.isNotBlank()) {
                        sosManager.handleIncomingSosCancel(senderId)
                    }
                }
                FrameType.LOCATION_RADAR -> {
                    val loc = PeerLocation.decode(decodedFrame.payload)
                    if (loc != null && isSanePeerLocation(loc)) {
                        val map = _peerLocations.value.toMutableMap()
                        val current = map[loc.peerId]
                        // Delayed radio packets must never move a peer backwards on the map.
                        if (current == null || loc.timestamp >= current.timestamp) {
                            map[loc.peerId] = loc
                            _peerLocations.value = map
                        }
                    }
                    shouldForwardControlFrame = true
                }
                FrameType.TELEMETRY -> {
                    val tel = PeerTelemetry.decode(decodedFrame.payload)
                    if (tel != null) {
                        telemetryManager.handleIncomingTelemetry(tel)
                    }
                    shouldForwardControlFrame = true
                }
                FrameType.PING -> {
                    val probe = decryptDiagnosticPayload(peerId, decodedFrame)
                    if (probe?.size == Long.SIZE_BYTES) {
                        sendEncryptedDiagnosticFrame(peerId, FrameType.PONG, probe)
                    } else {
                        Log.w(TAG, "Dropped unauthenticated or malformed PING from $peerId")
                    }
                }
                FrameType.PONG -> {
                    val probe = decryptDiagnosticPayload(peerId, decodedFrame)
                    if (probe?.size == Long.SIZE_BYTES) {
                        linkTestController.onPongReceived(ByteBuffer.wrap(probe).long)
                    } else {
                        Log.w(TAG, "Dropped unauthenticated or malformed PONG from $peerId")
                    }
                }
                else -> {}
            }
            if (shouldForwardControlFrame &&
                _isRelayModeActive.value &&
                !decodedFrame.isEncrypted() &&
                decodedFrame.hopLimit > 0
            ) {
                val fwdBytes = decodedFrame.copy(hopLimit = (decodedFrame.hopLimit - 1).toByte()).toBytes()
                _peers.value.filter {
                    it.isConnected && it.id != peerId && isVoiceTrusted(it.id)
                }.forEach {
                    transport.send(it.id, fwdBytes)
                }
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Gates voice on authenticated session keys and group verification, not raw transport connectivity. (P0-03 Fix)
     */
    fun isVoiceTrusted(peerId: String): Boolean {
        if (!isSessionAuthenticated(peerId)) return false
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
            if (groupState.value.isInGroup) {
                configureMeshLinks()
                val key = meshRouter.channelVoiceKey ?: return@launch
                val packet = com.example.offlinewalkietalkie.protocol.InnerPacket(
                    type = FrameType.VOICE,
                    flags = if (voiceEngine.isSurvivalCodecActive()) Frame.FLAG_CODEC_CODEC2 else Frame.FLAG_CODEC_AMR_NB,
                    channelId = groupState.value.group!!.groupId.hashCode().toLong(),
                    memberId = sessionId,
                    sequence = sequence,
                    timestamp = System.currentTimeMillis().toInt(),
                    payload = encodedFrame
                )
                sendMeshPackets(meshRouter.originatePacket(packet, key))
                return@launch
            }
            val activePeers = _peers.value.filter { it.isConnected && isSessionAuthenticated(it.id) }
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

    private fun configureMeshLinks() {
        val verified = _peers.value.filter { it.isConnected && isVoiceTrusted(it.id) }
        val ids = verified.map { it.id }.toSet()
        meshRouter.linkKeys.keys.toList().filterNot { it in ids }.forEach(meshRouter::unregisterNeighborLink)
        verified.forEach { peer ->
            cryptoManager.meshHopKey(peer.id)?.let {
                meshRouter.registerNeighborLink(peer.id, peer.id.hashCode().toLong(), it)
            }
        }
        meshRouter.channelVoiceKey = groupManager.channelEncryptionKey()
        meshRouter.isRelayEnabled = _isRelayModeActive.value
    }

    private suspend fun sendMeshPackets(packets: Map<String, ByteArray>) {
        packets.forEach { (endpoint, payload) ->
            transport.send(endpoint, Frame(
                type = FrameType.MESH, sessionId = sessionId,
                sequence = controlSequence.getAndIncrement(), hopLimit = 0, payload = payload
            ).toBytes())
        }
    }

    private fun deliverMeshVoice(packet: com.example.offlinewalkietalkie.protocol.InnerPacket) {
        val group = groupState.value.group ?: return
        if (packet.type != FrameType.VOICE || packet.channelId != group.groupId.hashCode().toLong()) return
        voiceEngine.handleIncomingFrame(
            peerId = "mesh:${packet.memberId}", sequence = packet.sequence,
            encodedAudio = packet.payload,
            codecType = com.example.offlinewalkietalkie.protocol.AudioCodecType.fromFlags(packet.flags),
            senderTimestampMs = packet.timestamp.toLong() and 0xFFFFFFFFL
        )
        if (replayBuffer.isEnabled) replayBuffer.onFrameReceived(packet.payload)
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
                if (!isSessionAuthenticated(targetPeerId)) return@launch
                routingManager.sendTo(targetPeerId, frame)
            } else {
                routingManager.markAsSeen(frame)
                _peers.value.filter { it.isConnected && isSessionAuthenticated(it.id) }.forEach { peer ->
                    transport.send(peer.id, frame.toBytes())
                }
            }
        }
    }

    private fun sendAuthFrame(peerId: String) {
        viewModelScope.launch {
            if (!isPairingApproved(peerId)) return@launch
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
            groupManager.setRelayOnlyJoining(relayOnlyJoining.value)
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
        requestPrefs.edit().remove("resume_requested").remove("resume_name").apply()
        meshRouter.reset()
        viewModelScope.launch {
            groupManager.leaveGroup()
            setInSession(false)
            pairingAttempts.keys.toList().forEach(::clearPeerPairingState)
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

    private suspend fun sendEncryptedDiagnosticFrame(
        peerId: String,
        type: FrameType,
        payload: ByteArray
    ) {
        if (!cryptoManager.hasSessionKey(peerId)) return
        val sequence = controlSequence.getAndIncrement()
        val frame = Frame(
            type = type,
            flags = Frame.FLAG_ENCRYPTED,
            hopLimit = 0,
            sessionId = sessionId,
            sequence = sequence,
            timestamp = (System.currentTimeMillis() and 0xFFFFFFFFL).toInt(),
            payload = ByteArray(payload.size)
        )
        val nonce = generateNonce(frame.sessionId, frame.sequence)
        val headerAad = frame.toBytes().copyOfRange(0, Frame.HEADER_SIZE)
        val encrypted = cryptoManager.encrypt(peerId, payload, nonce, headerAad) ?: return
        transport.send(
            peerId,
            frame.copy(payload = encrypted.first, authTag = encrypted.second).toBytes()
        )
    }

    private fun decryptDiagnosticPayload(peerId: String, frame: Frame): ByteArray? {
        if (!frame.isEncrypted() || frame.authTag == null || !cryptoManager.hasSessionKey(peerId)) return null
        val nonce = generateNonce(frame.sessionId, frame.sequence)
        val headerAad = frame.copy(hopLimit = 0).toBytes().copyOfRange(0, Frame.HEADER_SIZE)
        return cryptoManager.decrypt(peerId, frame.payload, frame.authTag, nonce, headerAad)
    }

    fun runLinkTest() {
        if (linkTestJob?.isActive == true) return
        val peer = _peers.value.firstOrNull { it.isConnected && it.isAuthenticated && cryptoManager.hasSessionKey(it.id) }
        if (peer == null) {
            _linkTestStatus.value = "No verified peer is connected."
            return
        }

        linkTestController.reset()
        linkTestController.updateTransportInfo(TransportSelector.getDisplayName(_transportType.value), meshHops = 1)
        voiceEngine.getJitterBuffer(peer.id)?.metrics?.value?.jitterMs?.let(linkTestController::updateJitter)
        _diagnosticsReport.value = null
        linkTestJob = viewModelScope.launch {
            _isLinkTestRunning.value = true
            _linkTestStatus.value = "Testing ${peer.name} over the offline link…"
            try {
                repeat(8) {
                    linkTestController.performLinkTest(peer.id)
                    delay(400)
                }
                delay(1_000)
                val result = linkTestController.telemetry.value
                if (adaptiveAudioEnabled.value && result.packetsReceived >= 4 && !_isRecording.value) {
                    setSurvivalCodecMode(result.packetLossPercent >= 12.5f || result.rttMs >= 500)
                }
                _linkTestStatus.value = if (result.packetsReceived > 0) {
                    "Complete • ${result.packetsReceived}/${result.packetsSent} replies"
                } else {
                    "No replies received. Check the peer connection."
                }
                Log.i(
                    TAG,
                    "Offline link test complete: replies=${result.packetsReceived}/${result.packetsSent} " +
                        "rtt=${result.rttMs}ms loss=${"%.1f".format(result.packetLossPercent)}% " +
                        "jitter=${"%.1f".format(result.jitterMs)}ms quality=${result.qualityBars}/5"
                )
            } finally {
                _isLinkTestRunning.value = false
            }
        }
    }

    fun generateRedactedDiagnostics(): String? {
        val telemetry = linkTestController.telemetry.value
        val report = RedactedDiagnosticsExporter.generateReport(
            context = getApplication(),
            activeTransportName = telemetry.transportName,
            totalPacketsSent = telemetry.packetsSent.toLong(),
            totalPacketsReceived = telemetry.packetsReceived.toLong(),
            totalPacketsLost = telemetry.packetsLost.toLong(),
            averageJitterMs = telemetry.jitterMs,
            measuredRttMs = telemetry.rttMs
        )
        if (!RedactedDiagnosticsExporter.isSafeAndRedacted(report)) {
            _transportError.value = "Diagnostics privacy validation failed. Nothing was exported."
            _diagnosticsReport.value = null
            return null
        }
        _diagnosticsReport.value = report
        return report
    }

    fun clearDiagnosticsReport() {
        _diagnosticsReport.value = null
    }

    fun setTransportType(type: TransportType) {
        if (!TransportSelector.isTransportAvailable(getApplication<Application>(), type)) {
            val name = TransportSelector.getDisplayName(type)
            _transportError.value = TransportSelector.unavailableReason(getApplication<Application>(), type)
                ?: "$name is not supported on this device"
            Log.w(TAG, "Cannot switch to unsupported transport: $type")
            return
        }
        viewModelScope.launch {
            transport.stop()
            setInSession(false)
            meshRouter.reset()
            codecNegotiationJob?.cancel()
            if (transport.setTransportType(type)) {
                _transportType.value = type
                startNearby()
                if (type == TransportType.BLUETOOTH_CLASSIC) {
                    runCatching {
                        getApplication<Application>().startActivity(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { _transportError.value = "Open Bluetooth settings to make this phone discoverable." }
                }
            }
        }
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
            clearPeerPairingState(peerId)
            transport.disconnect(peerId)
        }
    }

    fun acceptConnection(peerId: String) {
        viewModelScope.launch {
            val attempt = pairingAttempts[peerId] ?: return@launch
            if (attempt.decision != DirectPeerTrust.Decision.REQUIRE_SAS) return@launch
            val code = attempt.request.authCode
            transport.acceptConnection(peerId)
            if (pairingAttempts[peerId] !== attempt) return@launch
            locallyConfirmedPairings.add(peerId)
            transport.confirmPairing(peerId, code)
            if (pairingAttempts[peerId] !== attempt) return@launch
            completePairingIfReady(peerId)
        }
    }

    fun rejectConnection(peerId: String) {
        viewModelScope.launch {
            clearPeerPairingState(peerId)
            transport.rejectConnection(peerId)
        }
    }

    fun clearPendingRequest() {
        _pendingRequest.value = null
    }

    /**
     * A direct link never authorizes a key exchange merely because GATT is up.
     * The transport publishes a pairing code for every direct peer; both local
     * and remote confirmations are required before AUTH is allowed through.
     * A peer carrying a valid group advert follows the separate group-manager
     * admission path instead of this direct SAS path.
     */
    private fun isPairingApproved(peerId: String): Boolean {
        // A group advert is only a discovery hint.  It becomes trusted after the
        // group secret / membership proof succeeds on this exact radio endpoint.
        val isVerifiedGroupPeer = groupState.value.isTrustedEndpoint(peerId) ||
            groupState.value.others.any { it.endpointId == peerId && it.humanVerified }
        return isVerifiedGroupPeer ||
            (peerId in pairingCodes &&
                peerId in locallyConfirmedPairings &&
                peerId in remotelyConfirmedPairings)
    }

    private fun isSanePeerLocation(location: PeerLocation): Boolean {
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return false
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return false
        // Reject timestamps grossly ahead of the receiving device while accepting
        // independently clocked offline phones with a five-minute allowance.
        return location.timestamp <= System.currentTimeMillis() + 5 * 60_000L
    }

    private fun completePairingIfReady(peerId: String) {
        if (!isPairingApproved(peerId)) return
        if (_pendingRequest.value?.peerId == peerId) _pendingRequest.value = null
        Log.i(TAG, "Pairing confirmed by both phones for $peerId; starting authenticated session")
        sendAuthFrame(peerId)
    }

    private suspend fun handlePairingRequest(request: ConnectionRequest) {
        val previous = pairingAttempts[request.peerId]
        // A duplicate notification is idempotent. A delayed identity receipt may
        // replace an unapproved legacy fallback, but never inherit confirmation.
        // An identity replacement on an approved SAS fails closed.
        val identityChanged = previous != null && previous.request.identityKeyHex != request.identityKeyHex
        val unapprovedIdentityReceipt = previous?.request?.identityKeyHex == null &&
            request.peerId !in locallyConfirmedPairings && !cryptoManager.hasSessionKey(request.peerId)
        if (previous != null && previous.request.authCode == request.authCode &&
            identityChanged && !unapprovedIdentityReceipt) {
            _transportError.value = "Security warning: the peer identity changed during pairing."
            clearPeerPairingState(request.peerId)
            transport.rejectConnection(request.peerId)
            return
        }
        val attempt = if (previous?.request == request) previous else {
            clearPeerPairingState(request.peerId)
            PairingAttempt(request).also { pairingAttempts[request.peerId] = it }
        }
        pairingCodes[request.peerId] = request.authCode
        request.identityKeyHex?.let { pairingIdentityKeys[request.peerId] = it }
        if (!_allowIncomingRequests.value) {
            clearPeerPairingState(request.peerId)
            transport.rejectConnection(request.peerId)
            return
        }

        // One snapshot avoids a key/name lookup race. A suspended lookup must
        // not approve a request replaced or disconnected in the meantime.
        val contacts = trustedContactsRepository.contacts.first()
        if (pairingAttempts[request.peerId] !== attempt) return
        val decision = attempt.decision ?: DirectPeerTrust.evaluate(request, contacts).also { attempt.decision = it }
        when (decision) {
            DirectPeerTrust.Decision.TRUSTED -> {
                if (autoConfirmingPairings.add(request.peerId)) {
                    reconnectProofs[request.peerId] = TrustedReconnectProof(
                        cryptoManager, request.peerId, requireNotNull(request.identityKeyHex), request.authCode)
                    reconnectProofDeadlines[request.peerId] = viewModelScope.launch {
                        delay(20_000)
                        if (pairingAttempts[request.peerId] === attempt &&
                            reconnectProofs[request.peerId]?.verified == false) {
                            _transportError.value = "Trusted reconnect authentication timed out. Connect again."
                            // Remove this job before cleanup so it does not cancel its own rejection.
                            reconnectProofDeadlines.remove(request.peerId)
                            clearPeerPairingState(request.peerId)
                            transport.rejectConnection(request.peerId)
                        }
                    }
                    if (_pendingRequest.value?.peerId == request.peerId) _pendingRequest.value = null
                    transport.acceptConnection(request.peerId)
                    if (pairingAttempts[request.peerId] !== attempt) return
                    locallyConfirmedPairings.add(request.peerId)
                    transport.confirmPairing(request.peerId, request.authCode)
                    if (pairingAttempts[request.peerId] !== attempt) return
                    completePairingIfReady(request.peerId)
                }
            }
            DirectPeerTrust.Decision.IDENTITY_CHANGED -> {
                _transportError.value = "Security warning: ${request.peerName} presented a different identity key."
                clearPeerPairingState(request.peerId)
                transport.rejectConnection(request.peerId)
            }
            DirectPeerTrust.Decision.REQUIRE_SAS -> {
                if (!isPairingApproved(request.peerId)) _pendingRequest.value = request
            }
        }
    }

    private fun isSessionAuthenticated(peerId: String): Boolean =
        isPairingApproved(peerId) && cryptoManager.hasSessionKey(peerId) &&
            (peerId !in autoConfirmingPairings || reconnectProofs[peerId]?.verified == true)

    private suspend fun sendReconnectProof(peerId: String, payload: ByteArray) {
        routingManager.sendTo(peerId, Frame(type = FrameType.AUTH_ACK, payload = payload,
            sessionId = sessionId, sequence = controlSequence.getAndIncrement(),
            timestamp = System.currentTimeMillis().toInt(), hopLimit = 1))
    }

    private suspend fun finishPeerAuthentication(peerId: String) {
        if (!isSessionAuthenticated(peerId)) return
        securedPeerIds.add(peerId)
        val attempt = pairingAttempts[peerId]
        val identity = pairingIdentityKeys[peerId]
        // Only direct SAS-approved or proven returning identities enter contacts.
        // Group membership is a separate authorization path, not SAS verification.
        if (attempt != null && identity != null) {
            trustedContactsRepository.trustIdentity(attempt.request.peerName, identity, pairingCodes[peerId].orEmpty())
        }
        if (attempt != null && pairingAttempts[peerId] !== attempt) return
        _peers.value = _peers.value.map { it.copy(isAuthenticated = it.isConnected && isSessionAuthenticated(it.id)) }
        _securedPeerCount.value = _peers.value.count { it.isAuthenticated }
        if (!_isInSession.value) {
            _peers.value.firstOrNull { it.id == peerId && it.isAuthenticated }?.let {
                _currentChannelName.value = it.name
                setInSession(true)
            }
        }
    }

    private fun clearPeerPairingState(peerId: String) {
        pairingAttempts.remove(peerId)
        reconnectProofs.remove(peerId)
        reconnectProofDeadlines.remove(peerId)?.cancel()
        pairingCodes.remove(peerId)
        pairingIdentityKeys.remove(peerId)
        locallyConfirmedPairings.remove(peerId)
        remotelyConfirmedPairings.remove(peerId)
        autoConfirmingPairings.remove(peerId)
        lastAuthReplyAtMs.remove(peerId)
        cryptoManager.removeSession(peerId)
        securedPeerIds.remove(peerId)
        _peers.value = _peers.value.map { if (it.id == peerId) it.copy(isAuthenticated = false) else it }
        _securedPeerCount.value = _peers.value.count { it.isConnected && it.isAuthenticated }
        if (_securedPeerCount.value == 0 && _isInSession.value) setInSession(false)
        if (_pendingRequest.value?.peerId == peerId) _pendingRequest.value = null
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
        com.example.offlinewalkietalkie.data.MapDownloadWorker.enqueue(getApplication(), name, latitude, longitude, radiusKm)
    }

    fun resumeOfflineMapDownload() {
        com.example.offlinewalkietalkie.data.MapDownloadWorker.resume(getApplication())
    }

    fun cancelOfflineMapDownload() {
        com.example.offlinewalkietalkie.data.MapDownloadWorker.pause(getApplication())
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

    // --- MBTiles Operations ---
    fun refreshMbTilesList() {
        _mbTilesPackages.value = mbTilesRepository.listPackages()
        mbTilesStorageUsed.value = mbTilesRepository.getStorageUsedBytes()
    }

    fun restoreBundledMbTiles() {
        _mbTilesPackages.value = mbTilesRepository.restoreBundledPacks()
        mbTilesStorageUsed.value = mbTilesRepository.getStorageUsedBytes()
    }

    fun importMbTiles(uri: Uri, displayName: String? = null): Boolean {
        return try {
            val pack = mbTilesRepository.importFromUri(uri, displayName)
            _mbTilesPackages.value = mbTilesRepository.listPackages()
            mbTilesStorageUsed.value = mbTilesRepository.getStorageUsedBytes()
            selectMbTiles(pack)
            true
        } catch (e: Exception) {
            _offlineMapError.value = "MBTiles import error: ${e.message}"
            false
        }
    }

    fun deleteMbTiles(pack: MbTilesPackage) {
        mbTilesRepository.deletePackage(pack)
        _mbTilesPackages.value = mbTilesRepository.listPackages()
        mbTilesStorageUsed.value = mbTilesRepository.getStorageUsedBytes()
        if (_selectedMbTilesPackage.value?.id == pack.id) {
            _selectedMbTilesPackage.value = null
            _selectedTilePreview.value = null
        }
    }

    fun clearAllMbTiles() {
        mbTilesRepository.deleteAllPackages()
        _mbTilesPackages.value = emptyList()
        _selectedMbTilesPackage.value = null
        _selectedTilePreview.value = null
        mbTilesStorageUsed.value = mbTilesRepository.getStorageUsedBytes()
    }

    fun selectMbTiles(pack: MbTilesPackage?) {
        _selectedMbTilesPackage.value = pack
        if (pack != null) {
            viewModelScope.launch(Dispatchers.IO) {
                _selectedTilePreview.value = mbTilesRepository.getSampleTile(pack)
            }
        } else {
            _selectedTilePreview.value = null
        }
    }

    fun markTextMessagesRead() {
        _textMessages.value = _textMessages.value.map { msg ->
            if (!msg.isOutgoing) msg.copy(isRead = true) else msg
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
        if (!old.isOutgoing || old.status != TextDeliveryStatus.FAILED) return
        sendTextMessageInternal(old.content, messageId)
    }

    private val _isLoopbackMode = MutableStateFlow(false)
    val isLoopbackMode = _isLoopbackMode.asStateFlow()

    fun setLoopbackMode(enabled: Boolean) {
        _isLoopbackMode.value = enabled
        voiceEngine.isLoopback = enabled
    }

    fun toggleOutdoorMode() {
        _isOutdoorMode.value = !_isOutdoorMode.value
    }

    fun toggleSurvivalCodecMode() {
        setSurvivalCodecMode(!_isSurvivalCodecMode.value)
    }

    fun setSurvivalCodecMode(enabled: Boolean) {
        if (enabled && !com.example.offlinewalkietalkie.voice.Codec2Native.isLoaded) {
            _transportError.value = "Low-bitrate audio is unavailable on this device."
            return
        }
        if (_isRecording.value) {
            _transportError.value = "Release Talk before changing audio mode."
            return
        }
        codecNegotiationJob?.cancel()
        codecNegotiationJob = viewModelScope.launch {
            val endpoints = _peers.value.filter { it.isConnected && isVoiceTrusted(it.id) }.map { it.id }.toSet()
            val proposal = com.example.offlinewalkietalkie.voice.CodecProposal(endpoints, enabled)
            codecProposal = proposal
            val payload = proposal.payload
            if (endpoints.isNotEmpty()) {
                repeat(3) {
                    proposal.pending().forEach { sendEncryptedDiagnosticFrame(it, FrameType.CODEC_PROPOSE, payload) }
                    delay(500)
                }
                if (!proposal.canCommit(_peers.value.filter { it.isConnected && isVoiceTrusted(it.id) }.map { it.id }.toSet())) {
                    _transportError.value = "Audio mode unchanged: not every verified peer acknowledged the change."
                    return@launch
                }
            }
            if (!_isRecording.value) {
                _isSurvivalCodecMode.value = enabled
                voiceEngine.setSurvivalCodecMode(enabled)
            }
        }
    }

    fun toggleRelayMode() {
        _isRelayModeActive.value = !_isRelayModeActive.value
    }

    fun setAudioRoute(route: AudioRoute) {
        voiceEngine.setAudioRoute(route)
        _speakerphoneEnabled.value = (route == AudioRoute.SPEAKER)
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
        if (inSession) requestPrefs.edit().putBoolean("resume_requested", true)
            .putString("resume_name", _currentChannelName.value).apply()
        if (!inSession) {
            _isMuted.value = false
            voiceEngine.isRealtimeMuted = false
        }
        if (inSession) {
            if (foregroundServiceStartGate.onSessionChanged(active = true)) {
                startBackgroundService()
            } else {
                Log.i(TAG, "Foreground notification deferred until the app is visible")
            }
            // The handshake is over; a leftover request would send the pairing screen
            // straight back up the moment it is dismissed.
            _pendingRequest.value = null
            voiceEngine.routeManager?.requestAudioFocus()
            voiceEngine.setSpeakerphone(_speakerphoneEnabled.value)
            Log.i(TAG, "Session started: speaker=${_speakerphoneEnabled.value}")
        } else {
            foregroundServiceStartGate.onSessionChanged(active = false)
            stopBackgroundService()
            voiceEngine.routeManager?.clearRouting()
        }
    }

    /** Called by the activity while it has a visible window for Android 12+ FGS policy. */
    fun onActivityVisible() {
        if (foregroundServiceStartGate.onActivityVisible()) {
            startBackgroundService()
        }
    }

    fun onActivityHidden() {
        foregroundServiceStartGate.onActivityHidden()
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
        sendTextMessageInternal(content)
    }

    private fun sendTextMessageInternal(content: String, retryId: String? = null) {
        val seq = controlSequence.getAndIncrement()
        val connectedPeers = _peers.value.filter { it.isConnected && isSessionAuthenticated(it.id) }.distinctBy { it.name }
        Log.i(TAG, "sendTextMessage (len=${content.length}) to ${connectedPeers.size} peer(s)")
        val textMsg = TextMessage(
            messageId = retryId ?: java.util.UUID.randomUUID().toString(),
            isOutgoing = true,
            sequence = seq,
            senderMemberId = sessionId,
            senderName = Build.MODEL,
            content = content,
            status = if (connectedPeers.isNotEmpty()) TextDeliveryStatus.SENDING else TextDeliveryStatus.FAILED
        )
        _textMessages.value = _textMessages.value.filterNot { it.messageId == textMsg.messageId } + textMsg

        val payload = content.toByteArray(Charsets.UTF_8)
        if (connectedPeers.isEmpty()) return
        val awaitingReceipts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
            addAll(connectedPeers.map { it.id })
        }
        val deliveryFailed = java.util.concurrent.atomic.AtomicBoolean(false)

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
                    awaitingReceipts.remove(peer.id)
                    if (awaitingReceipts.isNotEmpty() || deliveryFailed.get()) return@sendReliable
                    _textMessages.value = _textMessages.value.map { current ->
                        if (current.messageId == textMsg.messageId) current.copy(status = TextDeliveryStatus.DELIVERED) else current
                    }
                },
                onFailed = {
                    deliveryFailed.set(true)
                    _textMessages.value = _textMessages.value.map { current ->
                        if (current.messageId == textMsg.messageId && current.status == TextDeliveryStatus.SENDING) current.copy(status = TextDeliveryStatus.FAILED) else current
                    }
                }
            )
        }
    }

    fun requestPtt() {
        if (groupState.value.isInGroup && groupManager.relayOnly) {
            _transportError.value = "Relay-only participants forward packets without transmitting or listening."
            return
        }
        val current = floorController.floorState.value
        if (current !is FloorState.Idle) return
        if (_isRecording.value) return

        rogerBeepGenerator.playIntroTone(_rogerBeepStyle.value)
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
        rogerBeepGenerator.playOutroTone(_rogerBeepStyle.value)
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

    private suspend fun broadcastSosCancelFrame(senderId: String) {
        val payload = senderId.toByteArray(Charsets.UTF_8)
        val frame = Frame(
            type = FrameType.SOS_CANCEL,
            flags = 0,
            hopLimit = 3,
            sessionId = sessionId,
            sequence = controlSequence.incrementAndGet(),
            timestamp = (System.currentTimeMillis() and 0xFFFFFFFFL).toInt(),
            payload = payload
        )
        val bytes = frame.toBytes()
        _peers.value.filter { it.isConnected }.forEach {
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
        val targets = _peers.value.filter { it.isConnected && isVoiceTrusted(it.id) }
        if (targets.isEmpty()) {
            Log.d(TAG, "Location update held: no verified link is available")
            return
        }
        Log.d(TAG, "Broadcasting location to ${targets.size} verified peer(s)")
        targets.forEach {
            // Marking the source frame as seen makes a later mesh echo harmless.
            routingManager.sendTo(it.id, frame)
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

        fun setRogerBeepStyle(style: RogerBeepStyle) {
        _rogerBeepStyle.value = style
        requestPrefs.edit().putString("roger_beep_style", style.id).apply()
        Log.i(TAG, "Roger beep style updated: ${style.displayName}")
    }

    fun previewRogerBeepStyle(style: RogerBeepStyle) {
        rogerBeepGenerator.previewStyle(style)
    }

    fun replayInstant10s() {
        if (instantReplayBuffer.isPlaying.value) {
            instantReplayBuffer.stop()
        } else {
            instantReplayBuffer.playLatest()
        }
    }

    fun stopInstantReplay() {
        instantReplayBuffer.stop()
    }

    fun replayLastMessage() {
        Log.i(TAG, "replayLastMessage triggered! hasInstantAudio=${instantReplayBuffer.hasAudio.value} isPlaying=${instantReplayBuffer.isPlaying.value}")
        if (instantReplayBuffer.isPlaying.value) {
            instantReplayBuffer.stop()
        } else if (instantReplayBuffer.hasAudio.value) {
            instantReplayBuffer.playLatest()
        } else {
            voiceHistoryManager.replayLastMessage()
        }
    }

    fun refreshLocation() {
        locationProvider.refreshLocation()
        locationProvider.startTracking()
    }

    fun stopReplay() {
        voiceHistoryManager.stopReplay()
        instantReplayBuffer.stop()
    }

    fun isBatteryOptimizationIgnored(): Boolean = hardwarePttManager.isBatteryOptimizationIgnored()
    fun getBatteryOptimizationIntent(): Intent = hardwarePttManager.createBatteryOptimizationIntent()
    fun requestBatteryOptimization(context: Context) = hardwarePttManager.requestBatteryOptimization(context)

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
        } catch (e: SecurityException) {
            // A microphone FGS also requires a foreground activity on Android 14+.
            foregroundServiceStartGate.onStartDenied()
            Log.w(TAG, "Foreground service start deferred until permissions are usable: ${e.message}")
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                foregroundServiceStartGate.onStartDenied()
                Log.w(TAG, "Foreground service start deferred by Android policy: ${e.message}")
            } else {
                Log.w(TAG, "Foreground service start failed: ${e.message}")
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
        instantReplayBuffer.clear()
        teardownScope.launch { transport.stop() }
    }

    fun leaveSession() {
        requestPrefs.edit().remove("resume_requested").remove("resume_name").apply()
        meshRouter.reset()
        codecNegotiationJob?.cancel()
        voiceEngine.stopRecording()
        voiceEngine.stopPlayback()
        // Routed through setInSession so the audio mode and volumes are handed back;
        // setting the flag directly leaves the device in MODE_IN_COMMUNICATION.
        setInSession(false)
        pairingAttempts.keys.toList().forEach(::clearPeerPairingState)
        securedPeerIds.forEach { cryptoManager.removeSession(it) }
        securedPeerIds.clear()
        _securedPeerCount.value = 0
        viewModelScope.launch {
            if (groupState.value.isInGroup) groupManager.leaveGroup()
            withContext(Dispatchers.IO) { recoveryStore?.save(null) }
            transport.stop()
        }
    }
}
