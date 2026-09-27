package com.example.offlinewalkietalkie.group

/**
 * What a device broadcasts about itself during discovery, packed into the Nearby
 * endpoint name so a peer knows which group an endpoint belongs to *before* connecting.
 *
 * Wire form is fixed-width with no separators:
 *
 *     T <groupId:4> <memberId:4> <role:1> <shortName:0..8>
 *
 * Size matters more than readability here. Nearby carries the endpoint name inside the
 * BLE advertisement for P2P_CLUSTER, and once the name pushes the packet past the legacy
 * 31-byte limit the endpoint stops being discoverable by devices without BLE 5 extended
 * advertising. Keeping the whole thing at 18 bytes leaves that headroom.
 *
 * Only a short form of the group name travels here, purely so the join list has something
 * to show; the full name arrives in [GroupMessage.Welcome] once a link is up.
 */
data class GroupAdvert(
    val groupId: String,
    val memberId: String,
    val role: GroupRole,
    val displayName: String
) {
    fun encode(): String {
        val roleChar = if (role == GroupRole.COORDINATOR) "C" else "M"
        val shortName = displayName.filter { it.code in 32..126 }.take(MAX_NAME)
        return PREFIX + groupId.take(GROUP_ID_LENGTH).padEnd(GROUP_ID_LENGTH, PAD) +
            memberId.take(MEMBER_ID_LENGTH).padEnd(MEMBER_ID_LENGTH, PAD) +
            roleChar + shortName
    }

    companion object {
        const val PREFIX = "T"
        const val GROUP_ID_LENGTH = 4
        const val MEMBER_ID_LENGTH = 4
        const val MAX_NAME = 8
        private const val PAD = '0'

        private const val ROLE_INDEX = PREFIX.length + GROUP_ID_LENGTH + MEMBER_ID_LENGTH
        private const val MIN_LENGTH = ROLE_INDEX + 1

        /** Longest endpoint name we will ever publish. */
        const val MAX_ENCODED_LENGTH = MIN_LENGTH + MAX_NAME

        /**
         * Returns null for anything that is not one of our adverts — a peer running an
         * older build, or an unrelated app sharing the service id.
         */
        fun decode(endpointName: String?): GroupAdvert? {
            if (endpointName == null) return null
            if (endpointName.length < MIN_LENGTH) return null
            if (!endpointName.startsWith(PREFIX)) return null

            val groupId = endpointName.substring(PREFIX.length, PREFIX.length + GROUP_ID_LENGTH)
            val memberId = endpointName.substring(
                PREFIX.length + GROUP_ID_LENGTH,
                ROLE_INDEX
            )
            val role = when (endpointName[ROLE_INDEX]) {
                'C' -> GroupRole.COORDINATOR
                'M' -> GroupRole.MEMBER
                else -> return null
            }
            if (groupId.isBlank() || memberId.isBlank()) return null

            val name = endpointName.substring(MIN_LENGTH).ifBlank { memberId }
            return GroupAdvert(groupId, memberId, role, name)
        }
    }
}
