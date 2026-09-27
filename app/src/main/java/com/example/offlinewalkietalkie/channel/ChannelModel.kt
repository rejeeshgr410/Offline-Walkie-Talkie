package com.example.offlinewalkietalkie.channel

import java.util.UUID

enum class MemberRole {
    HOST,
    MEMBER,
    RELAY
}

enum class RelayPolicy {
    OFF, // P2P_STAR (Default for direct star topology)
    ON   // P2P_CLUSTER (Enables mesh relaying §19.5)
}

data class ChannelMember(
    val memberId: Long,
    val displayName: String,
    val role: MemberRole,
    val endpointId: String? = null,
    val isDirect: Boolean = true,
    val isOnline: Boolean = true,
    val joinedEpochMs: Long = System.currentTimeMillis()
)

data class ChannelInfo(
    val channelId: Long,
    val name: String,
    val hostMemberId: Long,
    val hostFingerprint: String,
    val relayPolicy: RelayPolicy = RelayPolicy.OFF,
    val keyEpoch: Int = 1
)

enum class TextDeliveryStatus {
    SENDING,
    DELIVERED,
    FAILED
}

data class TextMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val sequence: Int,
    val senderMemberId: Long,
    val senderName: String,
    val content: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val status: TextDeliveryStatus = TextDeliveryStatus.SENDING
)

data class ReplayTransmission(
    val id: String = UUID.randomUUID().toString(),
    val senderMemberId: Long,
    val senderName: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val frames: List<ByteArray>
)
