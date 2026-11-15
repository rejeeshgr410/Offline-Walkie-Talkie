package com.example.offlinewalkietalkie.voice

import com.example.offlinewalkietalkie.protocol.VivoLeanProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecModeMapperTest {
    @Test
    fun `wire indexes select their intended native codec2 modes`() {
        assertEquals(
            Codec2Native.CODEC2_MODE_1600,
            CodecModeMapper.codec2NativeModeForWireMode(VivoLeanProtocol.CODEC_MODE_1600)
        )
        assertEquals(
            Codec2Native.CODEC2_MODE_3200,
            CodecModeMapper.codec2NativeModeForWireMode(VivoLeanProtocol.CODEC_MODE_3200)
        )
        assertEquals(
            Codec2Native.CODEC2_MODE_2400,
            CodecModeMapper.codec2NativeModeForWireMode(VivoLeanProtocol.CODEC_MODE_2400)
        )
    }

    @Test
    fun `unsupported 450 wire mode falls back to supported 700 native mode`() {
        assertEquals(
            Codec2Native.CODEC2_MODE_700,
            CodecModeMapper.codec2NativeModeForWireMode(VivoLeanProtocol.CODEC_MODE_450)
        )
    }

    @Test
    fun `amr mapping preserves low to high quality intent`() {
        assertEquals(4_750, CodecModeMapper.amrNbBitrateForWireMode(VivoLeanProtocol.CODEC_MODE_700))
        assertEquals(10_200, CodecModeMapper.amrNbBitrateForWireMode(VivoLeanProtocol.CODEC_MODE_2400))
        assertEquals(12_200, CodecModeMapper.amrNbBitrateForWireMode(VivoLeanProtocol.CODEC_MODE_3200))
    }
}
