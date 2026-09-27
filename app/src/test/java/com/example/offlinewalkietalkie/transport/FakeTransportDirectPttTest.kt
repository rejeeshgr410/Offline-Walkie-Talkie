package com.example.offlinewalkietalkie.transport

import com.example.offlinewalkietalkie.crypto.TinkCryptoEngine
import com.example.offlinewalkietalkie.protocol.FrameType
import com.example.offlinewalkietalkie.protocol.InnerPacket
import com.example.offlinewalkietalkie.protocol.TwoLayerCodec
import com.example.offlinewalkietalkie.testutil.FakeTransport
import com.example.offlinewalkietalkie.voice.PcmCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeTransportDirectPttTest {

    @Test
    fun testFakeTransportDirectPttWithPcmCodec() = runTest {
        val aliceTransport = FakeTransport(localId = "alice_endpoint")
        val bobTransport = FakeTransport(localId = "bob_endpoint")
        aliceTransport.linkTo(bobTransport)

        // 1. Establish direct link over transport
        aliceTransport.startAdvertising(LocalIdentity("Alice"))
        bobTransport.startDiscovery(LocalIdentity("Bob"))

        bobTransport.connect("alice_endpoint")
        aliceTransport.acceptConnection("bob_endpoint")

        assertEquals(TransportState.ACTIVE, aliceTransport.state.value)
        assertEquals(TransportState.ACTIVE, bobTransport.state.value)

        // 2. Perform ECDH key agreement between Alice and Bob
        val cryptoEngine = TinkCryptoEngine()
        val codec = TwoLayerCodec(cryptoEngine)

        val alicePriv = cryptoEngine.generatePrivateKey()
        val alicePub = cryptoEngine.computePublicKey(alicePriv)
        val bobPriv = cryptoEngine.generatePrivateKey()
        val bobPub = cryptoEngine.computePublicKey(bobPriv)

        val aliceShared = cryptoEngine.computeSharedSecret(alicePriv, bobPub)
        val bobShared = cryptoEngine.computeSharedSecret(bobPriv, alicePub)
        assertArrayEquals(aliceShared, bobShared)

        val channelKey = cryptoEngine.deriveChannelKey(aliceShared)
        val hopKey = cryptoEngine.deriveHopKey(aliceShared)

        // 3. Alice encodes test audio frame using test PcmCodec (20 ms @ 48kHz = 960 samples)
        val pcmCodec = PcmCodec()
        val originalPcm = ShortArray(960) { i -> (i * 10).toShort() }
        val encodedAudio = pcmCodec.encode(originalPcm)
        assertEquals(1920, encodedAudio.size)

        // 4. Alice wraps audio into InnerPacket and encodes with TwoLayerCodec
        val innerPacket = InnerPacket(
            type = FrameType.VOICE,
            flags = InnerPacket.FLAG_ENCRYPTED,
            hopLimit = 3,
            channelId = 0x54414C4BL,
            memberId = 0x11223344L,
            sequence = 1,
            timestamp = 1000,
            payload = encodedAudio
        )

        val wireBytes = codec.pack(
            innerPacket = innerPacket,
            channelKey = channelKey,
            hopKey = hopKey,
            senderNodeId = 0x11223344L
        )

        // 5. Send over FakeTransport
        aliceTransport.send("bob_endpoint", wireBytes)

        // 6. Bob receives incoming frame from FakeTransport
        val receivedIncoming = bobTransport.incomingFrames().first()
        assertEquals("alice_endpoint", receivedIncoming.peerId)
        assertArrayEquals(wireBytes, receivedIncoming.bytes)

        // 7. Bob unpacks outer packet with hopKey
        val hopResult = codec.unpackOuter(receivedIncoming.bytes, hopKey)
        assertNotNull(hopResult)
        assertEquals(0x11223344L, hopResult!!.first)

        // 8. Bob unpacks inner voice packet with channelKey
        val plaintextInner = codec.unpackInner(hopResult.second, channelKey)
        assertNotNull(plaintextInner)
        assertEquals(FrameType.VOICE, plaintextInner?.type)
        assertEquals(1, plaintextInner?.sequence)

        // 9. Bob decodes audio with PcmCodec and verifies fidelity
        val decodedPcm = pcmCodec.decode(plaintextInner!!.payload)
        assertEquals(originalPcm.size, decodedPcm.size)
        assertArrayEquals(originalPcm, decodedPcm)

        // 10. Clean up
        aliceTransport.stop()
        bobTransport.stop()
        assertEquals(TransportState.IDLE, aliceTransport.state.value)
        assertEquals(TransportState.IDLE, bobTransport.state.value)
    }
}
