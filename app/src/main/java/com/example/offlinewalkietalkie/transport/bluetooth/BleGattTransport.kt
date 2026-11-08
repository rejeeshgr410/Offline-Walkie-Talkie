package com.example.offlinewalkietalkie.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Long-Range Direct BLE GATT Transport Engine.
 *
 * Implements Vivo's production architecture:
 * - Dual-Role BLE: Simultaneous BluetoothGattServer (peripheral) and BluetoothGatt (central).
 * - Bluetooth 5.0 LE Coded PHY (S=8) support for up to ~1 km direct outdoor line-of-sight range.
 * - Single-packet voice delivery: writes directly via WRITE_TYPE_NO_RESPONSE for sub-millisecond audio transmission.
 * - Zero Wi-Fi disruption: requires no Wi-Fi Direct or SoftAP negotiation; leaves user's normal Wi-Fi intact.
 */
@SuppressLint("MissingPermission")
class BleGattTransport(private val context: Context) : Transport {

    companion object {
        private const val TAG = "TL/BleGattTransport"

        // Walkie-Talkie Service and Voice/Data Characteristic UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("66840001-8673-8679-8390-6768696E9746")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("66841001-8673-8679-8390-6768696E9746")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active client GATT connections: peerAddress -> BluetoothGatt
    private val clientGatts = ConcurrentHashMap<String, BluetoothGatt>()

    // Remote peer characteristics: peerAddress -> BluetoothGattCharacteristic
    private val peerCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    // Devices connected to our GATT Server: peerAddress -> BluetoothDevice
    private val serverConnectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null

    private var leAdvertiser: BluetoothLeAdvertiser? = null
    private var leScanner: BluetoothLeScanner? = null

    // GATT Server Callback (Peripheral Role)
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val address = device?.address ?: return
            val name = device.name ?: "Peer $address"
            Log.i(TAG, "GattServer connectionStateChange: $address -> newState=$newState (status=$status)")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                serverConnectedDevices[address] = device
                addOrUpdatePeer(address, name, isConnected = true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                serverConnectedDevices.remove(address)
                val isClientConnected = clientGatts.containsKey(address)
                addOrUpdatePeer(address, name, isConnected = isClientConnected)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val address = device?.address ?: return
            if (value != null && value.isNotEmpty()) {
                _incomingFrames.tryEmit(IncomingFrame(address, value))
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    // GATT Client Callback (Central Role)
    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val address = gatt?.device?.address ?: return
            val name = gatt.device?.name ?: "Peer $address"
            Log.i(TAG, "GattClient connectionStateChange: $address -> newState=$newState (status=$status)")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                clientGatts[address] = gatt
                addOrUpdatePeer(address, name, isConnected = true)

                // Request larger MTU (247 bytes) for voice packets
                gatt.requestMtu(247)

                // Discover services
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clientGatts.remove(address)
                peerCharacteristics.remove(address)
                try { gatt.close() } catch (e: Throwable) {}
                val isServerConnected = serverConnectedDevices.containsKey(address)
                addOrUpdatePeer(address, name, isConnected = isServerConnected)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            val address = gatt?.device?.address ?: return
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    peerCharacteristics[address] = characteristic
                    Log.i(TAG, "Discovered Walkie-Talkie GATT Characteristic for $address")

                    // Enable notifications on this characteristic
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }

                    // Request LE Coded PHY (Long Range S=8) if supported
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bluetoothAdapter?.isLeCodedPhySupported == true) {
                        Log.i(TAG, "Requesting LE Coded PHY (S=8) for $address")
                        gatt.setPreferredPhy(
                            BluetoothDevice.PHY_LE_CODED,
                            BluetoothDevice.PHY_LE_CODED,
                            BluetoothDevice.PHY_OPTION_S8
                        )
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val address = gatt?.device?.address ?: return
            val value = characteristic?.value
            if (value != null && value.isNotEmpty()) {
                _incomingFrames.tryEmit(IncomingFrame(address, value))
            }
        }

        override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            Log.i(TAG, "PHY update for ${gatt?.device?.address}: txPhy=$txPhy, rxPhy=$rxPhy (status=$status)")
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i(TAG, "MTU changed for ${gatt?.device?.address}: mtu=$mtu (status=$status)")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.i(TAG, "BLE GATT Advertising started successfully")
            _isAdvertising.value = true
            _state.value = TransportState.ACTIVE
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "BLE GATT Advertising failed with code $errorCode")
            _isAdvertising.value = false
            _state.value = TransportState.ERROR
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address
            val name = device.name ?: result.scanRecord?.deviceName ?: "Walkie-Talkie $address"
            val isConn = clientGatts.containsKey(address) || serverConnectedDevices.containsKey(address)
            addOrUpdatePeer(address, name, isConnected = isConn)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed with code $errorCode")
            _isDiscovering.value = false
        }
    }

    private fun addOrUpdatePeer(address: String, name: String, isConnected: Boolean) {
        val current = _peers.value.toMutableMap()
        current[address] = Peer(id = address, name = name, isConnected = isConnected)
        _peers.value = current
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _state.value = TransportState.ERROR
            return
        }

        startGattServer()

        leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            Log.w(TAG, "BluetoothLeAdvertiser not available")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        try {
            leAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to startAdvertising", e)
        }
    }

    private fun startGattServer() {
        if (gattServer != null) return
        try {
            val server = bluetoothManager?.openGattServer(context, gattServerCallback)
            if (server != null) {
                val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                val characteristic = BluetoothGattCharacteristic(
                    CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
                )
                val descriptor = BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                characteristic.addDescriptor(descriptor)
                service.addCharacteristic(characteristic)
                server.addService(service)

                gattServer = server
                serverCharacteristic = characteristic
                Log.i(TAG, "GATT Server started successfully with Walkie-Talkie Service")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting GATT Server", e)
        }
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        leScanner = bluetoothAdapter.bluetoothLeScanner
        if (leScanner == null) return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            leScanner?.startScan(listOf(filter), settings, scanCallback)
            _isDiscovering.value = true
            Log.i(TAG, "BLE GATT scanning started for Walkie-Talkie Service")
        } catch (e: Throwable) {
            Log.e(TAG, "startScan failed", e)
            _isDiscovering.value = false
        }
    }

    override suspend fun stopAdvertising() {
        try {
            leAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Throwable) {}
        _isAdvertising.value = false
    }

    override suspend fun connect(peerId: String) {
        val device = bluetoothAdapter?.getRemoteDevice(peerId) ?: return
        if (clientGatts.containsKey(peerId)) return

        Log.i(TAG, "Connecting GATT client to $peerId")
        try {
            val gatt = device.connectGatt(context, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
            clientGatts[peerId] = gatt
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to connect GATT to $peerId", e)
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        // BLE GATT accepts incoming connections automatically on the GATT Server
        connect(peerId)
    }

    override suspend fun rejectConnection(peerId: String) {
        disconnect(peerId)
    }

    override suspend fun disconnect(peerId: String) {
        clientGatts.remove(peerId)?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Throwable) {}
        }
        serverConnectedDevices.remove(peerId)
        val current = _peers.value.toMutableMap()
        current[peerId]?.let {
            current[peerId] = it.copy(isConnected = false)
            _peers.value = current
        }
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        // Priority 1: Send via Client GATT writeCharacteristic
        val gatt = clientGatts[peerId]
        val char = peerCharacteristics[peerId]
        if (gatt != null && char != null) {
            char.value = bytes
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            gatt.writeCharacteristic(char)
            return
        }

        // Priority 2: Send via GATT Server notification to connected peer
        val serverDev = serverConnectedDevices[peerId]
        val srvChar = serverCharacteristic
        val srv = gattServer
        if (srv != null && serverDev != null && srvChar != null) {
            srvChar.value = bytes
            srv.notifyCharacteristicChanged(serverDev, srvChar, false)
            return
        }

        // If not connected yet, trigger auto-connect
        connect(peerId)
    }

    override suspend fun stop() {
        stopAdvertising()
        try {
            leScanner?.stopScan(scanCallback)
        } catch (e: Throwable) {}
        _isDiscovering.value = false

        clientGatts.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Throwable) {}
        }
        clientGatts.clear()
        peerCharacteristics.clear()
        serverConnectedDevices.clear()

        try {
            gattServer?.close()
        } catch (e: Throwable) {}
        gattServer = null
        serverCharacteristic = null

        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
    }
}
