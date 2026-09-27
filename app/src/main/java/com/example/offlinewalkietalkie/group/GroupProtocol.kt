package com.example.offlinewalkietalkie.group

/**
 * Control messages exchanged over established links to keep group membership in sync.
 *
 * These are low-rate (roster changes and a heartbeat every few seconds), so a compact
 * text encoding is used rather than a packed binary one — it stays debuggable in logs
 * and diffable in tests. Voice never travels this path.
 */
sealed class GroupMessage {

    /** Coordinator -> joiner: you are in, here is the group. */
    data class Welcome(
        val groupId: String,
        val groupName: String,
        val coordinatorId: String,
        val epoch: Int
    ) : GroupMessage()

    /** Joiner -> coordinator: requesting membership. */
    data class JoinRequest(
        val memberId: String,
        val displayName: String
    ) : GroupMessage()

    /**
     * Coordinator -> everyone: the authoritative membership list. Members apply it
     * wholesale, which is what lets a late joiner learn about peers it never discovered.
     */
    data class Roster(
        val groupId: String,
        val epoch: Int,
        val entries: List<RosterEntry>
    ) : GroupMessage()

    /** Liveness beacon. Absence of these is what triggers a failover. */
    data class Heartbeat(
        val memberId: String,
        val epoch: Int
    ) : GroupMessage()

    /**
     * Sent when a member decides the coordinator is gone and takes over. The highest
     * epoch wins; equal epochs are broken by the lowest member id so two simultaneous
     * claims always converge on the same winner.
     */
    data class CoordinatorClaim(
        val memberId: String,
        val groupId: String,
        val epoch: Int
    ) : GroupMessage()

    /** Deliberate departure, so peers can drop the member without waiting for a timeout. */
    data class Leave(val memberId: String) : GroupMessage()

    fun encode(): ByteArray = when (this) {
        is Welcome -> join(T_WELCOME, groupId, groupName, coordinatorId, epoch.toString())
        is JoinRequest -> join(T_JOIN, memberId, displayName)
        is Roster -> join(
            T_ROSTER,
            groupId,
            epoch.toString(),
            entries.joinToString(ENTRY_SEP) { it.encode() }
        )
        is Heartbeat -> join(T_HEARTBEAT, memberId, epoch.toString())
        is CoordinatorClaim -> join(T_CLAIM, memberId, groupId, epoch.toString())
        is Leave -> join(T_LEAVE, memberId)
    }.toByteArray(Charsets.UTF_8)

    companion object {
        private const val SEP = ""
        private const val ENTRY_SEP = ""
        private const val FIELD_SEP = ""

        private const val T_WELCOME = "W"
        private const val T_JOIN = "J"
        private const val T_ROSTER = "R"
        private const val T_HEARTBEAT = "H"
        private const val T_CLAIM = "C"
        private const val T_LEAVE = "L"

        private fun join(vararg parts: String) = parts.joinToString(SEP)

        fun decode(bytes: ByteArray): GroupMessage? {
            val text = try {
                String(bytes, Charsets.UTF_8)
            } catch (e: Throwable) {
                return null
            }
            val parts = text.split(SEP)
            if (parts.isEmpty()) return null

            return try {
                when (parts[0]) {
                    T_WELCOME -> if (parts.size < 5) null else Welcome(
                        groupId = parts[1],
                        groupName = parts[2],
                        coordinatorId = parts[3],
                        epoch = parts[4].toInt()
                    )
                    T_JOIN -> if (parts.size < 3) null else JoinRequest(
                        memberId = parts[1],
                        displayName = parts[2]
                    )
                    T_ROSTER -> if (parts.size < 4) null else Roster(
                        groupId = parts[1],
                        epoch = parts[2].toInt(),
                        entries = parts[3]
                            .split(ENTRY_SEP)
                            .filter { it.isNotBlank() }
                            .mapNotNull { RosterEntry.decode(it, FIELD_SEP) }
                    )
                    T_HEARTBEAT -> if (parts.size < 3) null else Heartbeat(
                        memberId = parts[1],
                        epoch = parts[2].toInt()
                    )
                    T_CLAIM -> if (parts.size < 4) null else CoordinatorClaim(
                        memberId = parts[1],
                        groupId = parts[2],
                        epoch = parts[3].toInt()
                    )
                    T_LEAVE -> if (parts.size < 2) null else Leave(memberId = parts[1])
                    else -> null
                }
            } catch (e: NumberFormatException) {
                null
            }
        }

        internal fun entryFieldSeparator() = FIELD_SEP
    }
}

/** One line of the coordinator's membership list. */
data class RosterEntry(
    val memberId: String,
    val displayName: String,
    val role: GroupRole
) {
    fun encode(separator: String = GroupMessage.entryFieldSeparator()): String {
        val roleChar = if (role == GroupRole.COORDINATOR) "C" else "M"
        return listOf(memberId, displayName, roleChar).joinToString(separator)
    }

    companion object {
        fun decode(text: String, separator: String): RosterEntry? {
            val f = text.split(separator)
            if (f.size < 3) return null
            val role = when (f[2]) {
                "C" -> GroupRole.COORDINATOR
                "M" -> GroupRole.MEMBER
                else -> return null
            }
            if (f[0].isBlank()) return null
            return RosterEntry(f[0], f[1], role)
        }
    }
}
