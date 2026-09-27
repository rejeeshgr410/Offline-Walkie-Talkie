package com.example.offlinewalkietalkie.mesh

import com.example.offlinewalkietalkie.crypto.TinkCryptoEngine
import com.example.offlinewalkietalkie.protocol.FrameType
import com.example.offlinewalkietalkie.protocol.InnerPacket
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MeshRelayTest {

    private lateinit var cryptoEngine: TinkCryptoEngine

    private lateinit var nodeA: MeshRelayRouter
    private lateinit var nodeB: MeshRelayRouter // Relay-only node (§19.2)
    private lateinit var nodeC: MeshRelayRouter

    private lateinit var hopKeyAB: ByteArray
    private lateinit var hopKeyBC: ByteArray
    private lateinit var channelVoiceKey: ByteArray

    @Before
    fun setUp() {
        cryptoEngine = TinkCryptoEngine()

        // 1. Initialize 3 nodes: A (sender), B (relay-only), C (receiver)
        nodeA = MeshRelayRouter(localNodeId = 111L)
        nodeB = MeshRelayRouter(localNodeId = 222L)
        nodeC = MeshRelayRouter(localNodeId = 333L)

        // 2. Derive distinct link keys for hops A-B and B-C
        val secretAB = ByteArray(32) { 0xAA.toByte() }
        val secretBC = ByteArray(32) { 0xBB.toByte() }
        hopKeyAB = cryptoEngine.deriveHopKey(secretAB)
        hopKeyBC = cryptoEngine.deriveHopKey(secretBC)

        // 3. Channel voice key is known ONLY by A and C (§19.2: relays do not receive voice keys)
        channelVoiceKey = ByteArray(32) { 0xCC.toByte() }
        nodeA.channelVoiceKey = channelVoiceKey
        nodeC.channelVoiceKey = channelVoiceKey
        nodeB.channelVoiceKey = null // Node B is relay-only!

        // 4. Configure neighbor links:
        // A is connected to B
        nodeA.registerNeighborLink(endpointId = "ep_B", neighborNodeId = 222L, hopKey = hopKeyAB, isRelay = true)

        // B is connected to A and C
        nodeB.registerNeighborLink(endpointId = "ep_A", neighborNodeId = 111L, hopKey = hopKeyAB, isRelay = false)
        nodeB.registerNeighborLink(endpointId = "ep_C", neighborNodeId = 333L, hopKey = hopKeyBC, isRelay = false)

        // C is connected to B
        nodeC.registerNeighborLink(endpointId = "ep_B", neighborNodeId = 222L, hopKey = hopKeyBC, isRelay = true)
    }

    @Test
    fun testABCThreeNodeChainCiphertextRelayingWithoutVoiceKey() {
        val voiceSecretPayload = "Classified Voice Data 20ms Frame".toByteArray(Charsets.UTF_8)

        // 1. Node A creates an InnerPacket
        val innerPacketA = InnerPacket(
            type = FrameType.VOICE,
            flags = InnerPacket.FLAG_ENCRYPTED,
            hopLimit = 3,
            channelId = 777L,
            memberId = 111L,
            sequence = 1,
            timestamp = 1000,
            payload = voiceSecretPayload
        )

        // 2. Node A originates wire packets down the tree
        val packetsFromA = nodeA.originatePacket(innerPacketA, channelVoiceKey)
        val wirePacketForB = packetsFromA["ep_B"]
        assertNotNull("Node A must produce an encrypted packet for neighbor B", wirePacketForB)

        // 3. Packet arrives at Node B (Relay)
        val outcomeB = nodeB.processPacket(fromEndpointId = "ep_A", wireBytes = wirePacketForB!!)
        assertTrue("Relay B must forward the packet to next hops", outcomeB is MeshOutcome.Forwarded)

        val forwardedB = outcomeB as MeshOutcome.Forwarded
        assertEquals(listOf("ep_C"), forwardedB.nextHopEndpointIds)
        assertEquals(2.toByte(), forwardedB.remainingHopLimit) // Decremented 3 -> 2

        val wirePacketForC = forwardedB.rewrappedPackets["ep_C"]
        assertNotNull("Relay B must provide rewrapped packet for C", wirePacketForC)

        // Verify Node B's state: relayed 1 packet, zero voice keys
        assertEquals(1, nodeB.totalPacketsRelayed)
        assertNull("Node B must never possess the channel voice key per Section 19.2", nodeB.channelVoiceKey)

        // 4. Packet arrives at Node C (Destination Member)
        val outcomeC = nodeC.processPacket(fromEndpointId = "ep_B", wireBytes = wirePacketForC!!)
        assertTrue("Destination C must successfully decrypt and deliver locally", outcomeC is MeshOutcome.DeliveredLocally)

        val deliveredC = outcomeC as MeshOutcome.DeliveredLocally
        assertEquals(FrameType.VOICE, deliveredC.innerPacket.type)
        assertEquals(111L, deliveredC.innerPacket.memberId)
        assertEquals(1, deliveredC.innerPacket.sequence)
        assertArrayEquals("Payload decrypted by C must match original voice payload from A",
            voiceSecretPayload, deliveredC.innerPacket.payload)
    }

    @Test
    fun testHopLimitEnforcementDropsExpiredPackets() {
        // Node A sends packet with hopLimit = 1
        val innerPacket = InnerPacket(
            type = FrameType.VOICE,
            flags = InnerPacket.FLAG_ENCRYPTED,
            hopLimit = 1,
            channelId = 777L,
            memberId = 111L,
            sequence = 2,
            payload = byteArrayOf(1, 2, 3)
        )

        val packetsFromA = nodeA.originatePacket(innerPacket, channelVoiceKey)
        val wirePacketForB = packetsFromA["ep_B"]!!

        // Node B receives packet with hopLimit = 1 -> decrementing reaches 0 -> dropped!
        val outcomeB = nodeB.processPacket(fromEndpointId = "ep_A", wireBytes = wirePacketForB)
        assertEquals(MeshOutcome.DroppedHopLimitExceeded, outcomeB)
        assertEquals(1, nodeB.totalHopLimitDropped)
        assertEquals(0, nodeB.totalPacketsRelayed)
    }

    @Test
    fun testLoopAndDuplicateCachePreventsEcho() {
        val innerPacket = InnerPacket(
            type = FrameType.TEXT,
            hopLimit = 3,
            channelId = 777L,
            memberId = 111L,
            sequence = 3,
            payload = "Duplicate Test".toByteArray()
        )

        val packetsFromA = nodeA.originatePacket(innerPacket, channelVoiceKey)
        val wirePacket = packetsFromA["ep_B"]!!

        // First delivery: forwarded
        val firstOutcome = nodeB.processPacket("ep_A", wirePacket)
        assertTrue(firstOutcome is MeshOutcome.Forwarded)

        // Second delivery of identical packet: dropped duplicate
        val secondOutcome = nodeB.processPacket("ep_A", wirePacket)
        assertEquals(MeshOutcome.DroppedDuplicate, secondOutcome)
        assertEquals(1, nodeB.totalDuplicatesDropped)
    }

    @Test
    fun testRouteFailureCleanlyDropsWithoutCrash() {
        // Disconnect Node C from B
        nodeB.unregisterNeighborLink("ep_C")

        val innerPacket = InnerPacket(
            type = FrameType.VOICE,
            hopLimit = 3,
            channelId = 777L,
            memberId = 111L,
            sequence = 4,
            payload = byteArrayOf(9, 9, 9)
        )

        val wirePacket = nodeA.originatePacket(innerPacket, channelVoiceKey)["ep_B"]!!
        val outcome = nodeB.processPacket("ep_A", wirePacket)

        // No downstream route available
        assertEquals(MeshOutcome.DroppedNoRoute, outcome)
    }
}
