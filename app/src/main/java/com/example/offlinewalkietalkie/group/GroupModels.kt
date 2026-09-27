package com.example.offlinewalkietalkie.group

/** Where a member sits in the group. */
enum class GroupRole {
    /** Owns the roster and vouches for who is allowed in. Exactly one per group. */
    COORDINATOR,
    MEMBER
}

/**
 * Lifecycle of one member as seen from this device. Drives both the connection state
 * machine and what the roster UI shows.
 */
enum class MemberState {
    /** Advertising our group id but no link attempted yet. */
    DISCOVERED,

    /** We are the initiator and a connection is in flight. */
    CONNECTING,

    /** They asked us; waiting for the local user (or auto-accept) to answer. */
    PENDING_APPROVAL,

    /** Link is up and the key exchange finished. Voice can flow. */
    CONNECTED,

    /** Link dropped but the member is still on the roster; retry is scheduled. */
    RECONNECTING,

    /** Attempts exhausted, or the peer refused. Surfaced to the user with a reason. */
    FAILED,

    /** Left deliberately. Kept briefly so the UI can show it rather than blink out. */
    LEFT
}

/** True once a link is usable for voice. */
val MemberState.isLive: Boolean
    get() = this == MemberState.CONNECTED

/** True while something is in progress, so the UI can show a spinner. */
val MemberState.isTransitional: Boolean
    get() = this == MemberState.CONNECTING ||
        this == MemberState.PENDING_APPROVAL ||
        this == MemberState.RECONNECTING

data class GroupMember(
    val memberId: String,
    val displayName: String,
    /** Transport-level address. Null when the member is only known via the roster. */
    val endpointId: String? = null,
    val role: GroupRole = GroupRole.MEMBER,
    val state: MemberState = MemberState.DISCOVERED,
    /** False when we reach them through a relay rather than our own radio link. */
    val isDirect: Boolean = true,
    val failureReason: String? = null,
    val attempts: Int = 0,
    val lastSeenEpochMs: Long = 0L
)

/**
 * Identity of a group. [epoch] increments on every coordinator change so a stale
 * coordinator's messages can be recognised and ignored after a failover.
 */
data class GroupIdentity(
    val groupId: String,
    val name: String,
    val coordinatorId: String,
    val epoch: Int = 1
)

/**
 * A group seen in discovery that we are not part of. Only the coordinator's advert can
 * be joined, so this carries the endpoint needed to dial it.
 */
data class DiscoveredGroup(
    val groupId: String,
    val name: String,
    val coordinatorMemberId: String,
    val coordinatorEndpointId: String,
    /** How many devices of this group are currently visible, coordinator included. */
    val devicesVisible: Int
)

/** Everything the UI needs to describe the current group in one snapshot. */
data class GroupState(
    val group: GroupIdentity? = null,
    val localMemberId: String = "",
    val members: List<GroupMember> = emptyList(),
    /** Set while forming or joining, cleared once at least one link is live. */
    val isForming: Boolean = false,
    val lastError: String? = null
) {
    val isInGroup: Boolean get() = group != null

    val localRole: GroupRole
        get() = if (group?.coordinatorId == localMemberId) GroupRole.COORDINATOR else GroupRole.MEMBER

    val isCoordinator: Boolean get() = localRole == GroupRole.COORDINATOR

    /** Everyone except us. */
    val others: List<GroupMember> get() = members.filter { it.memberId != localMemberId }

    val connected: List<GroupMember> get() = others.filter { it.state.isLive }

    val pending: List<GroupMember> get() = others.filter { it.state.isTransitional }

    val failed: List<GroupMember> get() = others.filter { it.state == MemberState.FAILED }

    val coordinator: GroupMember? get() = members.find { it.memberId == group?.coordinatorId }

    /** Us plus everyone we can actually talk to. */
    val liveMemberCount: Int get() = connected.size + 1

    fun member(memberId: String): GroupMember? = members.find { it.memberId == memberId }

    fun memberByEndpoint(endpointId: String): GroupMember? =
        members.find { it.endpointId == endpointId }
}
