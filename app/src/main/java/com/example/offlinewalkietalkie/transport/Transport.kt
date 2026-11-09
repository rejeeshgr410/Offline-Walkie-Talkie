package com.example.offlinewalkietalkie.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LocalIdentity(val endpointName: String)

data class Peer(
    val id: String,
    val name: String,
    val isConnected: Boolean,
    val isAuthenticated: Boolean = false,
    val isDirect: Boolean = true,
    val hopCount: Int = 0
)

data class IncomingFrame(val peerId: String, val bytes: ByteArray)

data class ConnectionRequest(val peerId: String, val peerName: String, val authCode: String)

enum class TransportState {
    IDLE, ACTIVE, ERROR
}

interface Transport {
    val state: StateFlow<TransportState>
    val peers: Flow<List<Peer>>
    val connectionRequests: Flow<ConnectionRequest>

    /** Human-readable reason the last advertise/discover/connect attempt failed, or null. */
    val lastError: StateFlow<String?> get() = NO_ERROR

    /** True while this device is discoverable by peers. */
    val isAdvertising: StateFlow<Boolean> get() = ALWAYS_FALSE

    /** True while this device is scanning for peers. */
    val isDiscovering: StateFlow<Boolean> get() = ALWAYS_FALSE

    suspend fun startAdvertising(identity: LocalIdentity)
    suspend fun startDiscovery(identity: LocalIdentity)

    /**
     * Stop being discoverable without tearing down live links. Needed to republish under
     * a new endpoint name when group identity or role changes.
     */
    suspend fun stopAdvertising() {}

    suspend fun connect(peerId: String)
    suspend fun acceptConnection(peerId: String)
    suspend fun rejectConnection(peerId: String)
    suspend fun disconnect(peerId: String)

    suspend fun send(peerId: String, bytes: ByteArray)
    fun incomingFrames(): Flow<IncomingFrame>

    suspend fun stop()

    companion object {
        val NO_ERROR: StateFlow<String?> = MutableStateFlow(null)
        val ALWAYS_FALSE: StateFlow<Boolean> = MutableStateFlow(false)
    }
}
