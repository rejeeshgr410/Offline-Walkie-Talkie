package com.example.offlinewalkietalkie.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProtocolPayloadsTest {

    @Test
    fun testSosBeaconSerialization() {
        val original = SosBeacon(
            senderId = "node-alpha-123",
            senderName = "Reji's Pixel",
            latitude = 37.774929,
            longitude = -122.419416,
            altitude = 125.5,
            batteryPercent = 74,
            distressMessage = "INJURED - SPRAINED ANKLE",
            timestamp = 1690000000000L,
            ttl = 3
        )

        val bytes = original.toBytes()
        val decoded = SosBeacon.decode(bytes)

        assertNotNull(decoded)
        assertEquals(original.senderId, decoded!!.senderId)
        assertEquals(original.senderName, decoded.senderName)
        assertEquals(original.latitude, decoded.latitude, 0.000001)
        assertEquals(original.longitude, decoded.longitude, 0.000001)
        assertEquals(original.altitude, decoded.altitude, 0.000001)
        assertEquals(original.batteryPercent, decoded.batteryPercent)
        assertEquals(original.distressMessage, decoded.distressMessage)
        assertEquals(original.timestamp, decoded.timestamp)
        assertEquals(original.ttl, decoded.ttl)
    }

    @Test
    fun testPeerLocationSerialization() {
        val original = PeerLocation(
            peerId = "peer-bravo-456",
            peerName = "Camp Alpha",
            latitude = 45.123456,
            longitude = 9.876543,
            altitude = 550.0,
            batteryPercent = 92,
            timestamp = 1690000005000L
        )

        val bytes = original.toBytes()
        val decoded = PeerLocation.decode(bytes)

        assertNotNull(decoded)
        assertEquals(original.peerId, decoded!!.peerId)
        assertEquals(original.peerName, decoded.peerName)
        assertEquals(original.latitude, decoded.latitude, 0.000001)
        assertEquals(original.longitude, decoded.longitude, 0.000001)
        assertEquals(original.altitude, decoded.altitude, 0.000001)
        assertEquals(original.batteryPercent, decoded.batteryPercent)
        assertEquals(original.timestamp, decoded.timestamp)
    }

    @Test
    fun testPeerTelemetrySerialization() {
        val original = PeerTelemetry(
            peerId = "peer-charlie-789",
            batteryPercent = 48,
            isCharging = true,
            rssi = -65,
            timestamp = 1690000010000L
        )

        val bytes = original.toBytes()
        val decoded = PeerTelemetry.decode(bytes)

        assertNotNull(decoded)
        assertEquals(original.peerId, decoded!!.peerId)
        assertEquals(original.batteryPercent, decoded.batteryPercent)
        assertEquals(original.isCharging, decoded.isCharging)
        assertEquals(original.rssi, decoded.rssi)
        assertEquals(original.timestamp, decoded.timestamp)
    }
}
