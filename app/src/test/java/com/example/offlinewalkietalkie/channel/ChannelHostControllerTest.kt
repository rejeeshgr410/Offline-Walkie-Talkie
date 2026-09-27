package com.example.offlinewalkietalkie.channel

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelHostControllerTest {

    private lateinit var hostController: ChannelHostController

    @Before
    fun setUp() {
        hostController = ChannelHostController(
            channelId = 987654321L,
            channelName = "Bravo Squad",
            hostFingerprint = "F1E2D3C4B5A6",
            hostDisplayName = "Commander"
        )
    }

    @Test
    fun testInitialHostRegistration() {
        val members = hostController.membersFlow.value
        assertEquals(1, members.size)
        assertEquals("Commander", members[0].displayName)
        assertEquals(MemberRole.HOST, members[0].role)
        assertEquals(1, hostController.currentKeyEpoch.value)
    }

    @Test
    fun testAdmittingMembersUpToSix() {
        var keyEpochRotations = 0
        var latestAuthorizedMembers: List<ChannelMember> = emptyList()

        hostController.onKeyEpochRotated = { epoch, key, authorized ->
            keyEpochRotations++
            latestAuthorizedMembers = authorized
        }

        // Admit 5 additional members (making 6 total including host)
        for (i in 1..5) {
            val secret = hostController.qrJoinManager.currentJoinData.value.oneTimeSecret
            val member = hostController.admitMember(
                displayName = "Member-$i",
                role = MemberRole.MEMBER,
                endpointId = "endpoint-$i",
                joinSecretOrCode = secret
            )
            assertNotNull("Member $i should be admitted", member)
            assertEquals("Member-$i", member!!.displayName)
            assertEquals(MemberRole.MEMBER, member.role)
        }

        assertEquals(6, hostController.membersFlow.value.size)
        assertEquals(5, keyEpochRotations)
        assertEquals(6, hostController.currentKeyEpoch.value) // Initial 1 + 5 rotations = 6
        assertEquals(6, latestAuthorizedMembers.size)
    }

    @Test
    fun testRelayRoleExcludedFromChannelVoiceKeys() {
        var latestAuthorizedMembers: List<ChannelMember> = emptyList()

        hostController.onKeyEpochRotated = { _, _, authorized ->
            latestAuthorizedMembers = authorized
        }

        val secret = hostController.qrJoinManager.currentJoinData.value.oneTimeSecret
        val relayNode = hostController.admitMember(
            displayName = "Relay-Node-B",
            role = MemberRole.RELAY,
            endpointId = "relay-endpoint",
            joinSecretOrCode = secret
        )
        assertNotNull(relayNode)
        assertEquals(MemberRole.RELAY, relayNode!!.role)

        // Relay node must NOT be in the authorized voice key recipients (§19.1 & §19.2)
        assertFalse(latestAuthorizedMembers.any { it.role == MemberRole.RELAY })
        assertTrue(latestAuthorizedMembers.any { it.role == MemberRole.HOST })
    }

    @Test
    fun testFloorControlContentionAndRelease() {
        val secret1 = hostController.qrJoinManager.currentJoinData.value.oneTimeSecret
        val member1 = hostController.admitMember("Alice", MemberRole.MEMBER, "ep1", secret1)!!

        val secret2 = hostController.qrJoinManager.currentJoinData.value.oneTimeSecret
        val member2 = hostController.admitMember("Bob", MemberRole.MEMBER, "ep2", secret2)!!

        var lastFloorHolderName = ""
        hostController.onFloorStatusChanged = { _, name ->
            lastFloorHolderName = name
        }

        // Alice requests floor -> Granted
        val granted1 = hostController.requestFloor(member1.memberId)
        assertTrue("Alice should be granted the floor", granted1)
        assertEquals("Alice", hostController.currentFloorHolder.value?.displayName)
        assertEquals("Alice", lastFloorHolderName)

        // Bob requests floor -> Denied (floor busy)
        val granted2 = hostController.requestFloor(member2.memberId)
        assertFalse("Bob should be denied while Alice talks", granted2)
        assertEquals("Alice", hostController.currentFloorHolder.value?.displayName)

        // Alice releases floor -> Free
        hostController.releaseFloor(member1.memberId)
        assertNull("Floor should be free after release", hostController.currentFloorHolder.value)
        assertEquals("", lastFloorHolderName)

        // Now Bob requests floor -> Granted
        val granted3 = hostController.requestFloor(member2.memberId)
        assertTrue("Bob should now be granted the floor", granted3)
        assertEquals("Bob", hostController.currentFloorHolder.value?.displayName)
    }

    @Test
    fun testMemberRemovalTriggersEpochRotationAndReleasesFloor() {
        val secret = hostController.qrJoinManager.currentJoinData.value.oneTimeSecret
        val member = hostController.admitMember("Charlie", MemberRole.MEMBER, "epC", secret)!!

        hostController.requestFloor(member.memberId)
        assertEquals(member.memberId, hostController.currentFloorHolder.value?.memberId)

        val epochBefore = hostController.currentKeyEpoch.value
        hostController.removeMember(member.memberId)

        // Epoch incremented to revoke departed member's access (§19.2)
        assertEquals(epochBefore + 1, hostController.currentKeyEpoch.value)
        // Floor auto-released
        assertNull(hostController.currentFloorHolder.value)
    }
}
