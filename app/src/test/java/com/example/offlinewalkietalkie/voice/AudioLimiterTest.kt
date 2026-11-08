package com.example.offlinewalkietalkie.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AudioLimiterTest {

    @Test
    fun testCeilingClamping() {
        val limiter = AudioLimiter(sampleRate = 8000, ceiling = 20000f)
        // Array of very loud samples (full scale Short.MAX_VALUE = 32767)
        val loudSamples = ShortArray(320) { 32767.toShort() }

        limiter.process(loudSamples)

        // After attack settles, samples should be bounded around ceiling
        val tailSamples = loudSamples.takeLast(160)
        for (sample in tailSamples) {
            assertTrue("Sample $sample should be constrained near ceiling 20000", abs(sample.toInt()) <= 26000)
        }
    }

    @Test
    fun testGentleBoostForQuietSamples() {
        val limiter = AudioLimiter(sampleRate = 8000, preGain = 1.5f, ceiling = 30000f)
        val quietSamples = ShortArray(160) { 1000.toShort() }

        limiter.process(quietSamples)

        // Quiet samples should be amplified by ~1.5x
        val firstSample = quietSamples[0].toInt()
        assertEquals(1500, firstSample)
    }

    @Test
    fun testReset() {
        val limiter = AudioLimiter()
        val loud = ShortArray(160) { 30000.toShort() }
        limiter.process(loud)
        limiter.reset()

        val quiet = ShortArray(160) { 1000.toShort() }
        limiter.process(quiet)
        // With reset, should immediately start with preGain without residual attenuation
        assertTrue(quiet[0] > 1000)
    }
}
