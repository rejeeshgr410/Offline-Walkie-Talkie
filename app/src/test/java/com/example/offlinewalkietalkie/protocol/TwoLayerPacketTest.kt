package com.example.offlinewalkietalkie.protocol

import com.example.offlinewalkietalkie.crypto.TinkCryptoEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TwoLayerPacketTest {

    private lateinit var cryptoEngine: TinkCryptoEngine
    private lateinit var codec: TwoLayerCodec

    @Before
    fun setUp() {
        cryptoEngine = TinkCryptoEngine()
        codec = TwoLayerCodec(cryptoEngine)
    }

    @Test
    fun testInnerPacketEncodeDecode() {
        val payload = "Hello TalkLoop Voice Packet".toByteArray(Charsets.UTF_8)
        val original = InnerPacket(
            type = FrameType.VOICE,
            flags = InnerPacket.FLAG_ENCRYPTED,
            hopLimit = 3,
            channelId = 123456789L,
            memberId = 987654321L,
            sequence = 42,
            timestamp = 1700000000,
            payload = payload
        )

        val encoded = original.encode()
        val decoded = InnerPacket.decode(encoded)

        assertNotNull("Decoded inner packet must not be null", decoded)
        assertEquals("Frame type must match", original.type, decoded!!.type)
        assertEquals("Hop limit must match", original.hopLimit, decoded.hopLimit)
        assertEquals("Channel ID must match", original.channelId, decoded.channelId)
        assertEquals("Member ID must match", original.memberId, decoded.memberId)
        assertEquals("Sequence number must match", original.sequence, decoded.sequence)
        assertArrayEquals("Payload bytes must match", original.payload, decoded.payload)
    }

    @Test
    fun testOuterPacketEncodeDecode() {
        val nonce = cryptoEngine.generateNonce()
        val ciphertext = ByteArray(64) { it.toByte() }
        val authTag = ByteArray(16) { (it * 2).toByte() }

        val outer = OuterPacket(
            senderNodeId = 0x7ABBCCDDEEFF0011L,
            nonce = nonce,
            ciphertext = ciphertext,
            authTag = authTag
        )

        val encoded = outer.encode()
        val decoded = OuterPacket.decode(encoded)

        assertNotNull("Decoded outer packet must not be null", decoded)
        assertEquals("Sender node ID must match", outer.senderNodeId, decoded!!.senderNodeId)
        assertArrayEquals("Nonce must match", outer.nonce, decoded.nonce)
        assertArrayEquals("Ciphertext must match", outer.ciphertext, decoded.ciphertext)
        assertArrayEquals("Auth tag must match", outer.authTag, decoded.authTag)
    }

    @Test
    fun testTwoLayerPackAndUnpackSuccess() {
        val alicePriv = cryptoEngine.generatePrivateKey()
        val alicePub = cryptoEngine.computePublicKey(alicePriv)
        val bobPriv = cryptoEngine.generatePrivateKey()
        val bobPub = cryptoEngine.computePublicKey(bobPriv)

        val sharedSecret = cryptoEngine.computeSharedSecret(alicePriv, bobPub)
        val hopKey = cryptoEngine.deriveHopKey(sharedSecret)
        val channelKey = cryptoEngine.deriveChannelKey(sharedSecret)

        val voiceData = "Simulated Opus Audio Frame Payload 20ms".toByteArray(Charsets.UTF_8)
        val innerPacket = InnerPacket(
            type = FrameType.VOICE,
            flags = InnerPacket.FLAG_ENCRYPTED,
            hopLimit = 3,
            channelId = 555555L,
            memberId = 888888L,
            sequence = 101,
            timestamp = 2000,
            payload = voiceData
        )

        val senderNodeId = 1122334455L
        val wireBytes = codec.pack(
            innerPacket = innerPacket,
            channelKey = channelKey,
            hopKey = hopKey,
            senderNodeId = senderNodeId
        )

        // 1. Hop-by-hop unwrapping (e.g. by an intermediate relay)
        val hopResult = codec.unpackOuter(wireBytes, hopKey)
        assertNotNull("Outer packet unwrap must succeed with valid hop key", hopResult)
        assertEquals("Sender node ID must match", senderNodeId, hopResult!!.first)

        // 2. End-to-end unwrapping (by the destination channel member)
        val receivedInner = codec.unpackInner(hopResult.second, channelKey)
        assertNotNull("Inner packet unwrap must succeed with valid channel key", receivedInner)
        assertEquals("Sequence number must match", 101, receivedInner!!.sequence)
        assertEquals("Member ID must match", 888888L, receivedInner.memberId)
        assertArrayEquals("Decrypted payload must match original voice data", voiceData, receivedInner.payload)
    }

    @Test
    fun testOuterPacketTamperDetection() {
        val sharedSecret = ByteArray(32) { 0x01 }
        val hopKey = cryptoEngine.deriveHopKey(sharedSecret)
        val channelKey = cryptoEngine.deriveChannelKey(sharedSecret)

        val inner = InnerPacket(
            type = FrameType.FLOOR_REQUEST,
            payload = byteArrayOf(1, 2, 3)
        )

        val wireBytes = codec.pack(inner, channelKey, hopKey, 12345L)

        // Tamper with a single byte in ciphertext
        wireBytes[wireBytes.size - 20] = (wireBytes[wireBytes.size - 20] + 1).toByte()

        val tamperedResult = codec.unpackOuter(wireBytes, hopKey)
        assertNull("Tampered outer packet must fail authentication and return null", tamperedResult)
    }

    @Test
    fun testSasCodeDerivation() {
        val transcriptA = "Handshake-Transcript-123456789".toByteArray(Charsets.UTF_8)
        val sasA = cryptoEngine.deriveSasCode(transcriptA)

        val transcriptB = "Handshake-Transcript-123456789".toByteArray(Charsets.UTF_8)
        val sasB = cryptoEngine.deriveSasCode(transcriptB)

        assertEquals("Same transcript must produce identical SAS codes", sasA, sasB)
        assertEquals("SAS code must be exactly 6 digits", 6, sasA.length)
        assertTrue("SAS code must be numeric digits only", sasA.all { it.isDigit() })
    }

    @Test
    fun testX25519EcdhSharedSecretAgreement() {
        val alicePriv = cryptoEngine.generatePrivateKey()
        val alicePub = cryptoEngine.computePublicKey(alicePriv)

        val bobPriv = cryptoEngine.generatePrivateKey()
        val bobPub = cryptoEngine.computePublicKey(bobPriv)

        val secretAlice = cryptoEngine.computeSharedSecret(alicePriv, bobPub)
        val secretBob = cryptoEngine.computeSharedSecret(bobPriv, alicePub)

        assertArrayEquals("Both parties must agree on the identical ECDH shared secret", secretAlice, secretBob)
    }
}
