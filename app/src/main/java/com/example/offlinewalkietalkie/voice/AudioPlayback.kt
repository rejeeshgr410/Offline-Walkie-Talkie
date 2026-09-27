package com.example.offlinewalkietalkie.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

class AudioPlayback {
    companion object {
        private const val TAG = "TL/AudioPlayback"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * The route toggle runs on the UI thread while the playback loop writes on an IO thread.
     * Without this guard the toggle can release the track mid-write, which is undefined
     * behaviour in the native layer.
     */
    private val lock = Any()

    private var audioTrack: AudioTrack? = null
    private val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private var frameCount = 0
    private var skippedFrameCount = 0
    private var currentUseSpeaker: Boolean? = null

    fun start(useSpeaker: Boolean = true) {
        synchronized(lock) {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING && currentUseSpeaker == useSpeaker) {
                return@synchronized
            }
            releaseLocked()
            try {
                val safeMin = if (minBufferSize > 0) minBufferSize else 3840
                val bufferSize = maxOf(safeMin, 960 * 2 * 2)

                // Samsung and some other OEMs ignore setCommunicationDevice for speaker routing
                // when the track uses USAGE_VOICE_COMMUNICATION, so speaker mode goes out as media.
                val usage = if (useSpeaker) {
                    AudioAttributes.USAGE_MEDIA
                } else {
                    AudioAttributes.USAGE_VOICE_COMMUNICATION
                }
                val contentType = if (useSpeaker) {
                    AudioAttributes.CONTENT_TYPE_MUSIC
                } else {
                    AudioAttributes.CONTENT_TYPE_SPEECH
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(contentType)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .setEncoding(AUDIO_FORMAT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                audioTrack = track
                frameCount = 0
                skippedFrameCount = 0
                currentUseSpeaker = useSpeaker
                Log.i(
                    TAG,
                    "AudioTrack started: useSpeaker=$useSpeaker usage=${if (useSpeaker) "MEDIA" else "VOICE_COMMUNICATION"} " +
                        "state=${track.state} playState=${track.playState} bufferSize=$bufferSize sessionId=${track.audioSessionId}"
                )
            } catch (e: Throwable) {
                Log.e(TAG, "AudioTrack start FAILED", e)
            }
        }
    }

    fun playFrame(pcmBytes: ByteArray) {
        synchronized(lock) {
            val track = audioTrack ?: return@synchronized
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                skippedFrameCount++
                if (skippedFrameCount == 1 || skippedFrameCount % 100 == 0) {
                    Log.w(TAG, "playFrame skipped x$skippedFrameCount: playState=${track.playState}")
                }
                return@synchronized
            }
            try {
                val written = track.write(pcmBytes, 0, pcmBytes.size)
                frameCount++
                if (frameCount == 1 || frameCount % 50 == 0) {
                    Log.i(TAG, "playFrame #$frameCount: wrote $written/${pcmBytes.size} bytes, maxAmp=${peakAmplitude(pcmBytes)}")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "playFrame failed", e)
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            Log.i(TAG, "stop (played $frameCount frames)")
            releaseLocked()
            frameCount = 0
            skippedFrameCount = 0
        }
    }

    private fun releaseLocked() {
        val track = audioTrack ?: return
        audioTrack = null
        currentUseSpeaker = null
        try {
            track.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "AudioTrack stop error: ${e.message}")
        }
        try {
            track.release()
        } catch (e: Throwable) {
        }
    }

    private fun peakAmplitude(pcmBytes: ByteArray): Int {
        var peak = 0
        for (i in 0 until pcmBytes.size - 1 step 2) {
            val sample = (pcmBytes[i].toInt() and 0xFF or (pcmBytes[i + 1].toInt() shl 8)).toShort().toInt()
            val magnitude = if (sample < 0) -sample else sample
            if (magnitude > peak) peak = magnitude
        }
        return peak
    }
}
