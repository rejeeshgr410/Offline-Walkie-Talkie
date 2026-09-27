package com.example.offlinewalkietalkie.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Inner Packet: End-to-End payload between channel members or 1:1 endpoints.
 * Conforms to Section 19.1 & Section 19.3 (Session-scoped 8-byte member ID).
 */
data class InnerPacket(
    val type: FrameType,
    val flags: Byte = 0,
    var hopLimit: Byte = 3,
    val channelId: Long = 0,
    val memberId: Long = 0, // Session-scoped random 8-byte ID (§19.3)
    val sequence: Int = 0,
    val timestamp: Int = 0,
    val payload: ByteArray = ByteArray(0),
    val authTag: ByteArray? = null
) {
    companion object {
        const val HEADER_SIZE = 1 + 1 + 1 + 8 + 8 + 4 + 4 + 2 // 29 bytes
        const val FLAG_ENCRYPTED: Byte = 0x01
        const val FLAG_EMERGENCY_SOS: Byte = 0x02
        const val FLAG_PRIORITY: Byte = 0x04

        fun decode(bytes: ByteArray): InnerPacket? {
            if (bytes.size < HEADER_SIZE) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            val typeByte = buf.get()
            val type = FrameType.fromValue(typeByte) ?: return null
            val flags = buf.get()
            val hopLimit = buf.get()
            val channelId = buf.getLong()
            val memberId = buf.getLong()
            val sequence = buf.getInt()
            val timestamp = buf.getInt()
            val payloadLen = buf.short.toInt() and 0xFFFF

            if (buf.remaining() < payloadLen) return null
            val payload = ByteArray(payloadLen)
            buf.get(payload)

            val isEncrypted = (flags.toInt() and FLAG_ENCRYPTED.toInt()) != 0
            val authTag = if (isEncrypted && buf.remaining() >= 16) {
                val tag = ByteArray(16)
                buf.get(tag)
                tag
            } else null

            return InnerPacket(
                type = type,
                flags = flags,
                hopLimit = hopLimit,
                channelId = channelId,
                memberId = memberId,
                sequence = sequence,
                timestamp = timestamp,
                payload = payload,
                authTag = authTag
            )
        }
    }

    fun isEncrypted(): Boolean = (flags.toInt() and FLAG_ENCRYPTED.toInt()) != 0
    fun isEmergency(): Boolean = (flags.toInt() and FLAG_EMERGENCY_SOS.toInt()) != 0

    fun encode(): ByteArray {
        val totalSize = HEADER_SIZE + payload.size + (authTag?.size ?: 0)
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buf.put(type.value)
        buf.put(flags)
        buf.put(hopLimit)
        buf.putLong(channelId)
        buf.putLong(memberId)
        buf.putInt(sequence)
        buf.putInt(timestamp)
        buf.putShort(payload.size.toShort())
        buf.put(payload)
        authTag?.let { buf.put(it) }

        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InnerPacket
        return type == other.type &&
                flags == other.flags &&
                hopLimit == other.hopLimit &&
                channelId == other.channelId &&
                memberId == other.memberId &&
                sequence == other.sequence &&
                timestamp == other.timestamp &&
                payload.contentEquals(other.payload) &&
                (authTag?.contentEquals(other.authTag ?: ByteArray(0)) ?: (other.authTag == null))
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + flags
        result = 31 * result + hopLimit
        result = 31 * result + channelId.hashCode()
        result = 31 * result + memberId.hashCode()
        result = 31 * result + sequence
        result = 31 * result + timestamp
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Outer Packet: Hop-by-Hop framing encrypted between immediate radio neighbors.
 * Conforms to Section 19.1.
 */
data class OuterPacket(
    val senderNodeId: Long,
    var hopLimit: Byte = 3,
    val nonce: ByteArray, // 12-byte nonce
    val ciphertext: ByteArray, // Encrypted inner packet
    val authTag: ByteArray // 16-byte Poly1305 / GCM tag
) {
    companion object {
        const val MAGIC: Short = 0x5403 // "T\x03" (TalkLoop V3)
        const val HEADER_SIZE = 2 + 2 + 8 + 1 + 12 // 25 bytes (magic + length + senderNodeId + hopLimit + nonce)
        const val TAG_SIZE = 16

        fun decode(bytes: ByteArray): OuterPacket? {
            if (bytes.size < HEADER_SIZE + TAG_SIZE) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            val magic = buf.short
            if (magic != MAGIC) return null

            val length = buf.short.toInt() and 0xFFFF
            if (bytes.size < length) return null

            val senderNodeId = buf.getLong()
            val hopLimit = buf.get()
            val nonce = ByteArray(12)
            buf.get(nonce)

            val ciphertextSize = length - HEADER_SIZE - TAG_SIZE
            if (ciphertextSize < 0 || buf.remaining() < ciphertextSize + TAG_SIZE) return null

            val ciphertext = ByteArray(ciphertextSize)
            buf.get(ciphertext)

            val authTag = ByteArray(TAG_SIZE)
            buf.get(authTag)

            return OuterPacket(
                senderNodeId = senderNodeId,
                hopLimit = hopLimit,
                nonce = nonce,
                ciphertext = ciphertext,
                authTag = authTag
            )
        }
    }

    fun encode(): ByteArray {
        val totalSize = HEADER_SIZE + ciphertext.size + authTag.size
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        buf.putShort(MAGIC)
        buf.putShort(totalSize.toShort())
        buf.putLong(senderNodeId)
        buf.put(hopLimit)
        buf.put(nonce)
        buf.put(ciphertext)
        buf.put(authTag)

        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OuterPacket
        return senderNodeId == other.senderNodeId &&
                nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext) &&
                authTag.contentEquals(other.authTag)
    }

    override fun hashCode(): Int {
        var result = senderNodeId.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + authTag.contentHashCode()
        return result
    }
}
