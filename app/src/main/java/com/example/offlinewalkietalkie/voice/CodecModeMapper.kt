package com.example.offlinewalkietalkie.voice

import com.example.offlinewalkietalkie.protocol.VivoLeanProtocol

/**
 * Keeps the compact on-wire codec index independent of codec2's native mode values.
 * They are deliberately not numerically compatible: wire 0 means 1600 bit/s,
 * while native codec2 mode 0 means 3200 bit/s.
 */
object CodecModeMapper {
    fun codec2NativeModeForWireMode(wireMode: Int): Int = when (wireMode) {
        VivoLeanProtocol.CODEC_MODE_3200 -> Codec2Native.CODEC2_MODE_3200
        VivoLeanProtocol.CODEC_MODE_2400 -> Codec2Native.CODEC2_MODE_2400
        VivoLeanProtocol.CODEC_MODE_1600 -> Codec2Native.CODEC2_MODE_1600
        VivoLeanProtocol.CODEC_MODE_1400 -> Codec2Native.CODEC2_MODE_1400
        VivoLeanProtocol.CODEC_MODE_1300 -> Codec2Native.CODEC2_MODE_1300
        VivoLeanProtocol.CODEC_MODE_1200 -> Codec2Native.CODEC2_MODE_1200
        VivoLeanProtocol.CODEC_MODE_700 -> Codec2Native.CODEC2_MODE_700
        // Codec2's bundled library has no 450 bit/s mode. Preserve a valid,
        // conservative codec setting rather than feeding an unsupported index.
        VivoLeanProtocol.CODEC_MODE_450 -> Codec2Native.CODEC2_MODE_700
        else -> Codec2Native.CODEC2_MODE_1600
    }

    /** AMR-NB has no 450 bps analogue; map quality intent from robust to clear. */
    fun amrNbBitrateForWireMode(wireMode: Int): Int = when (wireMode) {
        VivoLeanProtocol.CODEC_MODE_3200 -> 12_200
        VivoLeanProtocol.CODEC_MODE_2400 -> 10_200
        VivoLeanProtocol.CODEC_MODE_1600 -> 7_950
        VivoLeanProtocol.CODEC_MODE_1400 -> 6_700
        VivoLeanProtocol.CODEC_MODE_1300 -> 5_900
        VivoLeanProtocol.CODEC_MODE_1200 -> 5_150
        VivoLeanProtocol.CODEC_MODE_700,
        VivoLeanProtocol.CODEC_MODE_450 -> 4_750
        else -> 7_950
    }
}
