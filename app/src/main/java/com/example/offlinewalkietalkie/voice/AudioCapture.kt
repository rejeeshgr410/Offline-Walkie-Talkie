package com.example.offlinewalkietalkie.voice

import android.annotation.SuppressLint
import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Section 6 Audio Capture with VOICE_COMMUNICATION, configurable sample rate (8kHz / 48kHz mono PCM 16-bit),
 * hardware AcousticEchoCanceler, NoiseSuppressor, AutomaticGainControl attachments,
 * and real-time software AudioLimiter (AGC / anti-clipping).
 */
class AudioCapture(
    val sampleRate: Int = SAMPLE_RATE
) {
    companion object {
        const val TAG = "TL/AudioCapture"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    private val limiter = AudioLimiter(sampleRate = sampleRate)

    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
    // 20ms frames: sampleRate * 0.02 sec samples
    private val samplesPerFrame = (sampleRate * 0.02).toInt()
    private val frameSize = samplesPerFrame * 2

    @SuppressLint("MissingPermission")
    fun start(onFrame: (ByteArray) -> Unit) {
        val bufferSize = maxOf(minBufferSize * 4, frameSize * 10)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord failed to initialize (state=${audioRecord?.state}, minBuffer=$minBufferSize)")
            audioRecord?.release()
            audioRecord = null
            return
        }
        val savedSession = audioRecord?.audioSessionId ?: 0
        Log.i(TAG, "AudioRecord initialized, session=$savedSession, buffer=$bufferSize")

        val sessionId = audioRecord?.audioSessionId ?: 0
        if (sessionId != 0) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Throwable) {}

            try {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Throwable) {}

            try {
                if (AutomaticGainControl.isAvailable()) {
                    gainControl = AutomaticGainControl.create(sessionId)?.apply {
                        enabled = true
                    }
                }
            } catch (e: Throwable) {}
        }

        audioRecord?.startRecording()
    }

    suspend fun captureLoop(onFrame: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val readBuffer = ByteArray(frameSize)
        var accumulationBuffer = ByteArray(0)
        limiter.reset()
        
        while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = audioRecord?.read(readBuffer, 0, frameSize, AudioRecord.READ_BLOCKING) ?: 0
            if (read > 0) {
                accumulationBuffer += readBuffer.copyOfRange(0, read)
                while (accumulationBuffer.size >= frameSize) {
                    val frame = accumulationBuffer.copyOfRange(0, frameSize)
                    accumulationBuffer = accumulationBuffer.copyOfRange(frameSize, accumulationBuffer.size)
                    
                    // Apply AudioLimiter in-place to prevent clipping and normalize gain
                    val shorts = ShortArray(frame.size / 2)
                    ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    limiter.process(shorts)
                    ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
                    
                    onFrame(frame)
                }
            }
        }
    }

    fun stop() {
        try {
            audioRecord?.stop()
        } catch (e: Throwable) {}

        try {
            echoCanceler?.release()
        } catch (e: Throwable) {}
        try {
            noiseSuppressor?.release()
        } catch (e: Throwable) {}
        try {
            gainControl?.release()
        } catch (e: Throwable) {}
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null

        audioRecord?.release()
        audioRecord = null
    }
}
