package com.example.offlinewalkietalkie.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class FrameType(val value: Byte) {
    HELLO(0),
    HELLO_ACK(1),
    AUTH(2),
    AUTH_ACK(3),
    SESSION_INIT(4),
    SESSION_JOIN(5),
    SESSION_READY(6),
    FLOOR_REQUEST(7),
    FLOOR_GRANT(8),
    FLOOR_RELEASE(9),
    VOICE(10),
    PING(11),
    PONG(12),
    ROUTE_ADVERTISE(13),
    ROUTE_REQUEST(14),
    ROUTE_RESPONSE(15),
    LEAVE(16),
    ERROR(17),
    FLOOR_DENY(18),
    FLOOR_STATUS(19),
    KEY_UPDATE(20),
    TEXT(21),
    ACK(22),
    TREE_PLAN(23),
    TREE_ACK(24),
    GROUP_CONTROL(25);

    companion object {
        fun fromValue(value: Byte): FrameType? = values().find { it.value == value }
    }
}

data class Frame(
    val version: Byte = 1,
    val type: FrameType,
    val flags: Byte = 0,
    var hopLimit: Byte = 3,
    val sessionId: Long = 0,
    val sequence: Int = 0,
    val timestamp: Int = 0,
    val payload: ByteArray = ByteArray(0),
    val authTag: ByteArray? = null
) {
    companion object {
        const val MAGIC: Short = 0x5754 // "WT"
        const val HEADER_SIZE = 2 + 1 + 1 + 1 + 1 + 8 + 4 + 4 + 2
        const val FLAG_ENCRYPTED: Byte = 0x01
    }

    fun isEncrypted(): Boolean = (flags.toInt() and FLAG_ENCRYPTED.toInt()) != 0

    fun toBytes(): ByteArray {
        val totalSize = HEADER_SIZE + payload.size + (authTag?.size ?: 0)
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        
        buffer.putShort(MAGIC)
        buffer.put(version)
        buffer.put(type.value)
        buffer.put(flags)
        buffer.put(hopLimit)
        buffer.putLong(sessionId)
        buffer.putInt(sequence)
        buffer.putInt(timestamp)
        buffer.putShort(payload.size.toShort())
        buffer.put(payload)
        authTag?.let { buffer.put(it) }
        
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        if (version != other.version) return false
        if (type != other.type) return false
        if (flags != other.flags) return false
        if (hopLimit != other.hopLimit) return false
        if (sessionId != other.sessionId) return false
        if (sequence != other.sequence) return false
        if (timestamp != other.timestamp) return false
        if (!payload.contentEquals(other.payload)) return false
        if (authTag != null) {
            if (other.authTag == null) return false
            if (!authTag.contentEquals(other.authTag)) return false
        } else if (other.authTag != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + flags
        result = 31 * result + hopLimit
        result = 31 * result + sessionId.hashCode()
        result = 31 * result + sequence
        result = 31 * result + timestamp
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (authTag?.contentHashCode() ?: 0)
        return result
    }
}

object FrameCodec {
    fun decode(bytes: ByteArray): Frame? {
        if (bytes.size < Frame.HEADER_SIZE) return null
        
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buffer.short != Frame.MAGIC) return null
        
        val version = buffer.get()
        if (version != 1.toByte()) return null
        
        val typeByte = buffer.get()
        val type = FrameType.fromValue(typeByte) ?: return null
        
        val flags = buffer.get()
        val isEncrypted = (flags.toInt() and Frame.FLAG_ENCRYPTED.toInt()) != 0
        
        val hopLimit = buffer.get()
        val sessionId = buffer.getLong()
        val sequence = buffer.getInt()
        val timestamp = buffer.getInt()
        val payloadLen = buffer.short.toInt() and 0xFFFF
        
        if (buffer.remaining() < payloadLen) return null
        val payload = ByteArray(payloadLen)
        buffer.get(payload)
        
        val authTag = if (isEncrypted && buffer.remaining() >= 16) {
            val tag = ByteArray(16)
            buffer.get(tag)
            tag
        } else null
        
        return Frame(
            version = version,
            type = type,
            flags = flags,
            hopLimit = hopLimit,
            sessionId = sessionId,
            sequence = sequence,
            timestamp = timestamp,
            payload = payload,
            authTag = authTag
        )
    }
}
