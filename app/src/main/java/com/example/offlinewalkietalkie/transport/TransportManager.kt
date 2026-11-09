package com.example.offlinewalkietalkie.transport

import android.content.Context
import android.util.Log
import com.example.offlinewalkietalkie.transport.bluetooth.BleGattTransport
import com.example.offlinewalkietalkie.transport.bluetooth.BleL2capTransport
import com.example.offlinewalkietalkie.transport.nearby.NearbyConnectionsTransport
import com.example.offlinewalkietalkie.transport.wifi.WifiAwareTransport
import com.example.offlinewalkietalkie.transport.wifidirect.WifiDirectTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

enum class TransportType {
    BLE_GATT,
    NEARBY_CONNECTIONS,
    BLE_L2CAP,
    WIFI_AWARE,
    WIFI_DIRECT,
    BLUETOOTH_CLASSIC
}

/**
 * Transport Manager for TalkLoop.
 *
 * Provides a dynamic transport registry (P0-01 Remediation) with seamless switching and fallback:
 * - Direct Bluetooth LE Long-Range GATT Transport (Default off-grid engine)
 * - Google Nearby Connections (High-bandwidth Wi-Fi hotspot fallback)
 * - Direct BLE L2CAP CoC (Raw L2CAP bearer)
 * - Wi-Fi Direct / Wi-Fi Aware (Local high-throughput off-grid mesh)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportManager(private val context: Context) {

    companion object {
        private const val TAG = "TL/TransportManager"
    }

    private val bleGattTransport by lazy { BleGattTransport(context) }
    private val nearbyTransport by lazy { NearbyConnectionsTransport(context) }
    private val bleL2capTransport by lazy { BleL2capTransport(context) }
    private val wifiDirectTransport by lazy { WifiDirectTransport(context) }
    private val wifiAwareTransport by lazy { WifiAwareTransport(context) }

    private val transports: Map<TransportType, Transport> by lazy {
        mapOf(
            TransportType.BLE_GATT to bleGattTransport,
            TransportType.NEARBY_CONNECTIONS to nearbyTransport,
            TransportType.BLE_L2CAP to bleL2capTransport,
            TransportType.WIFI_DIRECT to wifiDirectTransport,
            TransportType.WIFI_AWARE to wifiAwareTransport
        )
    }

    private val _activeTransportType = MutableStateFlow(TransportType.BLE_GATT)
    val activeTransportType = _activeTransportType.asStateFlow()

    val activeTransport: Transport
        get() = transports[_activeTransportType.value] ?: bleGattTransport

    fun setTransportType(type: TransportType): Boolean {
        if (!TransportSelector.isTransportAvailable(context, type)) {
            Log.w(TAG, "Transport $type is not supported on this device. Retaining ${_activeTransportType.value}")
            return false
        }
        if (_activeTransportType.value != type) {
            val oldType = _activeTransportType.value
            _activeTransportType.value = type
            Log.i(TAG, "Transport switched from $oldType to $type")
        }
        return true
    }

    val state: StateFlow<TransportState>
        get() = activeTransport.state

    val peers: Flow<List<Peer>> = _activeTransportType.flatMapLatest { type ->
        (transports[type] ?: bleGattTransport).peers
    }

    val connectionRequests: Flow<ConnectionRequest> = _activeTransportType.flatMapLatest { type ->
        (transports[type] ?: bleGattTransport).connectionRequests
    }

    val lastError: StateFlow<String?>
        get() = activeTransport.lastError

    val isAdvertising: StateFlow<Boolean>
        get() = activeTransport.isAdvertising

    val isDiscovering: StateFlow<Boolean>
        get() = activeTransport.isDiscovering

    suspend fun startAdvertising(identity: LocalIdentity) {
        activeTransport.startAdvertising(identity)
    }

    suspend fun startDiscovery(identity: LocalIdentity) {
        activeTransport.startDiscovery(identity)
    }

    suspend fun stopAdvertising() {
        activeTransport.stopAdvertising()
    }

    suspend fun republishAdvertising(identity: LocalIdentity) {
        activeTransport.stopAdvertising()
        activeTransport.startAdvertising(identity)
    }

    suspend fun startSymmetricDiscovery(identity: LocalIdentity) {
        Log.i(TAG, "startSymmetricDiscovery as '${identity.endpointName}' on ${_activeTransportType.value}")
        activeTransport.startAdvertising(identity)
        activeTransport.startDiscovery(identity)
    }

    suspend fun connect(peerId: String) {
        activeTransport.connect(peerId)
    }

    suspend fun acceptConnection(peerId: String) {
        activeTransport.acceptConnection(peerId)
    }

    suspend fun rejectConnection(peerId: String) {
        activeTransport.rejectConnection(peerId)
    }

    suspend fun disconnect(peerId: String) {
        activeTransport.disconnect(peerId)
    }

    suspend fun send(peerId: String, bytes: ByteArray) {
        activeTransport.send(peerId, bytes)
    }

    fun incomingFrames(): Flow<IncomingFrame> = _activeTransportType.flatMapLatest { type ->
        (transports[type] ?: bleGattTransport).incomingFrames()
    }

    suspend fun stop() {
        transports.values.forEach { transport ->
            try { transport.stop() } catch (e: Throwable) {}
        }
    }
}
