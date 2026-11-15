package com.example.offlinewalkietalkie.transport.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConnectionPolicyTest {

    @Test
    fun `exactly one endpoint initiates when both Bluetooth addresses are available`() {
        val aInitiates = BleConnectionPolicy.shouldInitiateClient(
            localAddress = "10:00:00:00:00:01",
            remoteAddress = "20:00:00:00:00:02",
            localName = "Pixel",
            remoteName = "Samsung"
        )
        val bInitiates = BleConnectionPolicy.shouldInitiateClient(
            localAddress = "20:00:00:00:00:02",
            remoteAddress = "10:00:00:00:00:01",
            localName = "Samsung",
            remoteName = "Pixel"
        )

        assertTrue(aInitiates)
        assertFalse(bInitiates)
    }

    @Test
    fun `redacted Android address falls back to one name based initiator`() {
        val pixelInitiates = BleConnectionPolicy.shouldInitiateClient(
            localAddress = "02:00:00:00:00:00",
            remoteAddress = "20:00:00:00:00:02",
            localName = "Pixel",
            remoteName = "Samsung"
        )
        val samsungInitiates = BleConnectionPolicy.shouldInitiateClient(
            localAddress = "02:00:00:00:00:00",
            remoteAddress = "10:00:00:00:00:01",
            localName = "Samsung",
            remoteName = "Pixel"
        )

        assertTrue(pixelInitiates)
        assertFalse(samsungInitiates)
    }
}
