package com.example.offlinewalkietalkie.group

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSessionManagerTest {

    /** Builds a group of [size] nodes, first one hosting, and settles the mesh. */
    private suspend fun formGroup(size: Int, name: String = "Field Ops"): MeshSimulator {
        val sim = MeshSimulator()
        val ids = memberIds(size)
        ids.forEach { sim.addNode(it) }

        val host = sim.nodeOf(ids.first())
        host.manager.createGroup(name)

        // Everyone else has to see the host advertising before they can join.
        sim.discoverAll()
        ids.drop(1).forEach { id ->
            val joiner = sim.nodeOf(id)
            val hostAdvert = host.advert!!
            joiner.manager.joinGroup(hostAdvert, host.endpointId)
        }
        sim.settle(rounds = 8)
        return sim
    }

    /** Ids chosen so sort order is obvious: AAAA < BBBB < CCCC ... */
    private fun memberIds(size: Int): List<String> =
        (0 until size).map { ('A' + it).toString().repeat(4) }

    // ------------------------------------------------------------------ group forming

    @Test
    fun `two devices form a group with one coordinator`() = runTest {
        val sim = formGroup(2)
        val (a, b) = memberIds(2).map { sim.nodeOf(it) }

        assertTrue("host should be coordinator", a.manager.state.value.isCoordinator)
        assertFalse("joiner should not be coordinator", b.manager.state.value.isCoordinator)
        assertEquals(2, a.manager.state.value.liveMemberCount)
        assertEquals(2, b.manager.state.value.liveMemberCount)
        assertTrue(sim.isLinked("AAAA", "BBBB"))
    }

    @Test
    fun `three devices all reach each other`() = runTest {
        val sim = formGroup(3)
        memberIds(3).forEach { id ->
            val state = sim.nodeOf(id).manager.state.value
            assertEquals("$id should see 3 live members", 3, state.liveMemberCount)
        }
        assertTrue(sim.isLinked("AAAA", "BBBB"))
        assertTrue(sim.isLinked("AAAA", "CCCC"))
        assertTrue(sim.isLinked("BBBB", "CCCC"))
    }

    @Test
    fun `four devices all reach each other`() = runTest {
        val sim = formGroup(4)
        memberIds(4).forEach { id ->
            assertEquals(
                "$id should see 4 live members",
                4,
                sim.nodeOf(id).manager.state.value.liveMemberCount
            )
        }
        // Full mesh across 4 nodes is 6 links.
        assertEquals(6, sim.linkCount())
    }

    @Test
    fun `six devices all reach each other`() = runTest {
        val sim = formGroup(6)
        memberIds(6).forEach { id ->
            assertEquals(
                "$id should see 6 live members",
                6,
                sim.nodeOf(id).manager.state.value.liveMemberCount
            )
        }
        assertEquals(15, sim.linkCount())
    }

    @Test
    fun `every member agrees on the same group and coordinator`() = runTest {
        val sim = formGroup(5)
        val states = memberIds(5).map { sim.nodeOf(it).manager.state.value }
        val groupIds = states.map { it.group?.groupId }.distinct()
        val coordinators = states.map { it.group?.coordinatorId }.distinct()

        assertEquals("one group id across all devices", 1, groupIds.size)
        assertEquals("one coordinator across all devices", 1, coordinators.size)
        assertEquals("AAAA", coordinators.single())
    }

    // ------------------------------------------------------------------- arbitration

    @Test
    fun `no duplicate links form when both sides discover each other`() = runTest {
        val sim = formGroup(4)
        // A full mesh of 4 is 6 links; a duplicate would push this higher.
        assertEquals(6, sim.linkCount())

        // Re-running discovery must not create another link or another dial.
        val dialsBefore = sim.allNodes().sumOf { it.connectCalls.size }
        sim.settle(rounds = 4)
        val dialsAfter = sim.allNodes().sumOf { it.connectCalls.size }

        assertEquals("no extra links", 6, sim.linkCount())
        assertEquals("no redundant connect attempts", dialsBefore, dialsAfter)
    }

    @Test
    fun `only the lower member id initiates a link`() = runTest {
        val sim = formGroup(3)
        // CCCC has the highest id, so it should never dial AAAA or BBBB itself.
        val c = sim.nodeOf("CCCC")
        assertTrue(
            "highest id should not initiate, but dialled ${c.connectCalls}",
            c.connectCalls.none { it == sim.endpointFor("BBBB") }
        )
    }

    // ------------------------------------------------------------ joining and leaving

    @Test
    fun `a device joining later learns the whole roster`() = runTest {
        val sim = formGroup(3)
        val host = sim.nodeOf("AAAA")

        val late = sim.addNode("DDDD")
        sim.discoverAll()
        late.manager.joinGroup(host.advert!!, host.endpointId)
        sim.settle(rounds = 8)

        val state = late.manager.state.value
        assertEquals("late joiner should see everyone", 4, state.liveMemberCount)
        assertNotNull(state.member("BBBB"))
        assertNotNull(state.member("CCCC"))
    }

    @Test
    fun `leaving removes the member everywhere`() = runTest {
        val sim = formGroup(3)
        sim.nodeOf("CCCC").manager.leaveGroup()
        sim.settle(rounds = 6)

        val hostState = sim.nodeOf("AAAA").manager.state.value
        assertEquals(2, hostState.liveMemberCount)
        assertNull("departed member should be gone", hostState.member("CCCC"))

        val bState = sim.nodeOf("BBBB").manager.state.value
        assertNull("peers should drop the departed member too", bState.member("CCCC"))
    }

    @Test
    fun `leaving clears local group state`() = runTest {
        val sim = formGroup(2)
        val b = sim.nodeOf("BBBB")
        b.manager.leaveGroup()
        assertFalse(b.manager.state.value.isInGroup)
        assertNull(b.manager.state.value.group)
    }

    // -------------------------------------------------------- dropouts and reconnects

    @Test
    fun `a dropped member is marked reconnecting not forgotten`() = runTest {
        val sim = formGroup(3)
        sim.killNode("CCCC")

        val hostState = sim.nodeOf("AAAA").manager.state.value
        val dropped = hostState.member("CCCC")
        assertNotNull("member should still be on the roster", dropped)
        assertEquals(MemberState.RECONNECTING, dropped!!.state)
    }

    @Test
    fun `a member that comes back is reconnected`() = runTest {
        val sim = formGroup(3)
        sim.killNode("CCCC")
        assertEquals(2, sim.nodeOf("AAAA").manager.state.value.liveMemberCount)

        sim.reviveNode("CCCC")
        sim.settle(rounds = 8, advanceMs = GroupSessionManager.RETRY_BACKOFF_MS + 500)

        assertEquals(
            "member should be live again",
            3,
            sim.nodeOf("AAAA").manager.state.value.liveMemberCount
        )
    }

    @Test
    fun `attempts stop after the retry budget and surface a reason`() = runTest {
        val sim = formGroup(3)
        sim.killNode("CCCC")
        // Never revived, so every retry times out.
        sim.settle(
            rounds = 12,
            advanceMs = GroupSessionManager.CONNECT_TIMEOUT_MS + 1_000
        )

        val dropped = sim.nodeOf("AAAA").manager.state.value.member("CCCC")
        assertNotNull(dropped)
        assertEquals(MemberState.FAILED, dropped!!.state)
        assertNotNull("failure should carry a reason for the UI", dropped.failureReason)
    }

    // ------------------------------------------------------------ coordinator failover

    @Test
    fun `coordinator loss promotes the lowest surviving member`() = runTest {
        val sim = formGroup(4)
        sim.killNode("AAAA")
        sim.settle(rounds = 10, advanceMs = GroupSessionManager.COORDINATOR_TIMEOUT_MS + 1_000)

        val b = sim.nodeOf("BBBB").manager.state.value
        assertTrue("lowest surviving id should take over", b.isCoordinator)

        // And the others must agree.
        listOf("CCCC", "DDDD").forEach { id ->
            val state = sim.nodeOf(id).manager.state.value
            assertEquals("$id should follow the new coordinator", "BBBB", state.group?.coordinatorId)
            assertFalse("$id should not also claim", state.isCoordinator)
        }
    }

    @Test
    fun `failover bumps the epoch so stale coordinators lose`() = runTest {
        val sim = formGroup(3)
        val epochBefore = sim.nodeOf("BBBB").manager.state.value.group!!.epoch

        sim.killNode("AAAA")
        sim.settle(rounds = 10, advanceMs = GroupSessionManager.COORDINATOR_TIMEOUT_MS + 1_000)

        val epochAfter = sim.nodeOf("BBBB").manager.state.value.group!!.epoch
        assertTrue("epoch must increase on failover", epochAfter > epochBefore)
    }

    @Test
    fun `group survives coordinator loss with voice peers still linked`() = runTest {
        val sim = formGroup(4)
        sim.killNode("AAAA")
        sim.settle(rounds = 10, advanceMs = GroupSessionManager.COORDINATOR_TIMEOUT_MS + 1_000)

        // The three survivors must still be a usable group for voice.
        listOf("BBBB", "CCCC", "DDDD").forEach { id ->
            assertEquals(
                "$id should still see the other survivors",
                3,
                sim.nodeOf(id).manager.state.value.liveMemberCount
            )
        }
    }

    /**
     * Caught on hardware: a joiner that could never reach the host promoted itself after
     * the coordinator timeout, turning one failed join into two rival single-device groups.
     */
    @Test
    fun `a joiner that never reaches the host does not promote itself`() = runTest {
        val sim = MeshSimulator()
        val host = sim.addNode("AAAA")
        val joiner = sim.addNode("BBBB")

        host.manager.createGroup("Field Ops")
        sim.discoverAll()

        // Host drops off the air before the join can complete.
        host.reachable = false
        joiner.manager.joinGroup(
            GroupAdvert("GGGG", "AAAA", GroupRole.COORDINATOR, "Field Ops"),
            host.endpointId
        )
        sim.settle(rounds = 12, advanceMs = GroupSessionManager.COORDINATOR_TIMEOUT_MS + 1_000)

        val state = joiner.manager.state.value
        assertFalse(
            "joiner must not crown itself after a failed join",
            state.isCoordinator
        )
        assertEquals("AAAA", state.group?.coordinatorId)
    }

    @Test
    fun `a member that did connect still fails over when the host dies`() = runTest {
        // The guard above must not disable genuine failover.
        val sim = formGroup(3)
        sim.killNode("AAAA")
        sim.settle(rounds = 10, advanceMs = GroupSessionManager.COORDINATOR_TIMEOUT_MS + 1_000)
        assertTrue(sim.nodeOf("BBBB").manager.state.value.isCoordinator)
    }

    @Test
    fun `a stale coordinator claim at a lower epoch is ignored`() = runTest {
        val sim = formGroup(3)
        val c = sim.nodeOf("CCCC")
        val group = c.manager.state.value.group!!

        c.manager.onControlMessage(
            sim.nodeOf("AAAA").endpointId,
            GroupMessage.CoordinatorClaim("ZZZZ", group.groupId, group.epoch - 1)
        )

        assertEquals(
            "coordinator should be unchanged",
            "AAAA",
            c.manager.state.value.group?.coordinatorId
        )
    }

    @Test
    fun `a claim for a different group is ignored`() = runTest {
        val sim = formGroup(2)
        val b = sim.nodeOf("BBBB")
        val before = b.manager.state.value.group!!.coordinatorId

        b.manager.onControlMessage(
            sim.nodeOf("AAAA").endpointId,
            GroupMessage.CoordinatorClaim("ZZZZ", "OTHERGRP", 99)
        )

        assertEquals(before, b.manager.state.value.group?.coordinatorId)
    }

    // ----------------------------------------------------------------- group isolation

    @Test
    fun `adverts from a different group are ignored`() = runTest {
        val sim = formGroup(2)
        val a = sim.nodeOf("AAAA")
        val before = a.manager.state.value.members.size

        a.manager.onEndpointDiscovered(
            "EP_STRANGER",
            GroupAdvert("OTHERGRP", "ZZZZ", GroupRole.COORDINATOR, "Someone else")
        )

        assertEquals("stranger must not join our roster", before, a.manager.state.value.members.size)
    }

    @Test
    fun `a request from another group needs explicit approval`() = runTest {
        val sim = formGroup(2)
        val a = sim.nodeOf("AAAA")

        a.manager.onConnectionRequest(
            "EP_STRANGER",
            GroupAdvert("OTHERGRP", "ZZZZ", GroupRole.MEMBER, "Stranger")
        )

        val stranger = a.manager.state.value.member("ZZZZ")
        assertNotNull(stranger)
        assertEquals(MemberState.PENDING_APPROVAL, stranger!!.state)
        assertTrue("must not auto-accept", a.acceptCalls.none { it == "EP_STRANGER" })
    }

    @Test
    fun `rejecting a request drops the member`() = runTest {
        val sim = formGroup(2)
        val a = sim.nodeOf("AAAA")
        a.manager.onConnectionRequest(
            "EP_STRANGER",
            GroupAdvert("OTHERGRP", "ZZZZ", GroupRole.MEMBER, "Stranger")
        )
        a.manager.respondToRequest("ZZZZ", accept = false)
        assertNull(a.manager.state.value.member("ZZZZ"))
    }

    // ------------------------------------------------------------------- voice fanout

    @Test
    fun `every member is a voice target once the group is formed`() = runTest {
        val sim = formGroup(5)
        // Voice is broadcast to every live peer, so each node must have 4 of them.
        memberIds(5).forEach { id ->
            val connected = sim.nodeOf(id).manager.state.value.connected
            assertEquals("$id should have 4 voice peers", 4, connected.size)
            assertTrue(
                "$id voice peers must all be addressable",
                connected.all { it.endpointId != null }
            )
        }
    }

    @Test
    fun `one to one behaviour still works`() = runTest {
        val sim = formGroup(2)
        val a = sim.nodeOf("AAAA").manager.state.value
        val b = sim.nodeOf("BBBB").manager.state.value

        assertEquals(1, a.connected.size)
        assertEquals(1, b.connected.size)
        assertEquals("BBBB", a.connected.single().memberId)
        assertEquals("AAAA", b.connected.single().memberId)
    }
}
