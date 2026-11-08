package com.example.offlinewalkietalkie.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarMathTest {

    @Test
    fun testHaversineDistanceKnownPoints() {
        // Paris (48.8566, 2.3522) to London (51.5074, -0.1278) ~ 343 km
        val distance = RadarMath.haversineDistance(48.8566, 2.3522, 51.5074, -0.1278)
        assertTrue("Distance should be around 343 km, got $distance", distance in 340000.0..346000.0)

        // Same point distance should be 0
        val zeroDistance = RadarMath.haversineDistance(10.0, 20.0, 10.0, 20.0)
        assertEquals(0.0, zeroDistance, 0.01)
    }

    @Test
    fun testInitialBearingNorthAndEast() {
        // Point directly North: (0.0, 0.0) to (1.0, 0.0) -> Bearing should be 0° (North)
        val bearingNorth = RadarMath.initialBearing(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0f, bearingNorth, 0.5f)

        // Point directly East: (0.0, 0.0) to (0.0, 1.0) -> Bearing should be 90° (East)
        val bearingEast = RadarMath.initialBearing(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0f, bearingEast, 0.5f)
    }

    @Test
    fun testRelativeBearing() {
        // Phone facing North (0°), target bearing East (90°) -> relative is +90° (right)
        val rel1 = RadarMath.relativeBearing(0f, 90f)
        assertEquals(90f, rel1, 0.01f)

        // Phone facing East (90°), target bearing North (0°) -> relative is -90° (left)
        val rel2 = RadarMath.relativeBearing(90f, 0f)
        assertEquals(-90f, rel2, 0.01f)

        // Phone facing 350°, target bearing 10° -> relative is +20° (right) across 0° boundary
        val rel3 = RadarMath.relativeBearing(350f, 10f)
        assertEquals(20f, rel3, 0.01f)
    }

    @Test
    fun testCardinalDirection() {
        assertEquals("N", RadarMath.cardinalDirection(0f))
        assertEquals("E", RadarMath.cardinalDirection(90f))
        assertEquals("S", RadarMath.cardinalDirection(180f))
        assertEquals("W", RadarMath.cardinalDirection(270f))
        assertEquals("NE", RadarMath.cardinalDirection(45f))
    }

    @Test
    fun testStabilizeAngleBoundaryWrapping() {
        // Heading moving from 358° to 2° (boundary crossing)
        val smoothed = RadarMath.stabilizeAngle(previous = 358f, current = 2f, alpha = 0.5f)
        // 358 + 0.5 * (+4) = 360 = 0°
        assertEquals(0f, smoothed, 0.5f)
    }
}
