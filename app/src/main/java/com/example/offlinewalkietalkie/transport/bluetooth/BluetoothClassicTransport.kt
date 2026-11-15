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
import android.util.Log
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
class BluetoothClassicTransport(private val context: Context, private val identityKeyHex: String = "") : Transport {
    private val _error = MutableStateFlow<String?>(null)
    override val lastError = _error.asStateFlow()
    private val _advertising = MutableStateFlow(false)
    override val isAdvertising = _advertising.asStateFlow()
    private val _discovering = MutableStateFlow(false)
    override val isDiscovering = _discovering.asStateFlow()
    private val _pending = MutableStateFlow<ConnectionRequest?>(null)
    override val pendingConnectionRequest = _pending.asStateFlow()
    private val confirmations = MutableSharedFlow<PairingConfirmation>(extraBufferCapacity = 16)
    override val pairingConfirmations = confirmations.asSharedFlow()
    private var localName = "TalkLoop"
    private var receiverRegistered = false

    companion object {
        private const val TAG = "TL/BtClassic"
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
    private val activeConnections = java.util.concurrent.ConcurrentHashMap<String, ConnectedThread>()
    private val pendingSockets = java.util.concurrent.ConcurrentHashMap<String, BluetoothSocket>()

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
        
        localName = identity.endpointName
        if (serverSocket != null) return
        _state.value = TransportState.ACTIVE
        startAcceptThread()
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        
        _state.value = TransportState.ACTIVE
        localName = identity.endpointName
        bluetoothAdapter.bondedDevices.forEach { addOrUpdatePeer(it.address, it.name ?: "Bluetooth phone", activeConnections.containsKey(it.address)) }
        if (receiverRegistered) return
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(discoveryReceiver, filter)
            }
            receiverRegistered = true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register discoveryReceiver", e)
        }
        _discovering.value = bluetoothAdapter.startDiscovery()
    }

    override suspend fun connect(peerId: String) {
        if (bluetoothAdapter == null) return
        bluetoothAdapter.cancelDiscovery()
        val device = bluetoothAdapter.getRemoteDevice(peerId)
        coroutineScope.launch {
            try {
                val socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                pendingSockets[peerId] = socket
                socket.connect()
                manageConnectedSocket(socket, device)
            } catch (e: IOException) {
                _error.value = "Bluetooth connection failed: ${e.message}"
                pendingSockets[peerId]?.close()
            } finally {
                pendingSockets.remove(peerId)
            }
        }
    }

    override suspend fun acceptConnection(peerId: String) {
        // Bluetooth fallback connects directly without separate accept prompt in MVP
    }

    override suspend fun rejectConnection(peerId: String) {
        disconnect(peerId)
    }

    override suspend fun confirmPairing(peerId: String, authCode: String) {
        val request = activeConnections[peerId]?.request ?: return
        if (request.authCode == authCode) activeConnections[peerId]?.writeControl("confirm", authCode)
    }

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        if (_pending.value?.peerId == peerId) _pending.value = null
        removePeer(peerId)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) { activeConnections[peerId]?.write(bytes) }
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
        receiverRegistered = false
        _advertising.value = false
        _discovering.value = false
        _pending.value = null
        activeConnections.values.forEach { it.cancel() }
        activeConnections.clear()
        pendingSockets.values.forEach { runCatching { it.close() } }
        pendingSockets.clear()
        _peers.value = emptyMap()
        _state.value = TransportState.IDLE
        coroutineScope.coroutineContext.cancelChildren()
    }

    private fun startAcceptThread() {
        coroutineScope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
                _advertising.value = serverSocket != null
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
                _error.value = "Bluetooth listener failed: ${e.message}"
                _advertising.value = false
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, device: BluetoothDevice) {
        val address = device.address
        val name = device.name ?: "Unknown"
        addOrUpdatePeer(address, name, true)
        val thread = ConnectedThread(socket, address)
        activeConnections.put(address, thread)?.cancel()
        thread.start()
        thread.writeControl("hello", java.util.UUID.randomUUID().toString())
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket, private val peerId: String) : Thread() {
        private val inStream: InputStream = socket.inputStream
        private val outStream: OutputStream = socket.outputStream
        private val nonce = java.util.UUID.randomUUID().toString()
        var request: ConnectionRequest? = null

        fun writeControl(kind: String, value: String) {
            val json = org.json.JSONObject().put("kind", kind).put("value", if (kind == "hello") nonce else value)
                .put("identity", identityKeyHex).put("name", localName)
            write(byteArrayOf(0x54, 0x43) + json.toString().toByteArray(Charsets.UTF_8))
        }

        private fun consumeControl(payload: ByteArray): Boolean {
            if (payload.size < 2 || payload[0] != 0x54.toByte() || payload[1] != 0x43.toByte()) return false
            val json = org.json.JSONObject(String(payload, 2, payload.size - 2, Charsets.UTF_8))
            when (json.getString("kind")) {
                "hello" -> {
                    if (request != null) return true
                    val transcript = listOf("$nonce:$identityKeyHex", "${json.getString("value")}:${json.getString("identity")}").sorted().joinToString("|")
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(transcript.toByteArray())
                    val code = ((java.nio.ByteBuffer.wrap(hash).int.toLong() and 0xffffffffL) % 1000000)
                    val req = ConnectionRequest(peerId, json.getString("name"), "%06d".format(java.util.Locale.US, code), json.optString("identity").takeIf { it.isNotBlank() })
                    request = req
                    _pending.value = req
                    _connectionRequests.tryEmit(req)
                }
                "confirm" -> request?.takeIf { it.authCode == json.getString("value") }?.let {
                    confirmations.tryEmit(PairingConfirmation(peerId, it.authCode))
                    if (_pending.value?.peerId == peerId) _pending.value = null
                }
            }
            return true
        }

        override fun run() {
            while (true) {
                try {
                    val payload = RfcommFraming.read(inStream)
                    if (!consumeControl(payload)) _incomingFrames.tryEmit(IncomingFrame(peerId, payload))
                } catch (e: Exception) {
                    break
                }
            }
            // Disconnected
            cancel()
            if (activeConnections.remove(peerId, this)) removePeer(peerId)
        }

        @Synchronized fun write(bytes: ByteArray) {
            try {
                RfcommFraming.write(outStream, bytes)
            } catch (e: IOException) {
                _error.value = "Bluetooth write failed: ${e.message}"
                cancel()
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
