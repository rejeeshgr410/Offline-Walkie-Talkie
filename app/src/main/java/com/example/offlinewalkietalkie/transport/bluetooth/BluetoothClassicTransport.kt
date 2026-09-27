package com.example.offlinewalkietalkie.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothClassicTransport(private val context: Context) : Transport {

    companion object {
        private const val APP_NAME = "OfflineWalkieTalkie"
        private val APP_UUID: UUID = UUID.fromString("1f2a3b4c-5d6e-7f8a-9b0c-1d2e3f4a5b6c")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

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
    private var activeConnections = mutableMapOf<String, ConnectedThread>()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val name = it.name ?: "Unknown"
                        val address = it.address
                        addOrUpdatePeer(address, name, activeConnections.containsKey(address))
                    }
                }
            }
        }
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        
        _state.value = TransportState.ACTIVE
        startAcceptThread()
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        
        _state.value = TransportState.ACTIVE
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(discoveryReceiver, filter)
        bluetoothAdapter.startDiscovery()
    }

    override suspend fun connect(peerId: String) {
        if (bluetoothAdapter == null) return
        bluetoothAdapter.cancelDiscovery()
        val device = bluetoothAdapter.getRemoteDevice(peerId)
        coroutineScope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()
                manageConnectedSocket(socket, device)
            } catch (e: IOException) {
                // Connection failed
            }
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        // Bluetooth fallback connects directly without separate accept prompt in MVP
    }

    override suspend fun rejectConnection(peerId: String) {
        // Not used in standard BT MVP
    }

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
        bluetoothAdapter?.cancelDiscovery()
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: IllegalArgumentException) {
            // Not registered
        }
        serverSocket?.close()
        serverSocket = null
        activeConnections.values.forEach { it.cancel() }
        activeConnections.clear()
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun startAcceptThread() {
        coroutineScope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                var shouldLoop = true
                while (shouldLoop) {
                    val socket: BluetoothSocket? = try {
                        serverSocket?.accept()
                    } catch (e: IOException) {
                        shouldLoop = false
                        null
                    }
                    socket?.let {
                        manageConnectedSocket(it, it.remoteDevice)
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, device: BluetoothDevice) {
        val address = device.address
        val name = device.name ?: "Unknown"
        addOrUpdatePeer(address, name, true)
        val thread = ConnectedThread(socket, address)
        activeConnections[address] = thread
        thread.start()
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket, private val peerId: String) : Thread() {
        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream

        override fun run() {
            while (true) {
                try {
                    val lengthBytes = ByteArray(4)
                    var readLength = inStream.read(lengthBytes)
                    if (readLength == -1) break
                    
                    val length = ByteBuffer.wrap(lengthBytes).int
                    if (length > 0) {
                        val payload = ByteArray(length)
                        var totalRead = 0
                        while (totalRead < length) {
                            val r = inStream.read(payload, totalRead, length - totalRead)
                            if (r == -1) break
                            totalRead += r
                        }
                        if (totalRead == length) {
                            _incomingFrames.tryEmit(IncomingFrame(peerId, payload))
                        }
                    }
                } catch (e: IOException) {
                    break
                }
            }
            // Disconnected
            activeConnections.remove(peerId)
            removePeer(peerId)
        }

        fun write(bytes: ByteArray) {
            try {
                val lengthBytes = ByteBuffer.allocate(4).putInt(bytes.size).array()
                outStream.write(lengthBytes)
                outStream.write(bytes)
                outStream.flush()
            } catch (e: IOException) {
                // Send failed
            }
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
