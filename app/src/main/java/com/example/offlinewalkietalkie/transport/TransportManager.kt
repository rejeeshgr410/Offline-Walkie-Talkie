package com.example.offlinewalkietalkie.transport

import android.content.Context
import android.util.Log
import com.example.offlinewalkietalkie.transport.bluetooth.BleGattTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Dedicated to Direct Bluetooth LE Long-Range GATT Transport (Vivo-style OEM architecture).
 */
class TransportManager(private val context: Context) {

    companion object {
        private const val TAG = "TL/TransportManager"
    }

    private val bleGattTransport = BleGattTransport(context)

    private val _activeTransportType = MutableStateFlow(TransportType.BLE_GATT)
    val activeTransportType = _activeTransportType.asStateFlow()

    private val activeTransport: Transport
        get() = bleGattTransport

    fun setTransportType(type: TransportType) {
        _activeTransportType.value = TransportType.BLE_GATT
    }

    val state = bleGattTransport.state

    val peers = bleGattTransport.peers

    val connectionRequests = bleGattTransport.connectionRequests

    val lastError = bleGattTransport.lastError

    val isAdvertising = bleGattTransport.isAdvertising

    val isDiscovering = bleGattTransport.isDiscovering

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
        Log.i(TAG, "startSymmetricDiscovery as '${identity.endpointName}' on BLE GATT (Vivo Long-Range)")
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
        return bleGattTransport.incomingFrames()
    }

    suspend fun stop() {
        bleGattTransport.stop()
    }
}
