package com.example.offlinewalkietalkie.history

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class VoiceMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val channelName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val isIncoming: Boolean,
    val filePath: String,
    val sampleRate: Int = 8000
)

/**
 * Manages offline recording, disk persistence, and 1-tap playback replay of voice transmissions.
 * Retains the last 50 messages with automatic cleanup.
 */
class VoiceHistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "TL/VoiceHistory"
        private const val MAX_SAVED_MESSAGES = 50
    }

    private val historyDir = File(context.filesDir, "voice_history").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _history = MutableStateFlow<List<VoiceMessage>>(emptyList())
    val history = _history.asStateFlow()

    private var activePlaybackTrack: AudioTrack? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentlyPlayingId = MutableStateFlow<String?>(null)
    val currentlyPlayingId = _currentlyPlayingId.asStateFlow()

    suspend fun saveTransmission(
        senderId: String,
        senderName: String,
        channelName: String,
        isIncoming: Boolean,
        durationMs: Long,
        audioPcmBytes: ByteArray,
        sampleRate: Int = 8000
    ): VoiceMessage? = withContext(Dispatchers.IO) {
        if (audioPcmBytes.isEmpty()) return@withContext null

        try {
            val fileName = "rec_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.pcm"
            val file = File(historyDir, fileName)
            FileOutputStream(file).use { it.write(audioPcmBytes) }

            val message = VoiceMessage(
                senderId = senderId,
                senderName = senderName,
                channelName = channelName,
                durationMs = durationMs.coerceAtLeast(1000L),
                isIncoming = isIncoming,
                filePath = file.absolutePath,
                sampleRate = sampleRate
            )

            val updated = mutableListOf(message)
            updated.addAll(_history.value)

            // Trim to max 50
            while (updated.size > MAX_SAVED_MESSAGES) {
                val removed = updated.removeLast()
                File(removed.filePath).delete()
            }

            _history.value = updated
            Log.i(TAG, "Saved voice transmission from $senderName (${durationMs}ms, ${audioPcmBytes.size}B)")
            message
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save voice transmission", e)
            null
        }
    }

    fun replayMessage(message: VoiceMessage, onCompletion: (() -> Unit)? = null) {
        stopReplay()

        val file = File(message.filePath)
        if (!file.exists()) {
            Log.w(TAG, "Cannot replay: audio file not found at ${message.filePath}")
            return
        }

        scope.launch {
            try {
                val audioData = FileInputStream(file).use { it.readBytes() }
                if (audioData.isEmpty()) return@launch

                val minBuf = AudioTrack.getMinBufferSize(
                    message.sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = minBuf.coerceAtLeast(message.sampleRate * 2)

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
                            .setSampleRate(message.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                activePlaybackTrack = track
                _currentlyPlayingId.value = message.id
                _isPlaying.value = true

                // Anti-pop VolumeShaper ramp
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val shaperConfig = android.media.VolumeShaper.Configuration.Builder()
                            .setDuration(25)
                            .setCurve(floatArrayOf(0.0f, 1.0f), floatArrayOf(0.0f, 1.0f))
                            .setInterpolatorType(android.media.VolumeShaper.Configuration.INTERPOLATOR_TYPE_CUBIC)
                            .build()
                        track.createVolumeShaper(shaperConfig).apply(android.media.VolumeShaper.Operation.PLAY)
                    } catch (e: Exception) {
                        Log.d(TAG, "VolumeShaper optional init: ${e.message}")
                    }
                }

                track.play()
                track.write(audioData, 0, audioData.size)

                // Wait for playback to flush
                val playDurationMs = (audioData.size * 1000L) / (message.sampleRate * 2)
                kotlinx.coroutines.delay(playDurationMs + 100)

                stopReplay()
                onCompletion?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error replaying voice message", e)
                stopReplay()
            }
        }
    }

    fun replayLastMessage() {
        val lastIncoming = _history.value.firstOrNull { it.isIncoming } ?: _history.value.firstOrNull()
        if (lastIncoming != null) {
            replayMessage(lastIncoming)
        }
    }

    fun stopReplay() {
        _isPlaying.value = false
        _currentlyPlayingId.value = null
        try {
            activePlaybackTrack?.stop()
            activePlaybackTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        activePlaybackTrack = null
    }

    fun clearHistory() {
        stopReplay()
        _history.value.forEach { File(it.filePath).delete() }
        _history.value = emptyList()
    }
}
