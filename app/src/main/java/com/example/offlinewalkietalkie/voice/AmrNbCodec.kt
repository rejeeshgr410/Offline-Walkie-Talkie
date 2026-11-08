package com.example.offlinewalkietalkie.voice

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hardware AMR-NB (Adaptive Multi-Rate Narrowband) AudioCodec.
 *
 * Modeled after Vivo's MultiTypeAudio engine:
 * - Operates at 8,000 Hz mono (160 samples per 20ms frame).
 * - Ultra-low bitrate: ~4.75 kbps to 7.4 kbps (produces 13 to 20 bytes per 20ms audio frame).
 * - Built into Android MediaCodec on 100% of devices since API 16 with zero NDK requirements.
 * - Perfect for direct BLE GATT transmission and long-range LE Coded PHY links.
 */
class AmrNbCodec(
    val sampleRate: Int = SAMPLE_RATE_8K,
    val bitrate: Int = DEFAULT_BITRATE
) : AudioCodec {

    companion object {
        private const val TAG = "TL/AmrNbCodec"
        const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AMR_NB // "audio/3gpp"
        const val SAMPLE_RATE_8K = 8000
        const val SAMPLES_PER_FRAME_20MS = 160 // 8000 * 0.02s
        const val BYTES_PER_FRAME_20MS = SAMPLES_PER_FRAME_20MS * 2 // 320 bytes PCM16
        const val DEFAULT_BITRATE = 7400 // 7.4 kbps
        private const val TIMEOUT_US = 8000L
    }

    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null

    private val lock = Any()
    private var lastDecodedFrame: ShortArray = ShortArray(SAMPLES_PER_FRAME_20MS)

    init {
        initEncoder()
        initDecoder()
    }

    private fun initEncoder() {
        try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            }
            val codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            encoder = codec
            Log.i(TAG, "AMR-NB Encoder initialized successfully at $sampleRate Hz, $bitrate bps")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize AMR-NB encoder", e)
            encoder = null
        }
    }

    private fun initDecoder() {
        try {
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            }
            val codec = MediaCodec.createDecoderByType(MIME_TYPE)
            codec.configure(format, null, null, 0)
            codec.start()
            decoder = codec
            Log.i(TAG, "AMR-NB Decoder initialized successfully at $sampleRate Hz")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize AMR-NB decoder", e)
            decoder = null
        }
    }

    override fun encode(pcm: ShortArray): ByteArray = synchronized(lock) {
        val enc = encoder ?: return ByteArray(0)
        try {
            // Convert shorts to bytes (Little Endian)
            val pcmBytes = ByteArray(pcm.size * 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm)

            val inputIndex = enc.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = enc.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmBytes)
                enc.queueInputBuffer(inputIndex, 0, pcmBytes.size, System.nanoTime() / 1000L, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = enc.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuffer = enc.getOutputBuffer(outputIndex)
                val outData = ByteArray(bufferInfo.size)
                outputBuffer?.position(bufferInfo.offset)
                outputBuffer?.get(outData, 0, bufferInfo.size)
                enc.releaseOutputBuffer(outputIndex, false)
                return outData
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AMR-NB encode error: ${e.message}")
        }
        return ByteArray(0)
    }

    override fun decode(encoded: ByteArray): ShortArray = synchronized(lock) {
        if (encoded.isEmpty()) return ShortArray(0)
        val dec = decoder ?: return ShortArray(0)
        try {
            val inputIndex = dec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = dec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(encoded)
                dec.queueInputBuffer(inputIndex, 0, encoded.size, System.nanoTime() / 1000L, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = dec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuffer = dec.getOutputBuffer(outputIndex)
                val shortCount = bufferInfo.size / 2
                val outShorts = ShortArray(shortCount)
                outputBuffer?.position(bufferInfo.offset)
                outputBuffer?.order(ByteOrder.LITTLE_ENDIAN)?.asShortBuffer()?.get(outShorts)
                dec.releaseOutputBuffer(outputIndex, false)

                if (outShorts.isNotEmpty()) {
                    lastDecodedFrame = outShorts.copyOf()
                }
                return outShorts
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AMR-NB decode error: ${e.message}")
        }
        return ShortArray(0)
    }

    override fun decodeLostFrame(): ShortArray = synchronized(lock) {
        // Packet Loss Concealment (PLC): repeat last frame with 50% energy attenuation
        val plc = ShortArray(lastDecodedFrame.size)
        for (i in lastDecodedFrame.indices) {
            plc[i] = (lastDecodedFrame[i] * 0.5f).toInt().toShort()
        }
        lastDecodedFrame = plc
        return plc
    }

    override fun reset() = synchronized(lock) {
        try {
            encoder?.flush()
            decoder?.flush()
        } catch (e: Throwable) {
            Log.w(TAG, "AMR-NB flush warning: ${e.message}")
        }
        lastDecodedFrame.fill(0)
    }

    fun release() = synchronized(lock) {
        try {
            encoder?.stop()
            encoder?.release()
        } catch (e: Throwable) {}
        try {
            decoder?.stop()
            decoder?.release()
        } catch (e: Throwable) {}
        encoder = null
        decoder = null
    }
}
