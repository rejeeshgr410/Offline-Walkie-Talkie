package com.example.offlinewalkietalkie.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tactical PTT Roger Beep and Squelch Chime sound generator.
 *
 * Synthesizes studio-grade 16-bit PCM audio chimes entirely procedurally:
 * - MOTOROLA: Classic 900 Hz / 1200 Hz dual-tone frequency shift chirp
 * - QUINDAR: NASA Apollo 2525 Hz intro (PTT down) / 2475 Hz outro (PTT up)
 * - TACTICAL_CHIRP: Rapid high-frequency acoustic sweep (1500 Hz -> 2200 Hz)
 * - SQUELCH: Analog receiver squelch tail burst (CTCSS turn-off + noise burst)
 * - NONE: Covert / stealth silent mode
 */
enum class RogerBeepStyle(val id: String, val displayName: String, val description: String) {
    NONE("none", "Silent / Covert", "Completely silent operation for stealth field use"),
    MOTOROLA("motorola", "Classic Motorola", "Tactical dual-tone 900 Hz / 1200 Hz frequency chirp"),
    QUINDAR("quindar", "Apollo Quindar", "Authentic NASA Apollo 2525 Hz intro / 2475 Hz outro"),
    TACTICAL_CHIRP("tactical", "Tactical Chirp", "Rapid high-frequency acoustic burst"),
    SQUELCH("squelch", "Tone Squelch Tail", "Simulated VHF analog squelch tail & CTCSS burst");

    companion object {
        fun fromId(id: String): RogerBeepStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MOTOROLA
        }
    }
}

class RogerBeepGenerator(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "TL/RogerBeep"
        const val SAMPLE_RATE = 16000
    }

    /**
     * Synthesizes intro pre-key chime (PTT down) PCM bytes.
     */
    fun generateIntroTone(style: RogerBeepStyle, sampleRate: Int = SAMPLE_RATE): ByteArray {
        return when (style) {
            RogerBeepStyle.NONE -> ByteArray(0)
            RogerBeepStyle.MOTOROLA -> {
                // 900 Hz (45ms) -> 1200 Hz (45ms)
                val part1 = synthesizeSine(900.0, 0.045, sampleRate, volume = 0.75f)
                val part2 = synthesizeSine(1200.0, 0.045, sampleRate, volume = 0.75f)
                concatPcm(part1, part2)
            }
            RogerBeepStyle.QUINDAR -> {
                // NASA Apollo Quindar intro tone: 2525 Hz for 200ms
                synthesizeSine(2525.0, 0.200, sampleRate, volume = 0.70f)
            }
            RogerBeepStyle.TACTICAL_CHIRP -> {
                // Fast sweep from 1500 Hz to 2200 Hz over 40ms
                synthesizeChirp(1500.0, 2200.0, 0.040, sampleRate, volume = 0.80f)
            }
            RogerBeepStyle.SQUELCH -> {
                // Short pre-key tick
                synthesizeSine(1400.0, 0.025, sampleRate, volume = 0.65f)
            }
        }
    }

    /**
     * Synthesizes outro roger beep chime (PTT up / floor released) PCM bytes.
     */
    fun generateOutroTone(style: RogerBeepStyle, sampleRate: Int = SAMPLE_RATE): ByteArray {
        return when (style) {
            RogerBeepStyle.NONE -> ByteArray(0)
            RogerBeepStyle.MOTOROLA -> {
                // 1200 Hz (45ms) -> 900 Hz (55ms)
                val part1 = synthesizeSine(1200.0, 0.045, sampleRate, volume = 0.75f)
                val part2 = synthesizeSine(900.0, 0.055, sampleRate, volume = 0.75f)
                concatPcm(part1, part2)
            }
            RogerBeepStyle.QUINDAR -> {
                // NASA Apollo Quindar outro tone: 2475 Hz for 200ms
                synthesizeSine(2475.0, 0.200, sampleRate, volume = 0.70f)
            }
            RogerBeepStyle.TACTICAL_CHIRP -> {
                // Downward sweep from 2200 Hz to 1400 Hz over 45ms
                synthesizeChirp(2200.0, 1400.0, 0.045, sampleRate, volume = 0.80f)
            }
            RogerBeepStyle.SQUELCH -> {
                // Squelch tail burst: 134.4 Hz CTCSS turn-off mixed with shaped noise burst
                synthesizeSquelchTail(0.065, sampleRate, volume = 0.65f)
            }
        }
    }

    /**
     * Plays the given PCM tone asynchronously without blocking caller.
     */
    fun playTone(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE) {
        if (pcm.isEmpty()) return
        scope.launch {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = minBuf.coerceAtLeast(pcm.size)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                track.write(pcm, 0, pcm.size)

                // Wait for the track to finish draining the buffer
                val playDurationMs = (pcm.size * 1000L) / (sampleRate * 2)
                kotlinx.coroutines.delay(playDurationMs + 40)

                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                    // Release cleanup
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play roger beep tone: ${e.message}")
            }
        }
    }

    fun playIntroTone(style: RogerBeepStyle, sampleRate: Int = SAMPLE_RATE) {
        val pcm = generateIntroTone(style, sampleRate)
        playTone(pcm, sampleRate)
    }

    fun playOutroTone(style: RogerBeepStyle, sampleRate: Int = SAMPLE_RATE) {
        val pcm = generateOutroTone(style, sampleRate)
        playTone(pcm, sampleRate)
    }

    /**
     * Previews the selected style: plays intro tone, brief pause, then outro tone.
     */
    fun previewStyle(style: RogerBeepStyle, sampleRate: Int = SAMPLE_RATE) {
        if (style == RogerBeepStyle.NONE) return
        scope.launch {
            val intro = generateIntroTone(style, sampleRate)
            if (intro.isNotEmpty()) {
                playTone(intro, sampleRate)
                val introDurMs = (intro.size * 1000L) / (sampleRate * 2)
                kotlinx.coroutines.delay(introDurMs + 120)
            }
            val outro = generateOutroTone(style, sampleRate)
            if (outro.isNotEmpty()) {
                playTone(outro, sampleRate)
            }
        }
    }

    // --- Procedural PCM Waveform Synthesis Helpers ---

    private fun synthesizeSine(
        frequencyHz: Double,
        durationSec: Double,
        sampleRate: Int,
        volume: Float = 0.8f
    ): ByteArray {
        val sampleCount = (durationSec * sampleRate).toInt().coerceAtLeast(1)
        val shorts = ShortArray(sampleCount)
        val attackSamples = (0.005 * sampleRate).toInt().coerceAtLeast(1)
        val decaySamples = (0.005 * sampleRate).toInt().coerceAtLeast(1)

        for (i in 0 until sampleCount) {
            val angle = 2.0 * PI * i * frequencyHz / sampleRate
            var envelope = 1.0
            if (i < attackSamples) {
                envelope = i.toDouble() / attackSamples
            } else if (i > sampleCount - decaySamples) {
                envelope = (sampleCount - 1 - i).toDouble() / decaySamples
            }
            val sampleVal = (sin(angle) * Short.MAX_VALUE * volume * envelope).toInt()
            shorts[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return shortsToBytes(shorts)
    }

    private fun synthesizeChirp(
        startFreqHz: Double,
        endFreqHz: Double,
        durationSec: Double,
        sampleRate: Int,
        volume: Float = 0.8f
    ): ByteArray {
        val sampleCount = (durationSec * sampleRate).toInt().coerceAtLeast(1)
        val shorts = ShortArray(sampleCount)
        val attackSamples = (0.004 * sampleRate).toInt().coerceAtLeast(1)
        val decaySamples = (0.004 * sampleRate).toInt().coerceAtLeast(1)

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / sampleRate
            // Phase for linear frequency sweep: phi(t) = 2*pi*(f0*t + ((f1-f0)/(2*T))*t^2)
            val freqSlope = (endFreqHz - startFreqHz) / (2.0 * durationSec)
            val phase = 2.0 * PI * (startFreqHz * t + freqSlope * t * t)

            var envelope = 1.0
            if (i < attackSamples) {
                envelope = i.toDouble() / attackSamples
            } else if (i > sampleCount - decaySamples) {
                envelope = (sampleCount - 1 - i).toDouble() / decaySamples
            }
            val sampleVal = (sin(phase) * Short.MAX_VALUE * volume * envelope).toInt()
            shorts[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return shortsToBytes(shorts)
    }

    private fun synthesizeSquelchTail(
        durationSec: Double,
        sampleRate: Int,
        volume: Float = 0.7f
    ): ByteArray {
        val sampleCount = (durationSec * sampleRate).toInt().coerceAtLeast(1)
        val shorts = ShortArray(sampleCount)
        val random = Random(42)
        val decaySamples = (durationSec * sampleRate * 0.7).toInt().coerceAtLeast(1)
        val ctcssFreq = 134.4 // Standard CTCSS reverse burst frequency

        for (i in 0 until sampleCount) {
            val ctcssAngle = 2.0 * PI * i * ctcssFreq / sampleRate
            val ctcssVal = sin(ctcssAngle) * 0.4
            val noiseVal = (random.nextDouble() * 2.0 - 1.0) * 0.6

            var envelope = 1.0
            val decayStart = sampleCount - decaySamples
            if (i > decayStart) {
                envelope = (sampleCount - 1 - i).toDouble() / decaySamples
            }
            val combined = (ctcssVal + noiseVal) * Short.MAX_VALUE * volume * envelope
            shorts[i] = combined.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return shortsToBytes(shorts)
    }

    private fun concatPcm(a: ByteArray, b: ByteArray): ByteArray {
        val out = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asShortBuffer().put(shorts)
        return buffer.array()
    }
}
