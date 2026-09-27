package com.example.offlinewalkietalkie.group

/**
 * Control messages exchanged over established links to keep group membership in sync.
 *
 * These are low-rate (roster changes and a heartbeat every few seconds), so a compact
 * text encoding is used rather than a packed binary one — it stays debuggable in logs
 * and diffable in tests. Voice never travels this path.
 */
sealed class GroupMessage {

    /**
     * Coordinator -> joiner, sent only after a person confirmed the link code on both
     * phones. Carries the group secret that later links use to prove membership without
     * another human check. Nearby encrypts every payload on an accepted connection, and
     * the confirmed code rules out a man in the middle, so this is the one safe moment to
     * hand the secret over.
     */
    data class Welcome(
        val groupId: String,
        val groupName: String,
        val coordinatorId: String,
        val epoch: Int,
        val secretHex: String
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

    /**
     * Liveness beacon. Absence of these is what triggers a failover. [neighbors] lists the
     * members the sender has a verified direct link to, so every device can work out who
     * needs a relay to be reached.
     */
    data class Heartbeat(
        val memberId: String,
        val epoch: Int,
        val neighbors: List<String> = emptyList()
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

    /**
     * Proof that the sender holds the group secret, bound to this one link's Nearby code
     * so it cannot be replayed or relayed onto a different connection.
     */
    data class MembershipProof(
        val memberId: String,
        val proofHex: String
    ) : GroupMessage()

    fun encode(): ByteArray = when (this) {
        is Welcome -> join(T_WELCOME, groupId, groupName, coordinatorId, epoch.toString(), secretHex)
        is JoinRequest -> join(T_JOIN, memberId, displayName)
        is Roster -> join(
            T_ROSTER,
            groupId,
            epoch.toString(),
            entries.joinToString(ENTRY_SEP) { it.encode() }
        )
        is Heartbeat -> join(T_HEARTBEAT, memberId, epoch.toString(), neighbors.joinToString(ENTRY_SEP))
        is CoordinatorClaim -> join(T_CLAIM, memberId, groupId, epoch.toString())
        is Leave -> join(T_LEAVE, memberId)
        is MembershipProof -> join(T_PROOF, memberId, proofHex)
    }.toByteArray(Charsets.UTF_8)

    companion object {
        // ASCII unit/record/group separators: never typed by users, so names cannot collide.
        private const val SEP = "\u001F"
        private const val ENTRY_SEP = "\u001E"
        private const val FIELD_SEP = "\u001D"

        private const val T_WELCOME = "W"
        private const val T_JOIN = "J"
        private const val T_ROSTER = "R"
        private const val T_HEARTBEAT = "H"
        private const val T_CLAIM = "C"
        private const val T_LEAVE = "L"
        private const val T_PROOF = "P"

        private fun join(vararg parts: String) = parts.joinToString(SEP)

        private fun splitList(text: String): List<String> =
            text.split(ENTRY_SEP).filter { it.isNotBlank() }

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
                    T_WELCOME -> if (parts.size < 6) null else Welcome(
                        groupId = parts[1],
                        groupName = parts[2],
                        coordinatorId = parts[3],
                        epoch = parts[4].toInt(),
                        secretHex = parts[5]
                    )
                    T_JOIN -> if (parts.size < 3) null else JoinRequest(
                        memberId = parts[1],
                        displayName = parts[2]
                    )
                    T_ROSTER -> if (parts.size < 4) null else Roster(
                        groupId = parts[1],
                        epoch = parts[2].toInt(),
                        entries = splitList(parts[3]).mapNotNull { RosterEntry.decode(it, FIELD_SEP) }
                    )
                    T_HEARTBEAT -> if (parts.size < 3) null else Heartbeat(
                        memberId = parts[1],
                        epoch = parts[2].toInt(),
                        neighbors = if (parts.size >= 4) splitList(parts[3]) else emptyList()
                    )
                    T_CLAIM -> if (parts.size < 4) null else CoordinatorClaim(
                        memberId = parts[1],
                        groupId = parts[2],
                        epoch = parts[3].toInt()
                    )
                    T_LEAVE -> if (parts.size < 2) null else Leave(memberId = parts[1])
                    T_PROOF -> if (parts.size < 3) null else MembershipProof(
                        memberId = parts[1],
                        proofHex = parts[2]
                    )
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
