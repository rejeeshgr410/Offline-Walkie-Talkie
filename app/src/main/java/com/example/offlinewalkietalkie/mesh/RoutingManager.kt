package com.example.offlinewalkietalkie.mesh

import android.util.Log
import com.example.offlinewalkietalkie.protocol.Frame
import com.example.offlinewalkietalkie.protocol.FrameCodec
import com.example.offlinewalkietalkie.transport.TransportManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Collections

class RoutingManager(private val transport: TransportManager) {

    companion object {
        private const val TAG = "TL/Routing"
    }

    private val _incomingFrames = MutableSharedFlow<Pair<String, Frame>>(extraBufferCapacity = 100)
    val incomingFrames = _incomingFrames.asSharedFlow()

    private val seenFramesCache = Collections.newSetFromMap(object : java.util.LinkedHashMap<Int, Boolean>(200, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>?): Boolean {
            return size > 1000
        }
    })

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var totalRawReceived = 0
    private var totalDecoded = 0
    private var totalDeduped = 0
    private var totalEmitted = 0

    init {
        transport.incomingFrames().onEach { incoming ->
            totalRawReceived++
            if (totalRawReceived == 1 || totalRawReceived % 50 == 0) {
                Log.i(TAG, "raw frame #$totalRawReceived from ${incoming.peerId} size=${incoming.bytes.size}B")
            }

            val decodedFrame = FrameCodec.decode(incoming.bytes)
            if (decodedFrame == null) {
                Log.w(TAG, "decode FAILED for frame from ${incoming.peerId} size=${incoming.bytes.size}")
                return@onEach
            }
            totalDecoded++
            
            val frameHash = generateLogicalHash(decodedFrame)
            
            synchronized(seenFramesCache) {
                if (seenFramesCache.contains(frameHash)) {
                    totalDeduped++
                    if (totalDeduped <= 5 || totalDeduped % 50 == 0) {
                        Log.w(TAG, "DEDUP drop #$totalDeduped type=${decodedFrame.type} seq=${decodedFrame.sequence} hash=$frameHash")
                    }
                    return@onEach
                }
                seenFramesCache.add(frameHash)
            }

            totalEmitted++
            if (totalEmitted <= 3 || totalEmitted % 50 == 0) {
                Log.i(TAG, "emit #$totalEmitted type=${decodedFrame.type} seq=${decodedFrame.sequence} encrypted=${decodedFrame.isEncrypted()} payloadSize=${decodedFrame.payload.size}")
            }
            _incomingFrames.tryEmit(Pair(incoming.peerId, decodedFrame))
            
        }.launchIn(scope)
    }

    suspend fun sendTo(peerId: String, frame: Frame) {
        val frameHash = generateLogicalHash(frame)
        synchronized(seenFramesCache) {
            seenFramesCache.add(frameHash)
        }
        transport.send(peerId, frame.toBytes())
    }

    fun markAsSeen(frame: Frame) {
        val hash = generateLogicalHash(frame)
        synchronized(seenFramesCache) {
            seenFramesCache.add(hash)
        }
    }

    fun generateLogicalHash(frame: Frame): Int {
        var result = frame.sessionId.hashCode()
        result = 31 * result + frame.sequence
        result = 31 * result + frame.timestamp
        result = 31 * result + frame.type.hashCode()
        return result
    }
}
