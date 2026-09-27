package com.example.offlinewalkietalkie.testutil

import com.example.offlinewalkietalkie.transport.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/**
 * FakeTransport for Stage 3 unit & integration tests per Section 4 & Section 16.
 * Simulates peer discovery, handshake requests, packet transmission, and link drops.
 */
class FakeTransport(val localId: String = "fake_local") : Transport {

    private val _state = MutableStateFlow(TransportState.IDLE)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: Flow<List<Peer>> = _peers.map { it.values.toList() }

    private val _connectionRequests = MutableSharedFlow<ConnectionRequest>(
        replay = 10,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val connectionRequests: Flow<ConnectionRequest> = _connectionRequests.asSharedFlow()

    private val _incomingFrames = MutableSharedFlow<IncomingFrame>(
        replay = 10,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override fun incomingFrames(): Flow<IncomingFrame> = _incomingFrames.asSharedFlow()

    val sentPackets = mutableListOf<Pair<String, ByteArray>>()
    var linkedPeerTransport: FakeTransport? = null

    fun linkTo(other: FakeTransport) {
        this.linkedPeerTransport = other
        other.linkedPeerTransport = this
    }

    fun simulateDiscoveredPeer(id: String, name: String, isConnected: Boolean = false) {
        _peers.update { it + (id to Peer(id, name, isConnected)) }
    }

    fun simulateConnectionRequest(peerId: String, peerName: String, authCode: String = "123456") {
        _connectionRequests.tryEmit(ConnectionRequest(peerId, peerName, authCode))
    }

    fun simulateIncomingPacket(peerId: String, bytes: ByteArray) {
        _incomingFrames.tryEmit(IncomingFrame(peerId, bytes))
    }

    override suspend fun startAdvertising(identity: LocalIdentity) {
        _state.value = TransportState.ACTIVE
    }

    override suspend fun startDiscovery(identity: LocalIdentity) {
        _state.value = TransportState.ACTIVE
    }

    override suspend fun connect(peerId: String) {
        _peers.update { it + (peerId to Peer(peerId, "Peer-$peerId", isConnected = true)) }
        linkedPeerTransport?.simulateDiscoveredPeer(localId, "Local-$localId", isConnected = true)
    }

    override suspend fun acceptConnection(peerId: String) {
        _peers.update { current ->
            val p = current[peerId] ?: Peer(peerId, "Peer-$peerId", false)
            current + (peerId to p.copy(isConnected = true))
        }
        linkedPeerTransport?._peers?.update { current ->
            val p = current[localId] ?: Peer(localId, "Local-$localId", false)
            current + (localId to p.copy(isConnected = true))
        }
    }

    override suspend fun rejectConnection(peerId: String) {
        _peers.update { it - peerId }
    }

    override suspend fun disconnect(peerId: String) {
        _peers.update { it - peerId }
        linkedPeerTransport?._peers?.update { it - localId }
    }

    override suspend fun send(peerId: String, bytes: ByteArray) {
        sentPackets.add(peerId to bytes.copyOf())
        linkedPeerTransport?.simulateIncomingPacket(localId, bytes.copyOf())
    }

    override suspend fun stop() {
        _state.value = TransportState.IDLE
        _peers.value = emptyMap()
    }
}
