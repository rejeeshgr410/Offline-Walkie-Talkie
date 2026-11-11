package com.example.offlinewalkietalkie.transport.wifidirect

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Looper
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

@SuppressLint("MissingPermission")
class WifiDirectTransport(private val context: Context) : Transport {

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, Looper.getMainLooper(), null)

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
    
    private var serverSocket: ServerSocket? = null
    private var activeConnections = mutableMapOf<String, WdConnectedThread>()
    
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peerList ->
                        val currentList = peerList.deviceList
                        _peers.update { current ->
                            val updated = current.toMutableMap()
                            currentList.forEach { device ->
                                val id = device.deviceAddress
                                val name = device.deviceName
                                updated[id] = Peer(id, name, activeConnections.containsKey(id))
                            }
                            updated
                        }
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    manager?.requestConnectionInfo(channel) { info ->
                        if (info.groupFormed) {
                            if (info.isGroupOwner) {
                                startServer()
                            } else {
                                startClient(info.groupOwnerAddress)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun safeRegisterReceiver() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, intentFilter)
            }
        } catch (_: Throwable) {}
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (manager == null || channel == null) return
        _state.value = TransportState.ACTIVE
        safeRegisterReceiver()
        manager.discoverPeers(channel, null)
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (manager == null || channel == null) return
        _state.value = TransportState.ACTIVE
        safeRegisterReceiver()
        manager.discoverPeers(channel, null)
    }

    override suspend fun connect(peerId: String) {
        if (manager == null || channel == null) return
        val config = WifiP2pConfig().apply {
            deviceAddress = peerId
        }
        manager.connect(channel, config, null)
    }

    override suspend fun acceptConnection(peerId: String) {}

    override suspend fun rejectConnection(peerId: String) {}

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        updatePeerStatus(peerId, false)
        if (activeConnections.isEmpty()) {
            manager?.removeGroup(channel, null)
        }
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        activeConnections[peerId]?.write(bytes)
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {}
        manager?.removeGroup(channel, null)
        manager?.cancelConnect(channel, null)
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
        coroutineScope.launch {
            try {
                serverSocket = ServerSocket(8888)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val peerAddress = socket.inetAddress.hostAddress ?: "unknown"
                    manageSocket(socket, peerAddress)
                }
            } catch (e: Exception) {}
        }
    }

    private fun startClient(hostAddress: InetAddress) {
        coroutineScope.launch {
            try {
                val socket = Socket()
                socket.bind(null)
                socket.connect(InetSocketAddress(hostAddress, 8888), 5000)
                manageSocket(socket, hostAddress.hostAddress ?: "unknown")
            } catch (e: Exception) {}
        }
    }

    private fun manageSocket(socket: Socket, peerId: String) {
        updatePeerStatus(peerId, true)
        val thread = WdConnectedThread(socket, peerId)
        activeConnections[peerId] = thread
        thread.start()
    }

    private fun updatePeerStatus(id: String, isConnected: Boolean) {
        _peers.update { current ->
            val peer = current[id] ?: Peer(id, "Peer $id", false)
            current + (id to peer.copy(isConnected = isConnected))
        }
    }

    private inner class WdConnectedThread(private val socket: Socket, private val peerId: String) : Thread() {
        private val inStream: InputStream = socket.getInputStream()
        private val outStream: OutputStream = socket.getOutputStream()

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
            activeConnections.remove(peerId)
            updatePeerStatus(peerId, false)
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
}
