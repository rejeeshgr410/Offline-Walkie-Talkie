package com.example.offlinewalkietalkie.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 5 & Section 19.15 Native BLE L2CAP CoC Transport.
 *
 * Requirements:
 * - Native Bluetooth fallback on API 29+ without Google Play Services.
 * - BLE Advertising & Scanning for discovery.
 * - Dynamic PSM assignment via listenUsingInsecureL2capChannel().
 * - Low-latency credit-based connection-oriented channels (L2CAP CoC).
 */
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
class BleL2capTransport(private val context: Context) : Transport {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
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
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var serverSocket: BluetoothServerSocket? = null
    private var localPsm: Int = 0
    private val activeConnections = ConcurrentHashMap<String, L2capConnectedThread>()

    // Remote peer PSM cache: address -> psm
    private val discoveredPsms = ConcurrentHashMap<String, Int>()

    private var leAdvertiser: BluetoothLeAdvertiser? = null
    private var leScanner: BluetoothLeScanner? = null

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _state.value = TransportState.ACTIVE
        }

        override fun onStartFailure(errorCode: Int) {
            _state.value = TransportState.ERROR
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val address = device.address
                val name = device.name ?: "BLE Peer"
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
                val psm = if (serviceData != null && serviceData.size >= 4) {
                    ByteBuffer.wrap(serviceData).int
                } else {
                    0
                }
                if (psm != 0) {
                    discoveredPsms[address] = psm
                }
                addOrUpdatePeer(address, name, activeConnections.containsKey(address))
            }
        }
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        startServer()

        leAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val psmData = ByteBuffer.allocate(4).putInt(localPsm).array()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), psmData)
            .build()

        leAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        _state.value = TransportState.ACTIVE

        leScanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        leScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    override suspend fun connect(peerId: String) {
        if (bluetoothAdapter == null) return
        val device = bluetoothAdapter.getRemoteDevice(peerId)
        val psm = discoveredPsms[peerId] ?: return

        coroutineScope.launch {
            try {
                // Open L2CAP Connection-Oriented Channel (§19.15)
                val socket = device.createInsecureL2capChannel(psm)
                socket.connect()
                manageConnectedSocket(socket, device)
            } catch (e: Exception) {
                // Connection failed
            }
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        // Direct socket connection
    }

    override suspend fun rejectConnection(peerId: String) {}

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        removePeer(peerId)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        activeConnections[peerId]?.write(bytes)
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        try {
            leAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {}
        try {
            leScanner?.stopScan(scanCallback)
        } catch (e: Exception) {}

        serverSocket?.close()
        serverSocket = null
        activeConnections.values.forEach { it.cancel() }
        activeConnections.clear()
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun startServer() {
        if (serverSocket != null) return
        try {
            // Allocate dynamic L2CAP PSM port
            val ss = bluetoothAdapter?.listenUsingInsecureL2capChannel() ?: return
            serverSocket = ss
            localPsm = ss.psm

            coroutineScope.launch {
                var shouldLoop = true
                while (shouldLoop) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        shouldLoop = false
                        null
                    }
                    socket?.let { manageConnectedSocket(it, it.remoteDevice) }
                }
            }
        } catch (e: Exception) {
            _state.value = TransportState.ERROR
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, device: BluetoothDevice) {
        val address = device.address
        val name = device.name ?: "BLE Peer"
        addOrUpdatePeer(address, name, true)
        val thread = L2capConnectedThread(socket, address)
        activeConnections[address] = thread
        thread.start()
    }

    private inner class L2capConnectedThread(
        private val socket: BluetoothSocket,
        private val peerId: String
    ) : Thread() {
        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream

        override fun run() {
            while (true) {
                try {
                    val lengthBytes = ByteArray(4)
                    var readLen = inStream.read(lengthBytes)
                    if (readLen == -1) break

                    val length = ByteBuffer.wrap(lengthBytes).int
                    if (length > 0 && length < 65536) {
                        val payload = ByteArray(length)
                        var total = 0
                        while (total < length) {
                            val r = inStream.read(payload, total, length - total)
                            if (r == -1) break
                            total += r
                        }
                        if (total == length) {
                            _incomingFrames.tryEmit(IncomingFrame(peerId, payload))
                        }
                    }
                } catch (e: IOException) {
                    break
                }
            }
            activeConnections.remove(peerId)
            removePeer(peerId)
        }

        fun write(bytes: ByteArray) {
            try {
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                outStream.write(lengthBytes)
                outStream.write(bytes)
                outStream.flush()
            } catch (e: IOException) {}
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {}
        }
    }

    private fun addOrUpdatePeer(id: String, name: String, isConnected: Boolean) {
        _peers.update { current ->
            current + (id to Peer(id, name, isConnected))
        }
    }

    private fun removePeer(id: String) {
        _peers.update { current -> current - id }
    }
}
