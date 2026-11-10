package com.example.offlinewalkietalkie.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-Efficiency Packed Wire Protocol.
 *
 * Reverse-engineered directly from Vivo Walkie-Talkie v1.2.0 (pj.e.h & pj.w.i):
 * - Ultra-lean 4-byte packed header:
 *   - Bits 0..7: Packet Type (1..12)
 *   - Bits 8..10: Codec Mode Index (0..7)
 *   - Bit 14 (0x4000 = 16384): FLAG_NO_TIMESTAMP (saves 8 bytes on bandwidth-constrained links)
 *   - Bits 11..13, 15..31: Reserved / state flags
 * - Optional 8-byte millisecond timestamp (omitted when bit 14 is set)
 * - Variable-length raw payload (audio frames, SOS packets, GPS sync, metadata)
 */
object VivoLeanProtocol {

    // Packet Types (from pj.w.i)
    const val TYPE_AUDIO_FRAME = 0x01
    const val TYPE_SOS_EMERGENCY = 0x02
    const val TYPE_CONNECT_REQ = 0x03
    const val TYPE_DISCONNECT = 0x04
    const val TYPE_HEARTBEAT = 0x05
    const val TYPE_REJECT_REQ = 0x06
    const val TYPE_LOCATION_SYNC = 0x07
    const val TYPE_PING = 0x08
    const val TYPE_AUTO_CONNECT = 0x09
    const val TYPE_CODEC_REQ = 0x0A
    const val TYPE_CODEC_ACK = 0x0B
    const val TYPE_CODEC_NACK = 0x0C

    // Bitmask constants
    const val MASK_TYPE = 0xFF
    const val MASK_CODEC_MODE = 0x07
    const val SHIFT_CODEC_MODE = 8
    const val FLAG_NO_TIMESTAMP = 0x4000 // Bit 14 (16384)

    // Codec Modes (Matching Vivo nj.a and Codec2 / AMR-NB bitrates)
    // 0: Codec2 1600 bps / AMR-NB 4.75 kbps
    // 1: Codec2 3200 bps / AMR-NB 5.15 kbps
    // 2: Codec2 2400 bps (Default) / AMR-NB 5.90 kbps
    // 3: Codec2 1400 bps / AMR-NB 6.70 kbps
    // 4: Codec2 1300 bps / AMR-NB 7.40 kbps
    // 5: Codec2 1200 bps / AMR-NB 7.95 kbps
    // 6: Codec2 700 bps (700C) / AMR-NB 10.20 kbps
    // 7: Codec2 450 bps / AMR-NB 12.20 kbps
    const val CODEC_MODE_1600 = 0
    const val CODEC_MODE_3200 = 1
    const val CODEC_MODE_2400 = 2
    const val CODEC_MODE_1400 = 3
    const val CODEC_MODE_1300 = 4
    const val CODEC_MODE_1200 = 5
    const val CODEC_MODE_700 = 6
    const val CODEC_MODE_450 = 7

    /**
     * Decoded packet model.
     */
    data class Packet(
        val type: Int,
        val codecMode: Int,
        val timestamp: Long,
        val hasTimestamp: Boolean,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Packet
            if (type != other.type) return false
            if (codecMode != other.codecMode) return false
            if (timestamp != other.timestamp) return false
            if (hasTimestamp != other.hasTimestamp) return false
            if (!payload.contentEquals(other.payload)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = type
            result = 31 * result + codecMode
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + hasTimestamp.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * Pack a packet into wire bytes.
     * Matches pj.e.h exactly.
     */
    fun encode(
        type: Int,
        codecMode: Int = CODEC_MODE_2400,
        payload: ByteArray? = null,
        textMetadata: String? = null,
        includeTimestamp: Boolean = true,
        customTimestamp: Long? = null
    ): ByteArray {
        val payloadLen = payload?.size ?: 0
        val textBytes = textMetadata?.toByteArray(Charsets.UTF_8)
        val textLen = textBytes?.size ?: 0
        val headerLen = (if (includeTimestamp) 8 else 0) + 4
        val totalLen = headerLen + payloadLen + textLen

        val buffer = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN)

        // Build 4-byte header integer
        var headerInt = (type and MASK_TYPE) or ((codecMode and MASK_CODEC_MODE) shl SHIFT_CODEC_MODE)
        if (!includeTimestamp) {
            headerInt = headerInt or FLAG_NO_TIMESTAMP
        }

        buffer.putInt(headerInt)

        if (includeTimestamp) {
            buffer.putLong(customTimestamp ?: System.currentTimeMillis())
        }

        payload?.let { buffer.put(it) }
        textBytes?.let { buffer.put(it) }

        return buffer.array()
    }

    /**
     * Parse raw wire bytes into a Packet.
     * Matches pj.w.i exactly.
     */
    fun decode(data: ByteArray): Packet? {
        if (data.size < 4) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val headerInt = buffer.getInt()

        val type = headerInt and MASK_TYPE
        val codecMode = (headerInt shr SHIFT_CODEC_MODE) and MASK_CODEC_MODE
        val withoutTimestamp = (headerInt and FLAG_NO_TIMESTAMP) == FLAG_NO_TIMESTAMP

        val timestamp = if (withoutTimestamp) {
            System.currentTimeMillis()
        } else {
            if (buffer.remaining() < 8) return null
            buffer.getLong()
        }

        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)

        return Packet(
            type = type,
            codecMode = codecMode,
            timestamp = timestamp,
            hasTimestamp = !withoutTimestamp,
            payload = payload
        )
    }

    /**
     * Checks if a byte array starts with a valid VivoLeanProtocol header (types 1..12).
     */
    fun isLeanPacket(data: ByteArray): Boolean {
        if (data.size < 4) return false
        val firstInt = ((data[0].toInt() and 0xFF) shl 24) or
            ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)
        val type = firstInt and MASK_TYPE
        return type in TYPE_AUDIO_FRAME..TYPE_CODEC_NACK
    }
}
