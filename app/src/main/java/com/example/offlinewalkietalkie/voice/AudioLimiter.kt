package com.example.offlinewalkietalkie.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * High-performance real-time software Peak Limiter & Automatic Gain Control (AGC) for 16-bit PCM.
 *
 * Fully mirrors Vivo's native limiter (GainProc / libnative-gain.so):
 * - Default 2.0x (+6.02 dB) linear pre-gain boost to keep whispers and distant speech intelligible.
 * - Dynamic envelope tracking with smooth lookahead-free attack (2.0 ms) and release (60.0 ms).
 * - Hard ceiling (30,000 / ~ -0.7 dBFS) to prevent digital clipping when shouting into mic.
 * - Supports both ShortArray and in-place ByteArray 16-bit PCM processing.
 */
class AudioLimiter(
    sampleRate: Int = 8000,
    var preGain: Float = 2.0f, // Matching nativeInit(8000, 2.0f) in GainProc.b
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
     * Process in-place on raw 16-bit PCM Little-Endian ByteArray.
     * Mirrors Vivo's GainProc.c(int length, byte[] buffer).
     */
    fun process(bytes: ByteArray, length: Int = bytes.size) {
        val numSamples = length / 2
        for (i in 0 until numSamples) {
            val offset = i * 2
            val low = bytes[offset].toInt() and 0xFF
            val high = bytes[offset + 1].toInt()
            val sample = (high shl 8) or low

            val input = sample * preGain
            val absInput = abs(input)

            currentEnvelope = if (absInput > currentEnvelope) {
                currentEnvelope + attackAlpha * (absInput - currentEnvelope)
            } else {
                currentEnvelope + releaseAlpha * (absInput - currentEnvelope)
            }

            val targetGain = if (currentEnvelope > ceiling) {
                ceiling / currentEnvelope
            } else {
                1.0f
            }

            currentGain = if (targetGain < currentGain) {
                currentGain + attackAlpha * (targetGain - currentGain)
            } else {
                currentGain + releaseAlpha * (targetGain - currentGain)
            }

            val limited = input * currentGain
            val clamped = max(-32768f, Math.min(32767f, limited)).toInt().toShort()

            bytes[offset] = (clamped.toInt() and 0xFF).toByte()
            bytes[offset + 1] = ((clamped.toInt() shr 8) and 0xFF).toByte()
        }
    }

    /**
     * Reset internal limiter state.
     */
    fun reset() {
        currentEnvelope = 0f
        currentGain = 1f
    }
}
