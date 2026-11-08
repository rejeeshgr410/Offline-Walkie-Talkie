package com.example.offlinewalkietalkie.transport

import android.content.Context
import android.util.Log
import com.example.offlinewalkietalkie.transport.bluetooth.BleGattTransport
import com.example.offlinewalkietalkie.transport.bluetooth.BleL2capTransport
import com.example.offlinewalkietalkie.transport.bluetooth.BluetoothClassicTransport
import com.example.offlinewalkietalkie.transport.nearby.NearbyConnectionsTransport
import com.example.offlinewalkietalkie.transport.wifi.WifiAwareTransport
import com.example.offlinewalkietalkie.transport.wifidirect.WifiDirectTransport
import com.example.offlinewalkietalkie.util.CapabilityManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

enum class TransportType {
    NEARBY_CONNECTIONS,
    BLE_GATT,
    BLE_L2CAP,
    WIFI_AWARE,
    WIFI_DIRECT,
    BLUETOOTH_CLASSIC
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransportManager(private val context: Context) {

    companion object {
        private const val TAG = "TL/TransportManager"
    }

    private val nearbyTransport = NearbyConnectionsTransport(context)
    private val bleGattTransport = BleGattTransport(context)
    private val bleL2capTransport = BleL2capTransport(context)
    private val wifiAwareTransport = WifiAwareTransport(context)
    private val bluetoothTransport = BluetoothClassicTransport(context)
    private val wifiDirectTransport = WifiDirectTransport(context)

    // Use TransportSelector to determine the best available transport
    private val _activeTransportType = MutableStateFlow(
        TransportSelector.selectTransport(context, isChannel = false, isRelayMesh = false)
    )
    val activeTransportType = _activeTransportType

    private val activeTransport: Transport
        get() = transportFor(_activeTransportType.value)

    private fun transportFor(type: TransportType): Transport = when (type) {
        TransportType.NEARBY_CONNECTIONS -> nearbyTransport
        TransportType.BLE_GATT -> bleGattTransport
        TransportType.BLE_L2CAP -> bleL2capTransport
        TransportType.WIFI_AWARE -> wifiAwareTransport
        TransportType.BLUETOOTH_CLASSIC -> bluetoothTransport
        TransportType.WIFI_DIRECT -> wifiDirectTransport
    }

    fun setTransportType(type: TransportType) {
        _activeTransportType.value = type
    }

    val state = _activeTransportType.flatMapLatest { transportFor(it).state }

    val peers = _activeTransportType.flatMapLatest { transportFor(it).peers }

    val connectionRequests = _activeTransportType.flatMapLatest { transportFor(it).connectionRequests }

    val lastError = _activeTransportType.flatMapLatest { transportFor(it).lastError }

    val isAdvertising = _activeTransportType.flatMapLatest { transportFor(it).isAdvertising }

    val isDiscovering = _activeTransportType.flatMapLatest { transportFor(it).isDiscovering }

    suspend fun startAdvertising(identity: LocalIdentity) {
        activeTransport.startAdvertising(identity)
    }

    suspend fun startDiscovery(identity: LocalIdentity) {
        activeTransport.startDiscovery(identity)
    }

    suspend fun stopAdvertising() {
        activeTransport.stopAdvertising()
    }

    /** Re-advertise under a new endpoint name, leaving established links untouched. */
    suspend fun republishAdvertising(identity: LocalIdentity) {
        activeTransport.stopAdvertising()
        activeTransport.startAdvertising(identity)
    }

    /**
     * Discovery is symmetric: every device must be both visible and looking. Running only one
     * half means two phones can sit next to each other and never see anything.
     */
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

    fun incomingFrames(): Flow<IncomingFrame> {
        return _activeTransportType.flatMapLatest { transportFor(it).incomingFrames() }
    }

    suspend fun stop() {
        nearbyTransport.stop()
        bleGattTransport.stop()
        bleL2capTransport.stop()
        wifiAwareTransport.stop()
        bluetoothTransport.stop()
        wifiDirectTransport.stop()
    }
}
