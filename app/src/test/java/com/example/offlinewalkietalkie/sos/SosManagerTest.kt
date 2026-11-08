package com.example.offlinewalkietalkie.sos

import com.example.offlinewalkietalkie.protocol.SosBeacon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SosManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testSosBroadcastAndRelay() = testScope.runTest {
        val relayedBeacons = mutableListOf<SosBeacon>()

        val sosManager = SosManager(
            localSenderId = "my-device-id",
            localSenderName = "My Pixel",
            broadcastFrame = { beacon, _ ->
                relayedBeacons.add(beacon)
            },
            scope = testScope
        )

        // Receive SOS from peer with TTL = 3
        val incoming = SosBeacon(
            senderId = "peer-injured-1",
            senderName = "Alice",
            latitude = 37.123,
            longitude = -122.456,
            altitude = 200.0,
            batteryPercent = 25,
            distressMessage = "LOST ON TRAIL",
            timestamp = 1000L,
            ttl = 3
        )

        sosManager.handleIncomingSos(incoming, ingressPeerId = "peer-injured-1")

        // Should have 1 alert
        assertEquals(1, sosManager.incomingAlerts.value.size)
        assertEquals("Alice", sosManager.incomingAlerts.value.first().senderName)

        // Should have relayed with TTL = 2
        assertEquals(1, relayedBeacons.size)
        assertEquals(2, relayedBeacons.first().ttl)

        // Dismiss alert
        sosManager.dismissAlert("peer-injured-1")
        assertTrue(sosManager.incomingAlerts.value.isEmpty())
    }

    @Test
    fun testSelfSosIgnored() = testScope.runTest {
        val relayedBeacons = mutableListOf<SosBeacon>()
        val sosManager = SosManager(
            localSenderId = "my-device-id",
            localSenderName = "My Pixel",
            broadcastFrame = { beacon, _ -> relayedBeacons.add(beacon) },
            scope = testScope
        )

        val selfBeacon = SosBeacon(
            senderId = "my-device-id",
            senderName = "My Pixel",
            latitude = 37.0,
            longitude = -122.0,
            distressMessage = "TEST",
            timestamp = 1000L,
            ttl = 3
        )

        sosManager.handleIncomingSos(selfBeacon, ingressPeerId = null)
        assertTrue(sosManager.incomingAlerts.value.isEmpty())
        assertTrue(relayedBeacons.isEmpty())
    }
}
