package com.example.offlinewalkietalkie.voice

import eu.buney.kopus.OpusApplication
import eu.buney.kopus.OpusDecoder
import eu.buney.kopus.OpusEncoder

/**
 * Section 6 Opus Audio Codec with Packet Loss Concealment (PLC).
 * Configured for 48kHz mono PCM, 20ms frames (960 samples), VoIP application.
 */
class OpusCodec : AudioCodec {
    private var encoder = createEncoder()
    private var decoder = createDecoder()

    override fun encode(pcm: ShortArray): ByteArray {
        val outData = ByteArray(1024)
        val frameSize = pcm.size // 960 samples = 20ms
        val bytesEncoded = encoder.encode(pcm, 0, frameSize, outData, 0, outData.size)
        if (bytesEncoded < 0) return ByteArray(0)
        return outData.copyOfRange(0, bytesEncoded)
    }

    override fun decode(encoded: ByteArray): ShortArray {
        val outPcm = ShortArray(960)
        val samplesDecoded = decoder.decode(encoded, 0, encoded.size, outPcm, 0, 960, false)
        if (samplesDecoded < 0) return ShortArray(0)
        return outPcm
    }

    override fun decodeLostFrame(): ShortArray {
        val outPcm = ShortArray(960)
        return try {
            // Passing empty array to Opus decoder activates native Packet Loss Concealment (PLC)
            val samplesDecoded = decoder.decode(ByteArray(0), 0, 0, outPcm, 0, 960, false)
            if (samplesDecoded > 0) outPcm else ShortArray(960)
        } catch (e: Throwable) {
            ShortArray(960)
        }
    }

    override fun reset() {
        try {
            encoder.close()
        } catch (e: Throwable) {}
        try {
            decoder.close()
        } catch (e: Throwable) {}
        encoder = createEncoder()
        decoder = createDecoder()
    }

    private fun createEncoder(): OpusEncoder {
        return OpusEncoder(
            sampleRate = 48000,
            channels = 1,
            application = OpusApplication.Voip
        )
    }

    private fun createDecoder(): OpusDecoder {
        return OpusDecoder(
            sampleRate = 48000,
            channels = 1
        )
    }
}
