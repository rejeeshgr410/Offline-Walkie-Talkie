package com.example.offlinewalkietalkie.transport.nearby

import android.content.Context
import android.util.Log
import com.example.offlinewalkietalkie.transport.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

class NearbyConnectionsTransport(
    private val context: Context,
    private var activeStrategy: Strategy = Strategy.P2P_CLUSTER
) : Transport {

    companion object {
        private const val TAG = "TL/Nearby"
        // Must be identical on every device or endpoints are invisible to each other.
        private const val SERVICE_ID = "com.example.offlinewalkietalkie.SERVICE_ID"
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val _isHighBandwidth = MutableStateFlow(false)
    val isHighBandwidth: StateFlow<Boolean> = _isHighBandwidth.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    fun setStrategy(strategy: Strategy) {
        activeStrategy = strategy
    }

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    _incomingFrames.tryEmit(IncomingFrame(endpointId, bytes))
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i(TAG, "onConnectionInitiated endpoint=$endpointId name=${info.endpointName} incoming=${info.isIncomingConnection}")
            // The remote endpoint may never have been surfaced by discovery (we were the
            // advertiser). Make sure it exists in the peer map before anything else.
            addOrUpdatePeer(endpointId, info.endpointName, isConnected = false)
            _connectionRequests.tryEmit(
                ConnectionRequest(endpointId, info.endpointName, info.authenticationDigits)
            )
        }

        override fun onBandwidthChanged(endpointId: String, bandwidthInfo: BandwidthInfo) {
            val isHigh = bandwidthInfo.quality == BandwidthInfo.Quality.HIGH
            Log.i(TAG, "onBandwidthChanged endpoint=$endpointId high=$isHigh")
            _isHighBandwidth.value = isHigh
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.i(TAG, "CONNECTED endpoint=$endpointId")
                markConnected(endpointId)
                _lastError.value = null
            } else {
                Log.w(TAG, "Connection failed endpoint=$endpointId status=${result.status.statusCode} ${result.status.statusMessage}")
                _lastError.value = "Connection failed (${result.status.statusCode})"
                removePeer(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "onDisconnected endpoint=$endpointId")
            removePeer(endpointId)
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i(TAG, "onEndpointFound endpoint=$endpointId name=${info.endpointName} service=${info.serviceId}")
            addOrUpdatePeer(endpointId, info.endpointName, isConnected = false)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "onEndpointLost endpoint=$endpointId")
            removePeerIfDisconnected(endpointId)
        }
    }

    /**
     * The name peers see for us. Held so an outgoing requestConnection identifies this
     * device the same way its advertisement does — otherwise the remote cannot tell which
     * group is calling.
     */
    private var localEndpointName: String = android.os.Build.MODEL

    override suspend fun stopAdvertising() {
        try {
            connectionsClient.stopAdvertising()
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising failed: ${e.message}")
        }
        _isAdvertising.value = false
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        localEndpointName = identity.endpointName
        val options = AdvertisingOptions.Builder()
            .setStrategy(activeStrategy)
            .setLowPower(false)
            .build()
        try {
            connectionsClient.startAdvertising(
                identity.endpointName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            ).await()
            Log.i(TAG, "Advertising as '${identity.endpointName}' strategy=$activeStrategy")
            _isAdvertising.value = true
            _state.value = TransportState.ACTIVE
            _lastError.value = null
        } catch (e: Exception) {
            if (statusCodeOf(e) == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                Log.i(TAG, "Already advertising - treating as success")
                _isAdvertising.value = true
                _state.value = TransportState.ACTIVE
                return
            }
            Log.e(TAG, "startAdvertising failed", e)
            _isAdvertising.value = false
            _lastError.value = describe("Advertising", e)
            _state.value = TransportState.ERROR
        }
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        val options = DiscoveryOptions.Builder()
            .setStrategy(activeStrategy)
            .setLowPower(false)
            .build()
        try {
            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            ).await()
            Log.i(TAG, "Discovering strategy=$activeStrategy")
            _isDiscovering.value = true
            _state.value = TransportState.ACTIVE
            _lastError.value = null
        } catch (e: Exception) {
            if (statusCodeOf(e) == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                Log.i(TAG, "Already discovering - treating as success")
                _isDiscovering.value = true
                _state.value = TransportState.ACTIVE
                return
            }
            Log.e(TAG, "startDiscovery failed", e)
            _isDiscovering.value = false
            _lastError.value = describe("Discovery", e)
            _state.value = TransportState.ERROR
        }
    }

    override suspend fun connect(peerId: String) {
        try {
            Log.i(TAG, "requestConnection -> $peerId")
            connectionsClient.requestConnection(
                localEndpointName,
                peerId,
                connectionLifecycleCallback
            ).await()
        } catch (e: Exception) {
            when (statusCodeOf(e)) {
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    Log.i(TAG, "Already connected to $peerId")
                    markConnected(peerId)
                }
                // Both sides requested at once; the other side's request wins and we will
                // still land in onConnectionInitiated. Not a user-visible failure.
                ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL -> {
                    Log.i(TAG, "Connection already in flight for $peerId")
                }
                else -> {
                    Log.e(TAG, "requestConnection failed for $peerId", e)
                    _lastError.value = describe("Connect", e)
                }
            }
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        try {
            Log.i(TAG, "acceptConnection $peerId")
            connectionsClient.acceptConnection(peerId, payloadCallback).await()
        } catch (e: Exception) {
            Log.e(TAG, "acceptConnection failed for $peerId", e)
            _lastError.value = describe("Accept", e)
        }
    }

    override suspend fun rejectConnection(peerId: String) {
        try {
            connectionsClient.rejectConnection(peerId).await()
        } catch (e: Exception) {
            Log.e(TAG, "rejectConnection failed for $peerId", e)
        }
    }

    override suspend fun disconnect(peerId: String) {
        connectionsClient.disconnectFromEndpoint(peerId)
        removePeer(peerId)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        try {
            connectionsClient.sendPayload(peerId, Payload.fromBytes(bytes))
        } catch (e: Exception) {
            Log.w(TAG, "sendPayload failed for $peerId: ${e.message}")
        }
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        Log.i(TAG, "stop()")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _isAdvertising.value = false
        _isDiscovering.value = false
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
    }

    private fun statusCodeOf(e: Exception): Int =
        (e as? ApiException)?.statusCode ?: -1

    private fun describe(action: String, e: Exception): String {
        val code = statusCodeOf(e)
        val reason = when (code) {
            ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ->
                "Bluetooth unavailable - turn Bluetooth on"
            ConnectionsStatusCodes.MISSING_SETTING_LOCATION_MUST_BE_ON ->
                "Turn on Location in system settings"
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION ->
                "Location permission required"
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT ->
                "Nearby devices permission required"
            ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES ->
                "Nearby Wi-Fi devices permission required"
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE,
            ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE ->
                "Wi-Fi permission required"
            ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                "Radio error - toggle Wi-Fi and Bluetooth"
            ConnectionsStatusCodes.STATUS_ALREADY_HAVE_ACTIVE_STRATEGY ->
                "Another Nearby strategy is already active"
            else -> e.message ?: ConnectionsStatusCodes.getStatusCodeString(code)
        }
        return "$action failed: $reason (code $code)"
    }

    private fun addOrUpdatePeer(id: String, name: String, isConnected: Boolean) {
        _peers.update { current ->
            val existing = current[id]
            // Never downgrade an already-connected peer back to disconnected here.
            val connected = existing?.isConnected ?: isConnected
            current + (id to Peer(id, name, connected))
        }
    }

    private fun markConnected(id: String) {
        _peers.update { current ->
            val peer = current[id] ?: Peer(id, id, false)
            current + (id to peer.copy(isConnected = true))
        }
    }

    private fun removePeer(id: String) {
        _peers.update { current -> current - id }
    }

    private fun removePeerIfDisconnected(id: String) {
        _peers.update { current ->
            if (current[id]?.isConnected == true) current else current - id
        }
    }
}
