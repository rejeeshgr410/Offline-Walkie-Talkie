package com.example.offlinewalkietalkie.sos

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Synthesizes an alternating dual-frequency emergency alert siren (850 Hz / 1100 Hz)
 * directly in PCM. Guarantees 100% offline audible distress alerting without audio asset files.
 */
class EmergencyAlarmPlayer {

    companion object {
        private const val TAG = "TL/EmergencyAlarm"
        private const val SAMPLE_RATE = 16000
    }

    private var audioTrack: AudioTrack? = null
    private var alarmJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var isPlaying = false

    @Synchronized
    fun startAlarm() {
        if (isPlaying) return
        isPlaying = true

        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBuf.coerceAtLeast(SAMPLE_RATE)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            alarmJob = scope.launch {
                // Generate 300ms chunks: 850Hz tone then 1100Hz tone
                val tone1 = generateTonePcm(850.0, 0.3)
                val tone2 = generateTonePcm(1100.0, 0.3)

                while (isActive && isPlaying) {
                    audioTrack?.write(tone1, 0, tone1.size)
                    audioTrack?.write(tone2, 0, tone2.size)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start emergency alarm", e)
            isPlaying = false
        }
    }

    @Synchronized
    fun stopAlarm() {
        if (!isPlaying) return
        isPlaying = false
        alarmJob?.cancel()
        alarmJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping emergency alarm audioTrack", e)
        }
        audioTrack = null
    }

    private fun generateTonePcm(frequency: Double, durationSec: Double): ShortArray {
        val count = (SAMPLE_RATE * durationSec).toInt()
        val buffer = ShortArray(count)
        for (i in 0 until count) {
            val angle = 2.0 * Math.PI * i / (SAMPLE_RATE / frequency)
            buffer[i] = (sin(angle) * 28000).toInt().toShort() // High volume tone
        }
        return buffer
    }
}
