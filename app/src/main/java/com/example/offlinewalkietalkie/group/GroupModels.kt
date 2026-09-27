package com.example.offlinewalkietalkie.group

/** Where a member sits in the group. */
enum class GroupRole {
    /** Owns the roster and admits newcomers. Exactly one per group. */
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

    /** A link is being opened. */
    CONNECTING,

    /**
     * A first-time link that a person has to confirm by comparing the code shown on both
     * phones. Nothing about the group is trusted until they do.
     */
    PENDING_APPROVAL,

    /** Link is up; waiting for the peer to prove it holds the group secret. */
    VERIFYING,

    /** Link is up and membership is proven. Voice can flow. */
    CONNECTED,

    /** Link dropped but the member is still on the roster; retry is scheduled. */
    RECONNECTING,

    /** Attempts exhausted, verification failed, or the peer refused. Carries a reason. */
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
        this == MemberState.VERIFYING ||
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
    /** No direct link, but a live neighbour of ours can reach them, so voice still gets there. */
    val reachableViaRelay: Boolean = false,
    /** Listed in the coordinator's roster, which is what lets later links skip the human check. */
    val onRoster: Boolean = false,
    /** The code both phones display for this link; compared by people on first contact. */
    val authCode: String? = null,
    /** A person confirmed [authCode] for the current link. */
    val humanVerified: Boolean = false,
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
    /**
     * memberId -> the members it has a verified direct link to, as last reported in its
     * heartbeat (our own entry is kept current locally). Drives relay decisions.
     */
    val neighbors: Map<String, Set<String>> = emptyMap(),
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

    val relayed: List<GroupMember> get() = others.filter { !it.state.isLive && it.reachableViaRelay }

    val pending: List<GroupMember> get() = others.filter { it.state.isTransitional }

    val awaitingApproval: List<GroupMember>
        get() = others.filter { it.state == MemberState.PENDING_APPROVAL }

    val failed: List<GroupMember> get() = others.filter { it.state == MemberState.FAILED }

    val coordinator: GroupMember? get() = members.find { it.memberId == group?.coordinatorId }

    /** Us plus everyone voice can reach, directly or through a relay. */
    val liveMemberCount: Int get() = connected.size + relayed.size + 1

    fun member(memberId: String): GroupMember? = members.find { it.memberId == memberId }

    fun memberByEndpoint(endpointId: String): GroupMember? =
        members.find { it.endpointId == endpointId }

    /** Only verified members may carry voice, text or control traffic. */
    fun isTrustedEndpoint(endpointId: String): Boolean =
        memberByEndpoint(endpointId)?.state == MemberState.CONNECTED
}
