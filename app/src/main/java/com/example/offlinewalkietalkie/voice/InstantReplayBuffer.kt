package com.example.offlinewalkietalkie.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Section 14 / Tactical 10-Second Instant Circular Replay Buffer.
 *
 * Keeps a real-time, in-memory circular ring buffer of the most recent incoming
 * PCM audio (up to 10 seconds = 160,000 bytes @ 8000 Hz 16-bit mono).
 *
 * Guarantees:
 * - Zero flash disk I/O for instant replay (survives offline field conditions).
 * - Instant review of missed or static-distorted radio transmissions.
 * - Secure in-memory eviction: wiped on channel switch or manual reset.
 */
class InstantReplayBuffer(
    val maxDurationSeconds: Int = 10,
    val sampleRate: Int = 8000,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "TL/InstantReplay"
        private const val BYTES_PER_SAMPLE = 2 // 16-bit PCM mono
    }

    private val capacityBytes = maxDurationSeconds * sampleRate * BYTES_PER_SAMPLE
    private val ringBuffer = ByteArray(capacityBytes)
    private var writeHead = 0
    private var totalBytesStored = 0

    private val lock = Any()

    private val _hasAudio = MutableStateFlow(false)
    val hasAudio: StateFlow<Boolean> = _hasAudio.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _bufferedDurationMs = MutableStateFlow(0L)
    val bufferedDurationMs: StateFlow<Long> = _bufferedDurationMs.asStateFlow()

    private var activeReplayTrack: AudioTrack? = null
    private var replayJob: Job? = null

    /**
     * Appends freshly decoded incoming PCM bytes to circular buffer.
     */
    fun write(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return
        synchronized(lock) {
            for (byte in pcmData) {
                ringBuffer[writeHead] = byte
                writeHead = (writeHead + 1) % capacityBytes
                if (totalBytesStored < capacityBytes) {
                    totalBytesStored++
                }
            }
            val ms = (totalBytesStored * 1000L) / (sampleRate * BYTES_PER_SAMPLE)
            _bufferedDurationMs.value = ms
            if (totalBytesStored >= (sampleRate * BYTES_PER_SAMPLE / 4)) { // at least 250ms
                _hasAudio.value = true
            }
        }
    }

    /**
     * Extracts an ordered snapshot of up to the last 10 seconds of audio.
     */
    fun getSnapshot(): ByteArray {
        synchronized(lock) {
            if (totalBytesStored == 0) return ByteArray(0)
            val snapshot = ByteArray(totalBytesStored)
            if (totalBytesStored < capacityBytes) {
                // Buffer hasn't wrapped yet: [0 .. writeHead-1]
                System.arraycopy(ringBuffer, 0, snapshot, 0, totalBytesStored)
            } else {
                // Buffer has wrapped: older data starts at writeHead
                val tailLen = capacityBytes - writeHead
                System.arraycopy(ringBuffer, writeHead, snapshot, 0, tailLen)
                System.arraycopy(ringBuffer, 0, snapshot, tailLen, writeHead)
            }
            return snapshot
        }
    }

    /**
     * Replays the stored circular buffer audio immediately through AudioTrack.
     */
    fun playLatest(onFinished: () -> Unit = {}) {
        Log.i(TAG, "playLatest called! totalBytesStored=$totalBytesStored")
        stop()

        val pcm = getSnapshot()
        if (pcm.isEmpty()) {
            Log.w(TAG, "No audio buffered for instant replay")
            return
        }

        replayJob = scope.launch {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = minBuf.coerceAtLeast(sampleRate * 2)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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

                activeReplayTrack = track
                _isPlaying.value = true

                // Anti-pop VolumeShaper ramp
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val config = android.media.VolumeShaper.Configuration.Builder()
                            .setDuration(20)
                            .setCurve(floatArrayOf(0.0f, 1.0f), floatArrayOf(0.0f, 1.0f))
                            .setInterpolatorType(android.media.VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                            .build()
                        track.createVolumeShaper(config).apply(android.media.VolumeShaper.Operation.PLAY)
                    } catch (e: Throwable) {
                        Log.d(TAG, "VolumeShaper skipped: ${e.message}")
                    }
                }

                track.play()
                track.write(pcm, 0, pcm.size)

                val playDurationMs = (pcm.size * 1000L) / (sampleRate * BYTES_PER_SAMPLE)
                Log.i(TAG, "Playing instant replay (${playDurationMs}ms, ${pcm.size} bytes)")
                kotlinx.coroutines.delay(playDurationMs + 80)

                stop()
                onFinished()
            } catch (e: Exception) {
                Log.e(TAG, "Failed instant replay playback", e)
                stop()
            }
        }
    }

    /**
     * Stops active instant replay playback immediately.
     */
    fun stop() {
        replayJob?.cancel()
        replayJob = null
        _isPlaying.value = false
        try {
            activeReplayTrack?.stop()
            activeReplayTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        activeReplayTrack = null
    }

    /**
     * Wipes audio memory cleanly.
     */
    fun clear() {
        stop()
        synchronized(lock) {
            ringBuffer.fill(0)
            writeHead = 0
            totalBytesStored = 0
            _hasAudio.value = false
            _bufferedDurationMs.value = 0L
        }
        Log.i(TAG, "Instant replay buffer wiped from memory")
    }
}
