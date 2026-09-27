package com.example.offlinewalkietalkie.voice

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Section 19.4 & Section 6 Pending-Floor Audio Ring Buffer.
 *
 * Buffers up to 500 ms (25 frames of 20 ms audio) while a floor request is pending.
 * When the floor is granted, buffered audio is drained in order and sent immediately,
 * ensuring no spoken syllables are clipped.
 * If floor is denied or released, the buffer is cleared.
 */
data class BufferedAudioFrame(
    val sequence: Int,
    val timestampMs: Long,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BufferedAudioFrame
        return sequence == other.sequence &&
                timestampMs == other.timestampMs &&
                data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = sequence
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class PendingFloorBuffer(
    val maxFrames: Int = DEFAULT_MAX_FRAMES
) {
    companion object {
        const val FRAME_DURATION_MS = 20L
        const val MAX_BUFFER_DURATION_MS = 500L
        const val DEFAULT_MAX_FRAMES = (MAX_BUFFER_DURATION_MS / FRAME_DURATION_MS).toInt() // 25 frames
    }

    private val queue = ConcurrentLinkedDeque<BufferedAudioFrame>()

    val size: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()

    /**
     * Push a new audio frame into the buffer.
     * If the buffer exceeds [maxFrames], the oldest frame is dropped (ring-buffer semantics).
     */
    fun push(sequence: Int, timestampMs: Long, data: ByteArray) {
        while (queue.size >= maxFrames) {
            queue.pollFirst() // Drop oldest frame
        }
        queue.offerLast(BufferedAudioFrame(sequence, timestampMs, data))
    }

    /**
     * Drains all buffered frames in order and clears the buffer.
     */
    fun drain(): List<BufferedAudioFrame> {
        val frames = mutableListOf<BufferedAudioFrame>()
        while (true) {
            val frame = queue.pollFirst() ?: break
            frames.add(frame)
        }
        return frames
    }

    /**
     * Clears all buffered frames without returning them.
     */
    fun clear() {
        queue.clear()
    }
}
