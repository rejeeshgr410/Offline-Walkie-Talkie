package com.example.offlinewalkietalkie.channel

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReplayBufferTest {

    private lateinit var replayBuffer: ReplayBuffer

    @Before
    fun setUp() {
        replayBuffer = ReplayBuffer(playback = null)
    }

    @Test
    fun testDefaultsOffAndOptIn() {
        assertFalse("Replay buffer must default to disabled per Section 14", replayBuffer.isEnabled)

        // Frames sent while disabled are ignored
        replayBuffer.onTransmissionStarted(1L, "Alice")
        replayBuffer.onFrameReceived(byteArrayOf(1, 2, 3))
        replayBuffer.onTransmissionCompleted()

        assertTrue(replayBuffer.transmissionList.value.isEmpty())

        // Enable replay buffer
        replayBuffer.isEnabled = true
        replayBuffer.onTransmissionStarted(1L, "Alice")
        replayBuffer.onFrameReceived(byteArrayOf(4, 5, 6))
        replayBuffer.onTransmissionCompleted()

        assertEquals(1, replayBuffer.transmissionList.value.size)
        assertEquals("Alice", replayBuffer.transmissionList.value[0].senderName)
    }

    @Test
    fun testRetainsMaxThreeTransmissionsFIFO() {
        replayBuffer.isEnabled = true

        // Push 4 transmissions
        for (i in 1..4) {
            replayBuffer.onTransmissionStarted(i.toLong(), "Speaker-$i")
            replayBuffer.onFrameReceived(byteArrayOf(i.toByte()))
            replayBuffer.onTransmissionCompleted()
        }

        val list = replayBuffer.transmissionList.value
        assertEquals("Must retain at most 3 transmissions per Section 14", 3, list.size)

        // FIFO: Speaker-1 evicted; Speaker-2, 3, 4 remain
        assertEquals("Speaker-2", list[0].senderName)
        assertEquals("Speaker-3", list[1].senderName)
        assertEquals("Speaker-4", list[2].senderName)
    }

    @Test
    fun testClearRemovesAllDataFromMemory() {
        replayBuffer.isEnabled = true
        replayBuffer.onTransmissionStarted(1L, "Bob")
        replayBuffer.onFrameReceived(byteArrayOf(10, 20))
        replayBuffer.onTransmissionCompleted()

        assertFalse(replayBuffer.transmissionList.value.isEmpty())

        replayBuffer.clear()
        assertTrue("Memory must be empty after clear()", replayBuffer.transmissionList.value.isEmpty())
    }
}
