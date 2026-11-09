package com.example.offlinewalkietalkie.voice

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Section 6 & Section 19.14 Adaptive Jitter Buffer.
 *
 * Requirements:
 * - Target playout buffer: 40 ms (2 frames), minimum 40 ms (2 frames), maximum 200 ms (10 frames).
 * - Stale-voice cutoff: packets older than 200 ms on the receiver's clock are discarded.
 * - Missing frames are decoded using Opus PLC (Packet Loss Concealment).
 * - RFC 3550 inter-arrival jitter estimation and packet loss metrics for live link telemetry.
 */
data class JitterBufferMetrics(
    val jitterMs: Double = 0.0,
    val packetLossCount: Int = 0,
    val lateDroppedCount: Int = 0,
    val bufferDepthMs: Int = 0
)

class JitterBuffer(
    private val codec: AudioCodec,
    val targetDelayFrames: Int = TARGET_DELAY_FRAMES,
    val maxDelayFrames: Int = MAX_DELAY_FRAMES
) {
    companion object {
        private const val TAG = "TL/JitterBuffer"
        const val FRAME_DURATION_MS = 20L
        const val TARGET_DELAY_FRAMES = 2 // 40 ms
        // 40 ms. Starting playout on a single frame leaves no slack, so any jitter on the
        // link underruns the track immediately and the audio chops.
        const val MIN_DELAY_FRAMES = 2
        const val MAX_DELAY_FRAMES = 10  // 200 ms max playout deadline
        const val STALE_VOICE_CUTOFF_MS = 200L // Stale cutoff on receiver clock (§19.14)
    }

    private data class BufferedPacket(
        val pcm: ShortArray,
        val arrivalEpochMs: Long,
        val senderTimestampMs: Long
    )

    // Key: Sequence number -> BufferedPacket
    private val buffer = ConcurrentSkipListMap<Int, BufferedPacket>()

    /**
     * push() decodes on the network thread while pop() runs PLC and reset() recreates the
     * decoder on the playback thread. The native Opus decoder is stateful and not thread-safe.
     */
    private val codecLock = Any()

    private var lastPlayedSequence = -1
    private var isBuffering = true
    private var plcConsecutiveFrames = 0
    private var emptyPopCount = 0 // track consecutive empty pops before re-buffering

    // RFC 3550 Jitter calculation state
    private var lastArrivalEpochMs = 0L
    private var lastSenderTimestampMs = 0L
    private var estimatedJitterMs = 0.0
    private var totalPacketsReceived = 0
    private var totalPacketsLost = 0
    private var totalLatePacketsDropped = 0

    private val _metrics = MutableStateFlow(JitterBufferMetrics())
    val metrics: StateFlow<JitterBufferMetrics> = _metrics.asStateFlow()

    /**
     * Push incoming encoded frame into jitter buffer.
     */
    fun push(sequence: Int, encodedOpus: ByteArray, senderTimestampMs: Long = 0L) {
        val now = System.currentTimeMillis()

        // 1. Drop packets older than last played sequence
        if (lastPlayedSequence != -1 && sequence <= lastPlayedSequence) {
            totalLatePacketsDropped++
            updateMetrics()
            return
        }

        // 2. Decode packet (Opus or PCM)
        val pcm = try {
            synchronized(codecLock) { codec.decode(encodedOpus) }
        } catch (e: Throwable) {
            null
        }

        if (pcm == null || pcm.isEmpty()) {
            return
        }

        // P1-01 Remediation: If codec produced 40ms frame (320 samples for Codec 2 mode 1600),
        // cleanly split into two 20ms frames (160 samples each) to maintain uniform 20ms playback
        // pacing across all codecs without timing jitter or drift.
        if (pcm.size == 320) {
            val half1 = pcm.copyOfRange(0, 160)
            val half2 = pcm.copyOfRange(160, 320)
            val subSeq1 = sequence * 2
            val subSeq2 = sequence * 2 + 1
            buffer[subSeq1] = BufferedPacket(half1, now, senderTimestampMs)
            buffer[subSeq2] = BufferedPacket(half2, now, senderTimestampMs + 20)
        } else {
            buffer[sequence] = BufferedPacket(pcm, now, senderTimestampMs)
        }

        // 3. RFC 3550 Inter-arrival jitter update
        if (lastArrivalEpochMs != 0L && senderTimestampMs != 0L && lastSenderTimestampMs != 0L) {
            val transitDiff = (now - lastArrivalEpochMs) - (senderTimestampMs - lastSenderTimestampMs)
            val d = Math.abs(transitDiff).toDouble()
            estimatedJitterMs += (d - estimatedJitterMs) / 16.0
        }
        lastArrivalEpochMs = now
        lastSenderTimestampMs = senderTimestampMs
        totalPacketsReceived++

        buffer[sequence] = BufferedPacket(pcm, now, senderTimestampMs)

        // Drop oldest packets if exceeding maximum playout deadline (200 ms)
        while (buffer.size > maxDelayFrames) {
            buffer.pollFirstEntry()
            totalLatePacketsDropped++
        }

        // Start playing as soon as we have MIN_DELAY_FRAMES
        if (isBuffering && buffer.size >= MIN_DELAY_FRAMES) {
            isBuffering = false
            emptyPopCount = 0
            Log.i(TAG, "buffering -> playing (buffer=${buffer.size} frames)")
        }

        updateMetrics()
    }

    /**
     * Pop next 20ms frame for playout.
     * Returns PCM samples or null if buffer is underrun.
     */
    fun pop(): ShortArray? {
        val now = System.currentTimeMillis()

        if (isBuffering) {
            return null
        }

        if (buffer.isEmpty()) {
            // Don't immediately go back to buffering — allow a few empty pops
            // to ride out brief network jitter without introducing a re-buffer gap
            emptyPopCount++
            if (emptyPopCount > 5) { // 5 * 20ms = 100ms of silence before re-buffering
                Log.i(TAG, "playing -> buffering (empty for ${emptyPopCount * FRAME_DURATION_MS}ms)")
                isBuffering = true
                plcConsecutiveFrames = 0
                emptyPopCount = 0
                updateMetrics()
            }
            return null
        }

        emptyPopCount = 0

        val nextSeq = if (lastPlayedSequence == -1) {
            buffer.firstKey()
        } else {
            lastPlayedSequence + 1
        }

        val packet = buffer.remove(nextSeq)
        if (packet != null) {
            // Check stale cutoff on receiver clock (§19.14)
            if (now - packet.arrivalEpochMs > STALE_VOICE_CUTOFF_MS) {
                totalLatePacketsDropped++
                lastPlayedSequence = nextSeq
                plcConsecutiveFrames = 0
                updateMetrics()
                return pop() // Drop stale frame and try next
            }

            lastPlayedSequence = nextSeq
            plcConsecutiveFrames = 0
            updateMetrics()
            return packet.pcm
        } else {
            // Gap detected: Attempt Packet Loss Concealment (PLC) for 1-2 frames
            if (plcConsecutiveFrames < 2) {
                plcConsecutiveFrames++
                totalPacketsLost++
                lastPlayedSequence = nextSeq
                updateMetrics()
                return synchronized(codecLock) { codec.decodeLostFrame() }
            }

            // Exceeded PLC budget: jump to next available sequence in buffer
            val nextAvailable = buffer.ceilingKey(nextSeq)
            if (nextAvailable != null && nextAvailable > nextSeq) {
                totalPacketsLost += (nextAvailable - nextSeq)
                lastPlayedSequence = nextAvailable
                plcConsecutiveFrames = 0
                val skippedPacket = buffer.remove(nextAvailable)
                updateMetrics()
                return skippedPacket?.pcm
            } else {
                // Buffer truly empty now
                emptyPopCount++
                if (emptyPopCount > 5) {
                    isBuffering = true
                    plcConsecutiveFrames = 0
                    emptyPopCount = 0
                }
                updateMetrics()
                return null
            }
        }
    }

    private fun updateMetrics() {
        _metrics.value = JitterBufferMetrics(
            jitterMs = estimatedJitterMs,
            packetLossCount = totalPacketsLost,
            lateDroppedCount = totalLatePacketsDropped,
            bufferDepthMs = (buffer.size * FRAME_DURATION_MS).toInt()
        )
    }

    fun reset() {
        buffer.clear()
        lastPlayedSequence = -1
        isBuffering = true
        plcConsecutiveFrames = 0
        emptyPopCount = 0
        lastArrivalEpochMs = 0L
        lastSenderTimestampMs = 0L
        synchronized(codecLock) { codec.reset() }
        updateMetrics()
    }
}
