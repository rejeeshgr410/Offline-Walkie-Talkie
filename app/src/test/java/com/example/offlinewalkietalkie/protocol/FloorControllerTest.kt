package com.example.offlinewalkietalkie.protocol

import com.example.offlinewalkietalkie.voice.BufferedAudioFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloorControllerTest {

    @Test
    fun testDirect1to1OptimisticGrant() = runTest {
        val controller = FloorController(
            localMemberId = 100L,
            mode = SessionFloorMode.DIRECT_1_TO_1
        )

        var chirpPlayed = false
        controller.onPlayPermissionChirp = { chirpPlayed = true }

        assertEquals(FloorState.Idle, controller.floorState.value)

        controller.requestPtt()

        assertEquals(FloorState.Talking, controller.floorState.value)
        assertTrue(chirpPlayed)

        // Audio captured while talking is sent live immediately
        val isLive = controller.onAudioCaptured(1, System.currentTimeMillis(), byteArrayOf(1, 2, 3))
        assertTrue(isLive)
        assertTrue(controller.pendingBuffer.isEmpty)

        controller.releasePtt()
        assertEquals(FloorState.Idle, controller.floorState.value)
    }

    @Test
    fun testChannelModePendingBufferAndGrant() = runTest {
        val controller = FloorController(
            localMemberId = 200L,
            mode = SessionFloorMode.CHANNEL_HOST_CONTROLLED
        )

        var transmittedFrames: List<BufferedAudioFrame>? = null
        controller.onTransmitPendingFrames = { frames -> transmittedFrames = frames }

        controller.requestPtt()
        assertEquals(FloorState.Requesting, controller.floorState.value)

        // Captured audio while requesting is buffered locally, NOT sent live
        for (i in 1..5) {
            val live = controller.onAudioCaptured(i, i * 20L, byteArrayOf(i.toByte()))
            assertFalse(live)
        }
        assertEquals(5, controller.pendingBuffer.size)

        // Host grants floor
        controller.onFloorGranted()
        assertEquals(FloorState.Talking, controller.floorState.value)

        // Buffer was drained and handed over for transmission
        assertNotNull(transmittedFrames)
        assertEquals(5, transmittedFrames?.size)
        assertEquals(1, transmittedFrames?.first()?.sequence)
        assertEquals(5, transmittedFrames?.last()?.sequence)
        assertTrue(controller.pendingBuffer.isEmpty)

        // Once talking, new audio is sent live
        val liveAfterGrant = controller.onAudioCaptured(6, 120L, byteArrayOf(6))
        assertTrue(liveAfterGrant)

        controller.releasePtt()
        assertEquals(FloorState.Idle, controller.floorState.value)
    }

    @Test
    fun testChannelModeDenialClearsBuffer() = runTest {
        val controller = FloorController(
            localMemberId = 300L,
            mode = SessionFloorMode.CHANNEL_HOST_CONTROLLED
        )

        var busyPlayed = false
        controller.onPlayBusyTone = { busyPlayed = true }

        controller.requestPtt()
        assertEquals(FloorState.Requesting, controller.floorState.value)

        // Push some audio into pending buffer
        controller.onAudioCaptured(1, 20L, byteArrayOf(1))
        controller.onAudioCaptured(2, 40L, byteArrayOf(2))
        assertEquals(2, controller.pendingBuffer.size)

        // Host denies floor
        controller.onFloorDenied("Channel floor is occupied")
        assertTrue(busyPlayed)
        assertTrue(controller.pendingBuffer.isEmpty)
        assertEquals(FloorState.Idle, controller.floorState.value)
    }

    @Test
    fun testDirect1to1DeterministicCollisionResolution() = runTest {
        val nodeA = FloorController(localMemberId = 1000L, mode = SessionFloorMode.DIRECT_1_TO_1)
        val nodeB = FloorController(localMemberId = 2000L, mode = SessionFloorMode.DIRECT_1_TO_1)

        var nodeABusy = false
        var nodeBBusy = false
        nodeA.onPlayBusyTone = { nodeABusy = true }
        nodeB.onPlayBusyTone = { nodeBBusy = true }

        // Both press PTT simultaneously
        nodeA.requestPtt()
        nodeB.requestPtt()
        assertEquals(FloorState.Talking, nodeA.floorState.value)
        assertEquals(FloorState.Talking, nodeB.floorState.value)

        val collisionTime = System.currentTimeMillis()

        // Node A compares against Node B (Node A: 1000 < 2000 -> Node A wins)
        val nodeAWon = nodeA.handleDirectCollision(peerMemberId = 2000L, peerPressTimestampMs = collisionTime)
        assertTrue(nodeAWon)
        assertEquals(FloorState.Talking, nodeA.floorState.value)
        assertFalse(nodeABusy)

        // Node B compares against Node A (Node B: 2000 > 1000 -> Node B loses)
        val nodeBWon = nodeB.handleDirectCollision(peerMemberId = 1000L, peerPressTimestampMs = collisionTime)
        assertFalse(nodeBWon)
        assertEquals(FloorState.Idle, nodeB.floorState.value)
        assertTrue(nodeBBusy)
    }
}
