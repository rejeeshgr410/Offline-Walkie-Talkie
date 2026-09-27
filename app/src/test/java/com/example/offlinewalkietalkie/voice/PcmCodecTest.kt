package com.example.offlinewalkietalkie.voice

import org.junit.Assert.*
import org.junit.Test

class PcmCodecTest {

    @Test
    fun testEncodeDecodeRoundTrip() {
        val codec = PcmCodec()
        val originalPcm = ShortArray(960) { it.toShort() }
        val encoded = codec.encode(originalPcm)
        assertEquals(1920, encoded.size) // 960 samples * 2 bytes

        val decodedPcm = codec.decode(encoded)
        assertEquals(960, decodedPcm.size)
        assertArrayEquals(originalPcm, decodedPcm)
    }

    @Test
    fun testDecodeLostFrameGeneratesSilence() {
        val codec = PcmCodec()
        val lostFrame = codec.decodeLostFrame()
        assertEquals(960, lostFrame.size)
        assertTrue(lostFrame.all { it == 0.toShort() })
    }
}
