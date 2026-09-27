package com.example.offlinewalkietalkie.diagnostics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkTestControllerTest {

    private lateinit var controller: LinkTestController
    private var lastPingTimestamp: Long = 0L

    @Before
    fun setUp() {
        controller = LinkTestController(
            sendPing = { _, ts ->
                lastPingTimestamp = ts
            }
        )
    }

    @Test
    fun testPingPongRttMeasurement() = runTest {
        var pingTimestamp = 0L
        val testController = LinkTestController(
            sendPing = { _, ts ->
                pingTimestamp = ts
            },
            scope = this
        )

        testController.performLinkTest("peer_1")
        runCurrent()
        assertTrue(pingTimestamp > 0)

        // Simulate 45 ms RTT response
        Thread.sleep(45)
        testController.onPongReceived(pingTimestamp)

        val telemetry = testController.telemetry.value
        assertTrue("Measured RTT should be >= 40 ms", telemetry.rttMs >= 40L)
        assertEquals(0.0, telemetry.packetLossPercent, 0.01)
        assertEquals(5, telemetry.qualityBars)
    }

    @Test
    fun testQualityBarsDegradation() {
        assertEquals(5, LinkTelemetry.calculateQualityBars(rttMs = 30L, lossPercent = 0.0, jitterMs = 10.0))
        assertEquals(4, LinkTelemetry.calculateQualityBars(rttMs = 100L, lossPercent = 0.0, jitterMs = 25.0))
        assertEquals(3, LinkTelemetry.calculateQualityBars(rttMs = 180L, lossPercent = 8.0, jitterMs = 45.0))
        assertEquals(2, LinkTelemetry.calculateQualityBars(rttMs = 280L, lossPercent = 20.0, jitterMs = 70.0))
        assertEquals(1, LinkTelemetry.calculateQualityBars(rttMs = 450L, lossPercent = 40.0, jitterMs = 100.0))
    }

    @Test
    fun testNoDistanceClaimedInTelemetry() {
        val telemetry = LinkTelemetry(
            rttMs = 50L,
            packetLossPercent = 0.0,
            jitterMs = 15.0,
            qualityBars = 5,
            transportName = "Nearby Connections"
        )

        val stringRep = telemetry.toString()
        // Section 0 Rule: Never claim fixed radio distance
        assertFalse(stringRep.contains("meters"))
        assertFalse(stringRep.contains("km"))
    }
}
