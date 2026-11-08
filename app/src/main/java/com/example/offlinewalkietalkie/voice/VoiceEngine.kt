package com.example.offlinewalkietalkie.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 6 VoiceEngine.
 * Orchestrates AudioCapture, AudioCodec (OpusCodec / AmrNbCodec), AudioPlayback, Adaptive JitterBuffer,
 * and AudioRouteManager (audio focus & speakerphone routing).
 */
class VoiceEngine(
    private val codec: AudioCodec = OpusCodec(),
    context: Context? = null
) {
    private val TAG = "TL/Voice"
    val routeManager: AudioRouteManager? = context?.let { AudioRouteManager(it) }

    val sampleRate: Int = if (codec is AmrNbCodec) codec.sampleRate else AudioCapture.SAMPLE_RATE
    private val capture = AudioCapture(sampleRate = sampleRate)
    private val playback = AudioPlayback(sampleRate = sampleRate)
    
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    var isLoopback = false
        set(value) {
            field = value
            if (value) startPlayback()
        }

    private val jitterBuffers = ConcurrentHashMap<String, JitterBuffer>()
    private var sequenceNumber = 0

    init {
        routeManager?.onInterruptedByCall = {
            stopRecording()
            stopPlayback()
        }
    }

    fun startRecording(onEncodedFrame: (sequence: Int, frame: ByteArray) -> Unit) {
        routeManager?.requestAudioFocus()
        capture.start { }
        captureJob = coroutineScope.launch {
            var frameCount = 0
            capture.captureLoop { pcmFrame ->
                try {
                    val encoded = codec.encode(bytesToShorts(pcmFrame))
                    if (encoded.isNotEmpty()) {
                        sequenceNumber++
                        frameCount++
                        if (frameCount == 1 || frameCount % 50 == 0) {
                            Log.i(TAG, "captured+encoded frame $frameCount (${encoded.size} bytes)")
                        }
                        if (isLoopback) {
                            handleIncomingFrame("local", sequenceNumber, encoded, System.currentTimeMillis())
                        } else {
                            onEncodedFrame(sequenceNumber, encoded)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Opus encode failed: ${e.message}")
                }
            }
            Log.i(TAG, "capture loop ended (frames=$frameCount)")
        }
    }

    fun stopRecording() {
        captureJob?.cancel()
        capture.stop()
    }

    var speakerphoneEnabled: Boolean = true

    fun startPlayback() {
        if (playbackJob?.isActive == true) return
        routeManager?.requestAudioFocus()
        playback.start(useSpeaker = speakerphoneEnabled)
        routeManager?.setSpeakerphone(speakerphoneEnabled)
        routeManager?.ensureVolume(speakerphoneEnabled)
        Log.i(TAG, "startPlayback: speakerphone=$speakerphoneEnabled")
        playbackJob = coroutineScope.launch {
            var popCount = 0
            var nextWakeTime = System.currentTimeMillis()
            while (isActive) {
                val due = ArrayList<ShortArray>(2)
                for (peer in jitterBuffers.keys.toList()) {
                    val pcm = jitterBuffers[peer]?.pop() ?: continue
                    popCount++
                    if (popCount == 1 || popCount % 100 == 0) {
                        Log.i(TAG, "playback pop #$popCount from $peer (${pcm.size} samples)")
                    }
                    due.add(pcm)
                }
                // Concurrent speakers have to be summed into one frame. Writing them back to
                // back would play them sequentially and drain the track at N times real time.
                if (due.isNotEmpty()) {
                    playback.playFrame(shortsToBytes(mixFrames(due)))
                }

                // Fixed-timestep loop for exactly 20ms intervals
                nextWakeTime += 20
                val now = System.currentTimeMillis()
                val sleepTime = nextWakeTime - now
                if (sleepTime > 0) {
                    delay(sleepTime)
                } else {
                    // If we lagged too far behind, reset the clock to prevent rapid fire
                    nextWakeTime = now
                }
            }
        }
    }
    
    fun handleIncomingFrame(
        peerId: String,
        sequence: Int,
        encodedOpus: ByteArray,
        senderTimestampMs: Long = 0L
    ) {
        startPlayback()
        val jb = jitterBuffers.getOrPut(peerId) { 
            JitterBuffer(if (codec is AmrNbCodec) AmrNbCodec() else OpusCodec()) 
        }
        jb.push(sequence, encodedOpus, senderTimestampMs)
    }

    fun getJitterBuffer(peerId: String): JitterBuffer? = jitterBuffers[peerId]

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        playback.stop()
        routeManager?.abandonAudioFocus()
        jitterBuffers.values.forEach { it.reset() }
        jitterBuffers.clear()
    }

    fun setSpeakerphone(enabled: Boolean) {
        speakerphoneEnabled = enabled
        routeManager?.setSpeakerphone(enabled)
        // Restart AudioTrack with the correct usage attribute for the new route
        playback.start(useSpeaker = enabled)
        routeManager?.ensureVolume(enabled)
        Log.i(TAG, "setSpeakerphone: restarted AudioTrack with useSpeaker=$enabled")
    }

    private fun mixFrames(frames: List<ShortArray>): ShortArray {
        if (frames.size == 1) return frames[0]
        val mixed = ShortArray(frames.maxOf { it.size })
        for (frame in frames) {
            for (i in frame.indices) {
                val sum = mixed[i] + frame[i]
                mixed[i] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
        return mixed
    }

    // Utilities to convert ByteArray <-> ShortArray for PCM16
    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
        return bytes
    }
}
