package com.example.offlinewalkietalkie.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Data payload for emergency SOS distress broadcast beacon.
 * Broadcast over BLE / P2P with multi-hop relay TTL decrement.
 */
data class SosBeacon(
    val senderId: String,
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val batteryPercent: Int = -1,
    val distressMessage: String = "EMERGENCY - NEED ASSISTANCE",
    val timestamp: Long = System.currentTimeMillis(),
    var ttl: Int = 3
) {
    fun toBytes(): ByteArray {
        val idBytes = senderId.toByteArray(StandardCharsets.UTF_8)
        val nameBytes = senderName.toByteArray(StandardCharsets.UTF_8)
        val msgBytes = distressMessage.toByteArray(StandardCharsets.UTF_8)

        val size = 8 + 8 + 8 + 1 + 8 + 1 + 2 + idBytes.size + 2 + nameBytes.size + 2 + msgBytes.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)

        buf.putDouble(latitude)
        buf.putDouble(longitude)
        buf.putDouble(altitude)
        buf.put(batteryPercent.toByte())
        buf.putLong(timestamp)
        buf.put(ttl.toByte())

        buf.putShort(idBytes.size.toShort())
        buf.put(idBytes)

        buf.putShort(nameBytes.size.toShort())
        buf.put(nameBytes)

        buf.putShort(msgBytes.size.toShort())
        buf.put(msgBytes)

        return buf.array()
    }

    companion object {
        fun decode(bytes: ByteArray): SosBeacon? {
            if (bytes.size < 34) return null
            return try {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val lat = buf.double
                val lon = buf.double
                val alt = buf.double
                val batt = buf.get().toInt() and 0xFF
                val time = buf.long
                val ttl = buf.get().toInt() and 0xFF

                val idLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < idLen) return null
                val idBytes = ByteArray(idLen)
                buf.get(idBytes)
                val senderId = String(idBytes, StandardCharsets.UTF_8)

                val nameLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < nameLen) return null
                val nameBytes = ByteArray(nameLen)
                buf.get(nameBytes)
                val senderName = String(nameBytes, StandardCharsets.UTF_8)

                val msgLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < msgLen) return null
                val msgBytes = ByteArray(msgLen)
                buf.get(msgBytes)
                val distressMsg = String(msgBytes, StandardCharsets.UTF_8)

                SosBeacon(
                    senderId = senderId,
                    senderName = senderName,
                    latitude = lat,
                    longitude = lon,
                    altitude = alt,
                    batteryPercent = batt,
                    distressMessage = distressMsg,
                    timestamp = time,
                    ttl = ttl
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Data payload for peer location and compass telemetry sharing.
 */


/**
 * Data payload acknowledging receipt of an emergency SOS distress beacon across mesh.
 */
data class SosAck(
    val sosSenderId: String,
    val sosTimestamp: Long,
    val responderId: String,
    val responderName: String,
    val ackTimestamp: Long = System.currentTimeMillis()
) {
    fun toBytes(): ByteArray {
        val sosIdBytes = sosSenderId.toByteArray(StandardCharsets.UTF_8)
        val respIdBytes = responderId.toByteArray(StandardCharsets.UTF_8)
        val respNameBytes = responderName.toByteArray(StandardCharsets.UTF_8)

        val size = 8 + 8 + 2 + sosIdBytes.size + 2 + respIdBytes.size + 2 + respNameBytes.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        buf.putLong(sosTimestamp)
        buf.putLong(ackTimestamp)
        buf.putShort(sosIdBytes.size.toShort())
        buf.put(sosIdBytes)
        buf.putShort(respIdBytes.size.toShort())
        buf.put(respIdBytes)
        buf.putShort(respNameBytes.size.toShort())
        buf.put(respNameBytes)
        return buf.array()
    }

    companion object {
        fun decode(bytes: ByteArray): SosAck? {
            if (bytes.size < 22) return null
            return try {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val sosTime = buf.long
                val ackTime = buf.long

                val sosIdLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < sosIdLen) return null
                val sosIdBytes = ByteArray(sosIdLen)
                buf.get(sosIdBytes)
                val sosSenderId = String(sosIdBytes, StandardCharsets.UTF_8)

                val respIdLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < respIdLen) return null
                val respIdBytes = ByteArray(respIdLen)
                buf.get(respIdBytes)
                val responderId = String(respIdBytes, StandardCharsets.UTF_8)

                val respNameLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < respNameLen) return null
                val respNameBytes = ByteArray(respNameLen)
                buf.get(respNameBytes)
                val responderName = String(respNameBytes, StandardCharsets.UTF_8)

                SosAck(sosSenderId, sosTime, responderId, responderName, ackTime)
            } catch (e: Exception) {
                null
            }
        }
    }
}
data class PeerLocation(
    val peerId: String,
    val peerName: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val batteryPercent: Int = -1,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toBytes(): ByteArray {
        val idBytes = peerId.toByteArray(StandardCharsets.UTF_8)
        val nameBytes = peerName.toByteArray(StandardCharsets.UTF_8)

        val size = 8 + 8 + 8 + 1 + 8 + 2 + idBytes.size + 2 + nameBytes.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)

        buf.putDouble(latitude)
        buf.putDouble(longitude)
        buf.putDouble(altitude)
        buf.put(batteryPercent.toByte())
        buf.putLong(timestamp)

        buf.putShort(idBytes.size.toShort())
        buf.put(idBytes)

        buf.putShort(nameBytes.size.toShort())
        buf.put(nameBytes)

        return buf.array()
    }

    companion object {
        fun decode(bytes: ByteArray): PeerLocation? {
            if (bytes.size < 33) return null
            return try {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val lat = buf.double
                val lon = buf.double
                val alt = buf.double
                val batt = buf.get().toInt() and 0xFF
                val time = buf.long

                val idLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < idLen) return null
                val idBytes = ByteArray(idLen)
                buf.get(idBytes)
                val peerId = String(idBytes, StandardCharsets.UTF_8)

                val nameLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < nameLen) return null
                val nameBytes = ByteArray(nameLen)
                buf.get(nameBytes)
                val peerName = String(nameBytes, StandardCharsets.UTF_8)

                PeerLocation(
                    peerId = peerId,
                    peerName = peerName,
                    latitude = lat,
                    longitude = lon,
                    altitude = alt,
                    batteryPercent = batt,
                    timestamp = time
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Data payload for battery & device telemetry exchange.
 */
data class PeerTelemetry(
    val peerId: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val rssi: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toBytes(): ByteArray {
        val idBytes = peerId.toByteArray(StandardCharsets.UTF_8)
        val size = 1 + 1 + 4 + 8 + 2 + idBytes.size
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)

        buf.put(batteryPercent.toByte())
        buf.put(if (isCharging) 1.toByte() else 0.toByte())
        buf.putInt(rssi)
        buf.putLong(timestamp)

        buf.putShort(idBytes.size.toShort())
        buf.put(idBytes)

        return buf.array()
    }

    companion object {
        fun decode(bytes: ByteArray): PeerTelemetry? {
            if (bytes.size < 16) return null
            return try {
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
                val batt = buf.get().toInt() and 0xFF
                val isCharging = buf.get().toInt() != 0
                val rssi = buf.getInt()
                val time = buf.long

                val idLen = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < idLen) return null
                val idBytes = ByteArray(idLen)
                buf.get(idBytes)
                val peerId = String(idBytes, StandardCharsets.UTF_8)

                PeerTelemetry(
                    peerId = peerId,
                    batteryPercent = batt,
                    isCharging = isCharging,
                    rssi = rssi,
                    timestamp = time
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
