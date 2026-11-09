package com.example.offlinewalkietalkie.voice

import android.util.Log

/**
 * Native Codec 2 AudioCodec for Survival / Extreme Range Mode.
 *
 * Modeled after Vivo's ultra-low bitrate tactical engine:
 * - Operates at 8,000 Hz mono PCM.
 * - Mode 1600 produces 64 bits (8 bytes) per 40ms frame (320 samples) -> 1,600 bps!
 * - Fits into single micro-BLE frames for maximum RF penetration at extreme ranges up to 1km.
 */
class Codec2AudioCodec(
    val mode: Int = Codec2Native.CODEC2_MODE_1600
) : AudioCodec {

    companion object {
        private const val TAG = "TL/Codec2Codec"
        const val SAMPLE_RATE_8K = 8000
    }

    private var encoderHandle: Long = 0
    private var decoderHandle: Long = 0

    val samplesPerFrame: Int
    val bytesPerFrame: Int

    private val lock = Any()
    private var lastDecodedFrame: ShortArray

    init {
        if (Codec2Native.isLoaded) {
            encoderHandle = Codec2Native.create(mode)
            decoderHandle = Codec2Native.create(mode)
            samplesPerFrame = if (encoderHandle != 0L) Codec2Native.getSamplesPerFrame(encoderHandle) else 320
            bytesPerFrame = if (encoderHandle != 0L) Codec2Native.getBitsSize(encoderHandle) else 8
        } else {
            samplesPerFrame = 320
            bytesPerFrame = 8
        }
        lastDecodedFrame = ShortArray(samplesPerFrame)
        Log.i(TAG, "Codec2 initialized: mode=$mode, samplesPerFrame=$samplesPerFrame, bytesPerFrame=$bytesPerFrame, loaded=${Codec2Native.isLoaded}")
    }

    override fun encode(pcm: ShortArray): ByteArray = synchronized(lock) {
        if (encoderHandle == 0L || !Codec2Native.isLoaded) return ByteArray(0)
        val outBits = ByteArray(bytesPerFrame)
        val input = if (pcm.size >= samplesPerFrame) pcm else {
            ShortArray(samplesPerFrame).also { System.arraycopy(pcm, 0, it, 0, pcm.size) }
        }
        val res = Codec2Native.encode(encoderHandle, input, outBits)
        if (res == 0L) outBits else ByteArray(0)
    }

    override fun decode(encoded: ByteArray): ShortArray = synchronized(lock) {
        if (decoderHandle == 0L || !Codec2Native.isLoaded || encoded.isEmpty()) return ShortArray(0)
        val outPcm = ShortArray(samplesPerFrame)
        val input = if (encoded.size >= bytesPerFrame) encoded else {
            ByteArray(bytesPerFrame).also { System.arraycopy(encoded, 0, it, 0, encoded.size) }
        }
        val res = Codec2Native.decode(decoderHandle, outPcm, input)
        if (res == 0L) {
            lastDecodedFrame = outPcm.copyOf()
            outPcm
        } else {
            lastDecodedFrame
        }
    }

    override fun decodeLostFrame(): ShortArray = synchronized(lock) {
        lastDecodedFrame.map { (it * 0.5f).toInt().toShort() }.toShortArray()
    }

    override fun reset() = synchronized(lock) {
        if (encoderHandle != 0L) Codec2Native.destroy(encoderHandle)
        if (decoderHandle != 0L) Codec2Native.destroy(decoderHandle)
        if (Codec2Native.isLoaded) {
            encoderHandle = Codec2Native.create(mode)
            decoderHandle = Codec2Native.create(mode)
        }
    }

    protected fun finalize() {
        if (encoderHandle != 0L) Codec2Native.destroy(encoderHandle)
        if (decoderHandle != 0L) Codec2Native.destroy(decoderHandle)
    }
}
