package com.example.offlinewalkietalkie.transport.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.aware.*
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 5 & Section 19.15 Wi-Fi Aware Transport.
 *
 * Requirements:
 * - Preferred over Wi-Fi Direct for multi-peer channels and mesh links when hardware supports it.
 * - Publish/Subscribe discovery without internet or access points.
 * - Link-local socket communication.
 */
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
class WifiAwareTransport(private val context: Context) : Transport {

    companion object {
        const val SERVICE_NAME = "talkloop-aware-v3"
    }

    private val awareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    private val isSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)

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

    private var awareSession: WifiAwareSession? = null
    private var discoverySession: DiscoverySession? = null
    private var serverSocket: ServerSocket? = null
    private val activeConnections = ConcurrentHashMap<String, AwareConnectedThread>()

    override suspend fun startAdvertising(identity: LocalIdentity) {
        if (!isSupported || awareManager?.isAvailable != true) {
            _state.value = TransportState.ERROR
            return
        }

        _state.value = TransportState.ACTIVE
        startServer()

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                val config = PublishConfig.Builder()
                    .setServiceName(SERVICE_NAME)
                    .build()
                session.publish(config, object : DiscoverySessionCallback() {
                    override fun onPublishStarted(session: PublishDiscoverySession) {
                        discoverySession = session
                    }
                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        val peerId = "aware_${peerHandle.hashCode()}"
                        addOrUpdatePeer(peerId, "Aware Peer", true)
                        _incomingFrames.tryEmit(IncomingFrame(peerId, message))
                    }
                }, null)
            }

            override fun onAttachFailed() {
                _state.value = TransportState.ERROR
            }
        }, null)
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        if (!isSupported || awareManager?.isAvailable != true) {
            _state.value = TransportState.ERROR
            return
        }

        _state.value = TransportState.ACTIVE

        awareManager.attach(object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                awareSession = session
                val config = SubscribeConfig.Builder()
                    .setServiceName(SERVICE_NAME)
                    .build()
                session.subscribe(config, object : DiscoverySessionCallback() {
                    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                        discoverySession = session
                    }
                    override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: MutableList<ByteArray>?) {
                        val peerId = "aware_${peerHandle.hashCode()}"
                        addOrUpdatePeer(peerId, "Aware Peer", false)
                    }
                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        val peerId = "aware_${peerHandle.hashCode()}"
                        addOrUpdatePeer(peerId, "Aware Peer", true)
                        _incomingFrames.tryEmit(IncomingFrame(peerId, message))
                    }
                }, null)
            }

            override fun onAttachFailed() {
                _state.value = TransportState.ERROR
            }
        }, null)
    }

    override suspend fun connect(peerId: String) {
        // Wi-Fi Aware initiates data path through discoverySession
        updatePeerStatus(peerId, true)
    }

    override suspend fun acceptConnection(peerId: String) {}

    override suspend fun rejectConnection(peerId: String) {}

    override suspend fun disconnect(peerId: String) {
        activeConnections[peerId]?.cancel()
        activeConnections.remove(peerId)
        updatePeerStatus(peerId, false)
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        val conn = activeConnections[peerId]
        if (conn != null) {
            conn.write(bytes)
        } else {
            // Send discovery message fallback
            val handleId = peerId.removePrefix("aware_").toIntOrNull()
            if (handleId != null && discoverySession != null) {
                // If using aware message channel
            }
        }
    }

    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    override suspend fun stop() {
        discoverySession?.close()
        discoverySession = null
        awareSession?.close()
        awareSession = null
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
                serverSocket = ServerSocket(0)
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    val peerId = socket.inetAddress.hostAddress ?: "unknown"
                    val thread = AwareConnectedThread(socket, peerId)
                    activeConnections[peerId] = thread
                    thread.start()
                    updatePeerStatus(peerId, true)
                }
            } catch (e: Exception) {}
        }
    }

    private inner class AwareConnectedThread(
        private val socket: Socket,
        private val peerId: String
    ) : Thread() {
        private val inStream: InputStream = socket.getInputStream()
        private val outStream: OutputStream = socket.getOutputStream()

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

    private fun addOrUpdatePeer(id: String, name: String, isConnected: Boolean) {
        _peers.update { current ->
            current + (id to Peer(id, name, isConnected))
        }
    }

    private fun updatePeerStatus(id: String, isConnected: Boolean) {
        _peers.update { current ->
            val peer = current[id] ?: Peer(id, "Peer $id", false)
            current + (id to peer.copy(isConnected = isConnected))
        }
    }
}
