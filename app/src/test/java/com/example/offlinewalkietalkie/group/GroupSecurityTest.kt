package com.example.offlinewalkietalkie.group

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The group must never admit a device that nobody vouched for. These cover the attacks a
 * nearby stranger can actually mount: copying the public advert, impersonating a member,
 * and injecting control messages.
 */
class GroupSecurityTest {

    private suspend fun formGroup(size: Int): MeshSimulator {
        val sim = MeshSimulator()
        val ids = (0 until size).map { ('A' + it).toString().repeat(4) }
        ids.forEach { sim.addNode(it) }
        val host = sim.nodeOf(ids.first())
        host.manager.createGroup("Field Ops")
        sim.discoverAll()
        ids.drop(1).forEach { sim.nodeOf(it).manager.joinGroup(host.advert!!, host.endpointId) }
        sim.settle(rounds = 8)
        return sim
    }

    // ----------------------------------------------------------- first contact is human

    @Test
    fun `joining needs both people to confirm the code`() = runTest {
        val sim = MeshSimulator()
        val host = sim.addNode("AAAA")
        val joiner = sim.addNode("BBBB")
        host.manager.createGroup("Field Ops")
        sim.discoverAll()
        joiner.manager.joinGroup(host.advert!!, host.endpointId)

        // Nobody taps anything.
        sim.settle(rounds = 4, autoApprove = false)

        val onHost = host.manager.state.value.member("BBBB")
        val onJoiner = joiner.manager.state.value.member("AAAA")
        assertEquals(MemberState.PENDING_APPROVAL, onHost?.state)
        assertEquals(MemberState.PENDING_APPROVAL, onJoiner?.state)
        assertNotNull(onHost?.authCode)
        assertEquals("both phones must show the same code", onHost?.authCode, onJoiner?.authCode)
        assertFalse(sim.isLinked("AAAA", "BBBB"))
        assertFalse("no secret before approval", joiner.manager.holdsGroupSecret)
    }

    @Test
    fun `one side confirming is not enough`() = runTest {
        val sim = MeshSimulator()
        val host = sim.addNode("AAAA")
        val joiner = sim.addNode("BBBB")
        host.manager.createGroup("Field Ops")
        sim.discoverAll()
        joiner.manager.joinGroup(host.advert!!, host.endpointId)

        host.manager.respondToRequest("BBBB", accept = true)
        sim.settle(rounds = 3, autoApprove = false)

        assertFalse(sim.isLinked("AAAA", "BBBB"))
        assertFalse(joiner.manager.holdsGroupSecret)
    }

    @Test
    fun `confirming on both sides admits the joiner and hands over the secret`() = runTest {
        val sim = formGroup(2)
        assertTrue(sim.nodeOf("BBBB").manager.holdsGroupSecret)
        assertEquals(
            MemberState.CONNECTED,
            sim.nodeOf("AAAA").manager.state.value.member("BBBB")?.state
        )
    }

    @Test
    fun `declining keeps the device out`() = runTest {
        val sim = MeshSimulator()
        val host = sim.addNode("AAAA")
        val joiner = sim.addNode("BBBB")
        host.manager.createGroup("Field Ops")
        sim.discoverAll()
        joiner.manager.joinGroup(host.advert!!, host.endpointId)

        host.manager.respondToRequest("BBBB", accept = false)
        sim.settle(rounds = 3, autoApprove = false)

        assertEquals(null, host.manager.state.value.member("BBBB"))
        assertFalse(sim.isLinked("AAAA", "BBBB"))
    }

    @Test
    fun `an unanswered code expires with a reason`() = runTest {
        val sim = MeshSimulator()
        val host = sim.addNode("AAAA")
        val joiner = sim.addNode("BBBB")
        host.manager.createGroup("Field Ops")
        sim.discoverAll()
        joiner.manager.joinGroup(host.advert!!, host.endpointId)

        sim.settle(
            rounds = 2,
            advanceMs = GroupSessionManager.APPROVAL_TIMEOUT_MS + 1_000,
            autoApprove = false
        )

        val onHost = host.manager.state.value.member("BBBB")
        assertEquals(MemberState.FAILED, onHost?.state)
        assertNotNull(onHost?.failureReason)
    }

    // ----------------------------------------------------------------- impersonation

    @Test
    fun `a stranger copying the public advert is not auto admitted`() = runTest {
        val sim = formGroup(2)
        val host = sim.nodeOf("AAAA")
        val advertGroupId = host.manager.state.value.group!!.groupId

        // Right group id, fresh member id: exactly what anyone scanning can forge.
        host.manager.onConnectionRequest(
            "EP_EVIL",
            GroupAdvert(advertGroupId, "EVIL", GroupRole.MEMBER, "Evil"),
            "7777"
        )

        assertEquals(MemberState.PENDING_APPROVAL, host.manager.state.value.member("EVIL")?.state)
        assertFalse(host.acceptCalls.contains("EP_EVIL"))
    }

    @Test
    fun `impersonating a member who is already connected is rejected`() = runTest {
        val sim = formGroup(3)
        val host = sim.nodeOf("AAAA")
        val groupId = host.manager.state.value.group!!.groupId

        host.manager.onConnectionRequest(
            "EP_EVIL",
            GroupAdvert(groupId, "BBBB", GroupRole.MEMBER, "Phone-BBBB"),
            "7777"
        )

        assertTrue(host.rejectCalls.contains("EP_EVIL"))
        assertEquals(sim.endpointFor("BBBB"), host.manager.state.value.member("BBBB")?.endpointId)
        assertEquals(MemberState.CONNECTED, host.manager.state.value.member("BBBB")?.state)
    }

    @Test
    fun `impersonating an offline member fails the membership proof`() = runTest {
        val sim = formGroup(3)
        sim.killNode("CCCC")
        val host = sim.nodeOf("AAAA")
        val groupId = host.manager.state.value.group!!.groupId

        // The real CCCC is gone, so the link is let in on the strength of the roster...
        host.manager.onConnectionRequest(
            "EP_EVIL",
            GroupAdvert(groupId, "CCCC", GroupRole.MEMBER, "Phone-CCCC"),
            "7777"
        )
        host.manager.onConnected("EP_EVIL")
        assertEquals(MemberState.VERIFYING, host.manager.state.value.member("CCCC")?.state)

        // ...but without the group secret there is no valid proof to give.
        host.manager.onControlMessage("EP_EVIL", GroupMessage.MembershipProof("CCCC", "00".repeat(32)))

        val impostor = host.manager.state.value.member("CCCC")
        assertEquals(MemberState.FAILED, impostor?.state)
        assertFalse(host.manager.state.value.isTrustedEndpoint("EP_EVIL"))
    }

    @Test
    fun `an impostor that never sends a proof times out`() = runTest {
        val sim = formGroup(3)
        sim.killNode("CCCC")
        val host = sim.nodeOf("AAAA")
        val groupId = host.manager.state.value.group!!.groupId

        host.manager.onConnectionRequest(
            "EP_EVIL",
            GroupAdvert(groupId, "CCCC", GroupRole.MEMBER, "Phone-CCCC"),
            "7777"
        )
        host.manager.onConnected("EP_EVIL")
        sim.now.advance(GroupSessionManager.CONNECT_TIMEOUT_MS + 1_000)
        host.manager.tick()

        assertEquals(MemberState.FAILED, host.manager.state.value.member("CCCC")?.state)
    }

    @Test
    fun `an unverified link cannot inject control messages`() = runTest {
        val sim = formGroup(3)
        sim.killNode("CCCC")
        val host = sim.nodeOf("AAAA")
        val b = sim.nodeOf("BBBB")
        val group = b.manager.state.value.group!!

        b.manager.onConnectionRequest(
            "EP_EVIL",
            GroupAdvert(group.groupId, "CCCC", GroupRole.MEMBER, "Phone-CCCC"),
            "7777"
        )
        b.manager.onConnected("EP_EVIL")

        // Still VERIFYING: a takeover claim and a forged roster must both be ignored.
        b.manager.onControlMessage(
            "EP_EVIL",
            GroupMessage.CoordinatorClaim("CCCC", group.groupId, group.epoch + 50)
        )
        b.manager.onControlMessage(
            "EP_EVIL",
            GroupMessage.Roster(group.groupId, group.epoch + 50, emptyList())
        )

        val after = b.manager.state.value
        assertEquals("AAAA", after.group?.coordinatorId)
        assertNotNull("roster must not be wiped", after.member("AAAA"))
        assertTrue(host.manager.state.value.isCoordinator)
    }

    @Test
    fun `a roster from anyone but the coordinator is ignored`() = runTest {
        val sim = formGroup(3)
        val c = sim.nodeOf("CCCC")
        val group = c.manager.state.value.group!!

        // BBBB is a verified member, but not the coordinator.
        c.manager.onControlMessage(
            sim.endpointFor("BBBB"),
            GroupMessage.Roster(group.groupId, group.epoch, emptyList())
        )

        assertNotNull(c.manager.state.value.member("AAAA"))
        assertNotNull(c.manager.state.value.member("BBBB"))
    }

    // ------------------------------------------------ member to member needs no human

    @Test
    fun `members link to each other without further confirmation`() = runTest {
        val sim = formGroup(3)
        // BBBB and CCCC only confirmed codes with the host, yet they are linked and verified.
        assertTrue(sim.isLinked("BBBB", "CCCC"))
        assertEquals(
            MemberState.CONNECTED,
            sim.nodeOf("BBBB").manager.state.value.member("CCCC")?.state
        )
        val humanChecked = sim.nodeOf("BBBB").manager.state.value.member("CCCC")?.humanVerified
        assertFalse("proved by secret, not by a person", humanChecked == true)
    }

    @Test
    fun `a returning member re-verifies by proof`() = runTest {
        val sim = formGroup(3)
        sim.killNode("CCCC")
        sim.reviveNode("CCCC")
        sim.settle(rounds = 8, advanceMs = GroupSessionManager.RETRY_BACKOFF_MS + 500, autoApprove = false)

        assertEquals(
            "rejoining must not need people to confirm codes again",
            MemberState.CONNECTED,
            sim.nodeOf("AAAA").manager.state.value.member("CCCC")?.state
        )
    }

    // -------------------------------------------------------------------------- relay

    @Test
    fun `a member out of direct range is reached through a relay`() = runTest {
        val sim = MeshSimulator()
        listOf("AAAA", "BBBB", "CCCC").forEach { sim.addNode(it) }
        // A line: AAAA <-> BBBB <-> CCCC, with AAAA and CCCC unable to hear each other.
        sim.setOutOfRange("AAAA", "CCCC")

        val host = sim.nodeOf("BBBB")
        host.manager.createGroup("Line")
        sim.discoverAll()
        sim.nodeOf("AAAA").manager.joinGroup(host.advert!!, host.endpointId)
        sim.nodeOf("CCCC").manager.joinGroup(host.advert!!, host.endpointId)
        // Enough rounds for heartbeats to spread the neighbour lists.
        sim.settle(rounds = 8, advanceMs = GroupSessionManager.HEARTBEAT_INTERVAL_MS)

        assertFalse(sim.isLinked("AAAA", "CCCC"))
        val a = sim.nodeOf("AAAA").manager.state.value
        val cSeenFromA = a.member("CCCC")
        assertNotNull(cSeenFromA)
        assertTrue("C should be reachable through B", cSeenFromA!!.reachableViaRelay)
        assertEquals("A counts all three as reachable", 3, a.liveMemberCount)

        // And B, as the only bridge, is the one that relays A's voice to C.
        val bNeighbors = sim.nodeOf("BBBB").manager.state.value.neighbors
        assertEquals(setOf("CCCC"), RelayPlanner.relayTargets("BBBB", "AAAA", bNeighbors))
    }

    // ------------------------------------------------------------------------- proofs

    @Test
    fun `proof verifies for the right secret, pair and link`() {
        val secret = GroupProof.newSecret()
        val proof = GroupProof.compute(secret, "BBBB", "AAAA", "1234")
        assertTrue(GroupProof.verify(secret, "BBBB", "AAAA", "1234", proof))
    }

    @Test
    fun `proof fails with a different secret`() {
        val proof = GroupProof.compute(GroupProof.newSecret(), "BBBB", "AAAA", "1234")
        assertFalse(GroupProof.verify(GroupProof.newSecret(), "BBBB", "AAAA", "1234", proof))
    }

    @Test
    fun `proof is bound to the link code so it cannot be relayed`() {
        val secret = GroupProof.newSecret()
        val proof = GroupProof.compute(secret, "BBBB", "AAAA", "1234")
        assertFalse(GroupProof.verify(secret, "BBBB", "AAAA", "9999", proof))
    }

    @Test
    fun `proof cannot be reflected back at its sender`() {
        val secret = GroupProof.newSecret()
        val proofFromB = GroupProof.compute(secret, "BBBB", "AAAA", "1234")
        val proofFromA = GroupProof.compute(secret, "AAAA", "BBBB", "1234")
        assertNotEquals(proofFromA, proofFromB)
        assertFalse(GroupProof.verify(secret, "AAAA", "BBBB", "1234", proofFromB))
    }

    @Test
    fun `secret survives a hex round trip`() {
        val secret = GroupProof.newSecret()
        val back = with(GroupProof) { fromHex(secret.toHex()) }
        assertTrue(secret.contentEquals(back))
    }
}
