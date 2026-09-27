package com.example.offlinewalkietalkie.voice

interface AudioCodec {
    fun encode(pcm: ShortArray): ByteArray
    fun decode(encoded: ByteArray): ShortArray
    fun decodeLostFrame(): ShortArray
    fun reset()
}
