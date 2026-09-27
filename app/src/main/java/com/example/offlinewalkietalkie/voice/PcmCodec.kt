package com.example.offlinewalkietalkie.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmCodec : AudioCodec {
    override fun encode(pcm: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in pcm) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }

    override fun decode(encoded: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        val pcm = ShortArray(encoded.size / 2)
        for (i in pcm.indices) {
            pcm[i] = buffer.getShort()
        }
        return pcm
    }

    override fun decodeLostFrame(): ShortArray {
        return ShortArray(960) // 20ms of silence at 48kHz mono
    }

    override fun reset() {
        // No-op for PCM
    }
}
