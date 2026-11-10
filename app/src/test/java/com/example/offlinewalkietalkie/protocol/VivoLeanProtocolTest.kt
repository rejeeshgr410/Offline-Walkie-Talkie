package com.example.offlinewalkietalkie.protocol

import org.junit.Assert.*
import org.junit.Test

class VivoLeanProtocolTest {

    @Test
    fun testEncodeDecodeWithTimestamp() {
        val payload = "Hello Vivo Lean Protocol".toByteArray(Charsets.UTF_8)
        val timestamp = 1731283200000L

        val encoded = VivoLeanProtocol.encode(
            type = VivoLeanProtocol.TYPE_AUDIO_FRAME,
            codecMode = VivoLeanProtocol.CODEC_MODE_2400,
            payload = payload,
            includeTimestamp = true,
            customTimestamp = timestamp
        )

        assertTrue(VivoLeanProtocol.isLeanPacket(encoded))
        // 4 bytes header + 8 bytes timestamp + payload.size
        assertEquals(4 + 8 + payload.size, encoded.size)

        val packet = VivoLeanProtocol.decode(encoded)
        assertNotNull(packet)
        assertEquals(VivoLeanProtocol.TYPE_AUDIO_FRAME, packet!!.type)
        assertEquals(VivoLeanProtocol.CODEC_MODE_2400, packet.codecMode)
        assertEquals(timestamp, packet.timestamp)
        assertTrue(packet.hasTimestamp)
        assertArrayEquals(payload, packet.payload)
    }

    @Test
    fun testEncodeDecodeWithoutTimestamp() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val encoded = VivoLeanProtocol.encode(
            type = VivoLeanProtocol.TYPE_CODEC_REQ,
            codecMode = VivoLeanProtocol.CODEC_MODE_1200,
            payload = payload,
            includeTimestamp = false
        )

        assertTrue(VivoLeanProtocol.isLeanPacket(encoded))
        // 4 bytes header + 0 bytes timestamp + payload.size
        assertEquals(4 + payload.size, encoded.size)

        val packet = VivoLeanProtocol.decode(encoded)
        assertNotNull(packet)
        assertEquals(VivoLeanProtocol.TYPE_CODEC_REQ, packet!!.type)
        assertEquals(VivoLeanProtocol.CODEC_MODE_1200, packet.codecMode)
        assertFalse(packet.hasTimestamp)
        assertArrayEquals(payload, packet.payload)
    }

    @Test
    fun testAllTwelvePacketTypes() {
        val types = listOf(
            VivoLeanProtocol.TYPE_AUDIO_FRAME,
            VivoLeanProtocol.TYPE_SOS_EMERGENCY,
            VivoLeanProtocol.TYPE_CONNECT_REQ,
            VivoLeanProtocol.TYPE_DISCONNECT,
            VivoLeanProtocol.TYPE_HEARTBEAT,
            VivoLeanProtocol.TYPE_REJECT_REQ,
            VivoLeanProtocol.TYPE_LOCATION_SYNC,
            VivoLeanProtocol.TYPE_PING,
            VivoLeanProtocol.TYPE_AUTO_CONNECT,
            VivoLeanProtocol.TYPE_CODEC_REQ,
            VivoLeanProtocol.TYPE_CODEC_ACK,
            VivoLeanProtocol.TYPE_CODEC_NACK
        )

        for (type in types) {
            val encoded = VivoLeanProtocol.encode(type = type, codecMode = VivoLeanProtocol.CODEC_MODE_1600)
            assertTrue("Type $type should be recognized as lean packet", VivoLeanProtocol.isLeanPacket(encoded))
            val packet = VivoLeanProtocol.decode(encoded)
            assertNotNull(packet)
            assertEquals(type, packet!!.type)
        }
    }

    @Test
    fun testCodecModes() {
        for (mode in 0..7) {
            val encoded = VivoLeanProtocol.encode(type = VivoLeanProtocol.TYPE_AUDIO_FRAME, codecMode = mode)
            val packet = VivoLeanProtocol.decode(encoded)
            assertNotNull(packet)
            assertEquals(mode, packet!!.codecMode)
        }
    }
}
