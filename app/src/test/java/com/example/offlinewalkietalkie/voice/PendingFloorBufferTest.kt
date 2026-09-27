package com.example.offlinewalkietalkie.voice

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PendingFloorBufferTest {

    private lateinit var buffer: PendingFloorBuffer

    @Before
    fun setUp() {
        buffer = PendingFloorBuffer(maxFrames = 25)
    }

    @Test
    fun testPushAndDrainInOrder() {
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)

        // Push 10 frames
        for (i in 1..10) {
            val audioData = byteArrayOf(i.toByte(), (i * 2).toByte())
            buffer.push(sequence = i, timestampMs = i * 20L, data = audioData)
        }

        assertEquals(10, buffer.size)
        assertFalse(buffer.isEmpty)

        val drained = buffer.drain()
        assertEquals(10, drained.size)
        assertTrue(buffer.isEmpty)

        // Verify sequence order preserved
        for (i in 0 until 10) {
            assertEquals(i + 1, drained[i].sequence)
            assertEquals((i + 1) * 20L, drained[i].timestampMs)
            assertArrayEquals(byteArrayOf((i + 1).toByte(), ((i + 1) * 2).toByte()), drained[i].data)
        }
    }

    @Test
    fun testRingBufferOverflowDropsOldest() {
        // Buffer capacity is 25 frames (500 ms)
        // Push 30 frames
        for (i in 1..30) {
            buffer.push(sequence = i, timestampMs = i * 20L, data = byteArrayOf(i.toByte()))
        }

        assertEquals(25, buffer.size)

        val drained = buffer.drain()
        assertEquals(25, drained.size)

        // Oldest frames 1..5 should have been dropped, keeping 6..30
        assertEquals(6, drained.first().sequence)
        assertEquals(30, drained.last().sequence)
    }

    @Test
    fun testClearEmptiesBuffer() {
        buffer.push(1, 100L, byteArrayOf(1, 2, 3))
        buffer.push(2, 120L, byteArrayOf(4, 5, 6))
        assertEquals(2, buffer.size)

        buffer.clear()
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
        assertTrue(buffer.drain().isEmpty())
    }
}
