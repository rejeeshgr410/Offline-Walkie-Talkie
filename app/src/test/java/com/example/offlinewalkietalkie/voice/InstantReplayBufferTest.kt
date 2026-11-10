package com.example.offlinewalkietalkie.voice

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InstantReplayBufferTest {

    private lateinit var replayBuffer: InstantReplayBuffer

    @Before
    fun setUp() {
        // 2-second buffer @ 1000 Hz, 16-bit mono -> capacity = 2 * 1000 * 2 = 4000 bytes
        replayBuffer = InstantReplayBuffer(maxDurationSeconds = 2, sampleRate = 1000)
    }

    @Test
    fun testBufferInitialState() {
        assertFalse(replayBuffer.hasAudio.value)
        assertFalse(replayBuffer.isPlaying.value)
        assertEquals(0L, replayBuffer.bufferedDurationMs.value)
        assertEquals(0, replayBuffer.getSnapshot().size)
    }

    @Test
    fun testWriteAndSnapshot() {
        // Write 1000 bytes (500ms of audio)
        val testData = ByteArray(1000) { (it % 120).toByte() }
        replayBuffer.write(testData)

        assertTrue(replayBuffer.hasAudio.value)
        assertEquals(500L, replayBuffer.bufferedDurationMs.value)

        val snapshot = replayBuffer.getSnapshot()
        assertEquals(1000, snapshot.size)
        assertArrayEquals(testData, snapshot)
    }

    @Test
    fun testCircularWrapAroundMaintainsOrdering() {
        // Buffer capacity is 4000 bytes.
        // Let's write 3000 bytes of block A (0x11)
        val blockA = ByteArray(3000) { 0x11.toByte() }
        replayBuffer.write(blockA)

        // Now write 2000 bytes of block B (0x22)
        // Total written = 5000 bytes > capacity 4000 bytes.
        // The first 1000 bytes of block A should be evicted.
        // Snapshot should contain 1000 bytes of 0x11 followed by 2000 bytes of 0x22 = total 3000 bytes? Wait!
        // capacity is 4000 bytes: total bytes stored is capped at 4000 bytes!
        val blockB = ByteArray(2000) { 0x22.toByte() }
        replayBuffer.write(blockB)

        val snapshot = replayBuffer.getSnapshot()
        assertEquals(4000, snapshot.size)

        // Older data: 2000 bytes of block A (0x11)
        for (i in 0 until 2000) {
            assertEquals("Expected block A byte at index $i", 0x11.toByte(), snapshot[i])
        }
        // Newer data: 2000 bytes of block B (0x22)
        for (i in 2000 until 4000) {
            assertEquals("Expected block B byte at index $i", 0x22.toByte(), snapshot[i])
        }
    }

    @Test
    fun testClearWipesMemory() {
        val testData = ByteArray(1000) { 0x33.toByte() }
        replayBuffer.write(testData)
        assertTrue(replayBuffer.hasAudio.value)

        replayBuffer.clear()
        assertFalse(replayBuffer.hasAudio.value)
        assertEquals(0L, replayBuffer.bufferedDurationMs.value)
        assertEquals(0, replayBuffer.getSnapshot().size)
    }
}
