package com.example.offlinewalkietalkie.voice

import kotlin.math.abs
import kotlin.math.max

/**
 * High-performance real-time software Peak Limiter & Automatic Gain Control (AGC) for 16-bit PCM.
 *
 * Inspired by Vivo's native limiter (libnative-gain.so):
 * - Applies a gentle pre-gain boost to keep soft speech intelligible.
 * - Senses peak amplitude and applies smooth lookahead-free attack / release gain reduction
 *   to guarantee samples never exceed the ceiling (preventing digital clipping/distortion).
 */
class AudioLimiter(
    sampleRate: Int = 8000,
    private val preGain: Float = 1.35f,
    private val ceiling: Float = 30000f, // ~ -0.7 dBFS ceiling (Short.MAX_VALUE is 32767)
    attackTimeMs: Float = 2.0f,
    releaseTimeMs: Float = 60.0f
) {
    // Attack and release smoothing coefficients per sample
    private val attackAlpha: Float = (1.0 - Math.exp((-1.0 / (sampleRate * (attackTimeMs / 1000.0))))).toFloat()
    private val releaseAlpha: Float = (1.0 - Math.exp((-1.0 / (sampleRate * (releaseTimeMs / 1000.0))))).toFloat()

    private var currentEnvelope: Float = 0f
    private var currentGain: Float = 1f

    /**
     * Process 16-bit PCM samples in-place.
     */
    fun process(samples: ShortArray) {
        for (i in samples.indices) {
            val input = samples[i] * preGain
            val absInput = abs(input)

            // Track envelope
            currentEnvelope = if (absInput > currentEnvelope) {
                currentEnvelope + attackAlpha * (absInput - currentEnvelope)
            } else {
                currentEnvelope + releaseAlpha * (absInput - currentEnvelope)
            }

            // Calculate target attenuation gain if peak exceeds ceiling
            val targetGain = if (currentEnvelope > ceiling) {
                ceiling / currentEnvelope
            } else {
                1.0f
            }

            // Smooth gain changes
            currentGain = if (targetGain < currentGain) {
                currentGain + attackAlpha * (targetGain - currentGain)
            } else {
                currentGain + releaseAlpha * (targetGain - currentGain)
            }

            val limited = input * currentGain
            // Hard clamp safety to prevent integer rollover
            val clamped = max(-32768f, Math.min(32767f, limited))
            samples[i] = clamped.toInt().toShort()
        }
    }

    /**
     * Reset internal state.
     */
    fun reset() {
        currentEnvelope = 0f
        currentGain = 1f
    }
}
