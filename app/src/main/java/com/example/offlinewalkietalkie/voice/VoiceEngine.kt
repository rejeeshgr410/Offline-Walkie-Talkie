package com.example.offlinewalkietalkie.voice

import android.content.Context
import android.util.Log
import com.example.offlinewalkietalkie.protocol.AudioCodecType
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
    private var codec: AudioCodec = AmrNbCodec(),
    context: Context? = null
) {
    private val TAG = "TL/Voice"
    val routeManager: AudioRouteManager? = context?.let { AudioRouteManager(it) }

    val sampleRate: Int = if (codec is AmrNbCodec || codec is Codec2AudioCodec) 8000 else AudioCapture.SAMPLE_RATE
    private val capture = AudioCapture(sampleRate = sampleRate)
    private val playback = AudioPlayback(sampleRate = sampleRate)
    
    // Vivo GainProc mirror: +6 dB boost & peak hard limiter to prevent clipping
    val audioLimiter = AudioLimiter(sampleRate = sampleRate, preGain = 2.0f)

    // Real-Time Adaptive Bitrate QoS State Machine
    val qosManager = AdaptiveQosManager().apply {
        onCodecChangeRequested = { targetMode ->
            handleAdaptiveCodecChange(targetMode)
        }
    }

    private fun handleAdaptiveCodecChange(targetMode: Int) {
        Log.i(TAG, "Adaptive QoS reconfiguring audio codec for mode $targetMode")
        if (codec is Codec2AudioCodec) {
            codec = Codec2AudioCodec(mode = targetMode)
        } else if (codec is AmrNbCodec) {
            val targetBitrate = when (targetMode) {
                0 -> 4750
                1 -> 5150
                2 -> 5900
                3 -> 6700
                4 -> 7400
                5 -> 7950
                6 -> 10200
                else -> 12200
            }
            codec = AmrNbCodec(sampleRate = 8000, bitrate = targetBitrate)
        }
    }
    
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

    var onRawPcmCaptured: ((ByteArray) -> Unit)? = null
    var onRawPcmPlayed: ((ByteArray) -> Unit)? = null

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
            var pendingCodec2Pcm: ShortArray? = null
            capture.captureLoop { pcmFrame ->
                try {
                    onRawPcmCaptured?.invoke(pcmFrame)
                    val shorts = bytesToShorts(pcmFrame)

                    // Apply Vivo GainProc pre-amp boost (+6 dB) and peak hard-limiter
                    audioLimiter.process(shorts)

                    // P1-01 Fix: Codec 2 mode 1600 needs 320 samples (40ms). Accumulate two 20ms (160 sample)
                    // captures before encoding to eliminate half-frame silence padding.
                    val pcmToEncode = if (codec is Codec2AudioCodec) {
                        if (pendingCodec2Pcm == null) {
                            pendingCodec2Pcm = shorts
                            return@captureLoop
                        }
                        val combined = ShortArray(pendingCodec2Pcm!!.size + shorts.size)
                        System.arraycopy(pendingCodec2Pcm!!, 0, combined, 0, pendingCodec2Pcm!!.size)
                        System.arraycopy(shorts, 0, combined, pendingCodec2Pcm!!.size, shorts.size)
                        pendingCodec2Pcm = null
                        combined
                    } else {
                        shorts
                    }

                    val encoded = codec.encode(pcmToEncode)
                    if (encoded.isNotEmpty()) {
                        sequenceNumber++
                        frameCount++
                        if (frameCount == 1 || frameCount % 50 == 0) {
                            Log.i(TAG, "captured+encoded frame $frameCount (${encoded.size} bytes, codec=${codec::class.simpleName})")
                        }
                        if (isLoopback) {
                            val codecType = if (codec is Codec2AudioCodec) AudioCodecType.CODEC2 else AudioCodecType.AMR_NB
                            handleIncomingFrame("local", sequenceNumber, encoded, codecType, System.currentTimeMillis())
                        } else {
                            onEncodedFrame(sequenceNumber, encoded)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Audio encode failed: ${e.message}")
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

    /** Mutes live received playback while keeping decoded frames available for history. */
    @Volatile
    var isRealtimeMuted: Boolean = false

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
                    val mixedBytes = shortsToBytes(mixFrames(due))
                    if (!isRealtimeMuted) playback.playFrame(mixedBytes)
                    onRawPcmPlayed?.invoke(mixedBytes)
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
    
    fun setSurvivalCodecMode(enabled: Boolean) {
        codec = if (enabled) Codec2AudioCodec() else AmrNbCodec()
        jitterBuffers.clear()
        Log.i(TAG, "Switched voice codec: ${codec::class.simpleName} (survivalMode=$enabled)")
    }

    fun isSurvivalCodecActive(): Boolean = codec is Codec2AudioCodec

    fun handleIncomingFrame(
        peerId: String,
        sequence: Int,
        encodedAudio: ByteArray,
        codecType: AudioCodecType = AudioCodecType.AMR_NB,
        senderTimestampMs: Long = 0L
    ) {
        startPlayback()
        val expectedCodecClass = when (codecType) {
            AudioCodecType.CODEC2 -> Codec2AudioCodec::class
            AudioCodecType.OPUS -> OpusCodec::class
            else -> AmrNbCodec::class
        }
        val currentJb = jitterBuffers[peerId]
        val jb = if (currentJb != null && currentJb.codec::class == expectedCodecClass) {
            currentJb
        } else {
            val decoder: AudioCodec = when (codecType) {
                AudioCodecType.CODEC2 -> Codec2AudioCodec()
                AudioCodecType.OPUS -> OpusCodec()
                else -> AmrNbCodec()
            }
            currentJb?.reset()
            val newJb = JitterBuffer(decoder)
            jitterBuffers[peerId] = newJb
            newJb
        }
        jb.push(sequence, encodedAudio, senderTimestampMs)
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
        setAudioRoute(if (enabled) AudioRoute.SPEAKER else AudioRoute.EARPIECE)
    }

    fun setAudioRoute(route: AudioRoute) {
        val useSpeaker = route == AudioRoute.SPEAKER
        speakerphoneEnabled = useSpeaker
        routeManager?.setAudioRoute(route)
        playback.start(useSpeaker = useSpeaker)
        routeManager?.ensureVolume(useSpeaker)
        Log.i(TAG, "setAudioRoute: active route=$route (useSpeaker=$useSpeaker)")
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
