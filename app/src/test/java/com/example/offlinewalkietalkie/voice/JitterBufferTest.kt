package com.example.offlinewalkietalkie.voice

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class JitterBufferTest {

    private lateinit var fakeCodec: FakeAudioCodec
    private lateinit var jitterBuffer: JitterBuffer

    class FakeAudioCodec : AudioCodec {
        var decodeCount = 0
        var plcCount = 0
        var resetCount = 0

        override fun encode(pcm: ShortArray): ByteArray {
            return ByteArray(pcm.size) { 1 }
        }

        override fun decode(encoded: ByteArray): ShortArray {
            decodeCount++
            return ShortArray(960) { (encoded.getOrNull(0) ?: 0).toShort() }
        }

        override fun decodeLostFrame(): ShortArray {
            plcCount++
            return ShortArray(960) { -999 } // Distinct marker for PLC frame
        }

        override fun reset() {
            resetCount++
        }
    }

    @Before
    fun setUp() {
        fakeCodec = FakeAudioCodec()
        jitterBuffer = JitterBuffer(fakeCodec)
    }

    @Test
    fun testInitialBufferingDelay() {
        // First frame pushed (target min delay is 2 frames = 40ms)
        jitterBuffer.push(1, byteArrayOf(10), System.currentTimeMillis())
        assertNull("Playout should not start with only 1 frame buffered", jitterBuffer.pop())

        // Second frame pushed (meets MIN_DELAY_FRAMES = 2)
        jitterBuffer.push(2, byteArrayOf(20), System.currentTimeMillis() + 20)
        
        val frame1 = jitterBuffer.pop()
        assertNotNull("Playout should start once 2 frames are buffered", frame1)
        assertEquals(10, frame1!![0].toInt())

        val frame2 = jitterBuffer.pop()
        assertNotNull(frame2)
        assertEquals(20, frame2!![0].toInt())

        // Buffer now empty, underrun should return null
        assertNull(jitterBuffer.pop())
    }

    @Test
    fun testPacketLossConcealmentOnMissingFrame() {
        // Push frame 1, 2, and 4 (frame 3 is lost)
        val now = System.currentTimeMillis()
        jitterBuffer.push(1, byteArrayOf(1), now)
        jitterBuffer.push(2, byteArrayOf(2), now + 20)
        jitterBuffer.push(4, byteArrayOf(4), now + 60)

        // Pop 1
        val f1 = jitterBuffer.pop()
        assertNotNull(f1)
        assertEquals(1, f1!![0].toInt())

        // Pop 2
        val f2 = jitterBuffer.pop()
        assertNotNull(f2)
        assertEquals(2, f2!![0].toInt())

        // Pop 3 -> Missing! Should trigger PLC concealment
        val f3 = jitterBuffer.pop()
        assertNotNull("Missing frame should be concealed via PLC", f3)
        assertEquals(-999, f3!![0].toInt())
        assertEquals(1, fakeCodec.plcCount)
        assertEquals(1, jitterBuffer.metrics.value.packetLossCount)

        // Pop 4 -> Available in buffer
        val f4 = jitterBuffer.pop()
        assertNotNull(f4)
        assertEquals(4, f4!![0].toInt())
    }

    @Test
    fun testLateAndDuplicatePacketsDiscarded() {
        val now = System.currentTimeMillis()
        jitterBuffer.push(1, byteArrayOf(1), now)
        jitterBuffer.push(2, byteArrayOf(2), now + 20)

        jitterBuffer.pop() // plays seq 1
        jitterBuffer.pop() // plays seq 2

        // Now an old/duplicate frame arrives for seq 2
        jitterBuffer.push(2, byteArrayOf(2), now + 40)
        assertEquals(1, jitterBuffer.metrics.value.lateDroppedCount)

        // And even older seq 1
        jitterBuffer.push(1, byteArrayOf(1), now + 50)
        assertEquals(2, jitterBuffer.metrics.value.lateDroppedCount)
    }

    @Test
    fun testStaleVoiceCutoffDiscardsExpiredAudio() {
        val now = System.currentTimeMillis()
        jitterBuffer.push(1, byteArrayOf(1), now)
        jitterBuffer.push(2, byteArrayOf(2), now + 20)

        // Wait longer than STALE_VOICE_CUTOFF_MS (200ms)
        Thread.sleep(220)

        // Pop should detect stale frame and discard it
        val popped = jitterBuffer.pop()
        // Either discarded or returns null when both expired
        assertTrue("Late packets dropped count should increase due to stale cutoff", 
            jitterBuffer.metrics.value.lateDroppedCount > 0)
    }

    @Test
    fun testMetricsTracking() {
        val now = System.currentTimeMillis()
        jitterBuffer.push(1, byteArrayOf(1), now)
        jitterBuffer.push(2, byteArrayOf(2), now + 20)

        val metrics = jitterBuffer.metrics.value
        assertEquals(40, metrics.bufferDepthMs) // 2 frames * 20ms = 40ms
        assertEquals(0, metrics.packetLossCount)
        assertEquals(0, metrics.lateDroppedCount)
    }
}
