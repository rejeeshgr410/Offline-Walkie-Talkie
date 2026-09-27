package com.example.offlinewalkietalkie.group

import android.util.Log
import com.example.offlinewalkietalkie.protocol.Frame
import com.example.offlinewalkietalkie.protocol.FrameType
import com.example.offlinewalkietalkie.transport.LocalIdentity
import com.example.offlinewalkietalkie.transport.TransportManager
import java.util.concurrent.atomic.AtomicInteger

/**
 * Binds [GroupSessionManager] to the real radio. Group control messages travel as
 * GROUP_CONTROL frames on the same link voice uses, so they follow the same lifecycle.
 */
class GroupNearbyPort(
    private val transport: TransportManager,
    private val sessionId: Long
) : GroupTransportPort {

    companion object {
        private const val TAG = "TL/GroupPort"
    }

    private val sequence = AtomicInteger(1)

    override suspend fun connect(endpointId: String) {
        Log.i(TAG, "connect -> $endpointId")
        transport.connect(endpointId)
    }

    override suspend fun acceptConnection(endpointId: String) {
        Log.i(TAG, "accept -> $endpointId")
        transport.acceptConnection(endpointId)
    }

    override suspend fun rejectConnection(endpointId: String) {
        Log.i(TAG, "reject -> $endpointId")
        transport.rejectConnection(endpointId)
    }

    override suspend fun disconnect(endpointId: String) {
        Log.i(TAG, "disconnect -> $endpointId")
        transport.disconnect(endpointId)
    }

    override suspend fun sendControl(endpointId: String, payload: ByteArray) {
        val frame = Frame(
            type = FrameType.GROUP_CONTROL,
            sessionId = sessionId,
            sequence = sequence.getAndIncrement(),
            timestamp = System.currentTimeMillis().toInt(),
            hopLimit = 1,
            payload = payload
        )
        transport.send(endpointId, frame.toBytes())
    }

    override suspend fun publishAdvert(advert: GroupAdvert) {
        val encoded = advert.encode()
        Log.i(TAG, "publishAdvert '$encoded'")
        transport.republishAdvertising(LocalIdentity(encoded))
    }

    override suspend fun clearAdvert() {
        Log.i(TAG, "clearAdvert")
        transport.stopAdvertising()
    }
}
