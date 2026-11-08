package com.example.offlinewalkietalkie.radar

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure mathematical calculations for offline peer radar and compass orientation.
 * Operates completely offline with no network or map tile dependencies.
 */
object RadarMath {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates great-circle distance between two GPS points using the Haversine formula.
     * @return Distance in meters.
     */
    fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates the initial azimuth bearing from (lat1, lon1) pointing towards (lat2, lon2).
     * @return Bearing in degrees, normalized to 0.0f..360.0f (where 0° = North, 90° = East).
     */
    fun initialBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)

        val initialBearingRad = atan2(y, x)
        val initialBearingDeg = Math.toDegrees(initialBearingRad).toFloat()
        return (initialBearingDeg + 360f) % 360f
    }

    /**
     * Calculates the angle to the target relative to the phone's current heading.
     * @param deviceHeading Azimuth of the phone (0° = pointing North).
     * @param targetBearing Bearing to the peer (0° = North).
     * @return Offset angle in degrees (-180f..180f), where 0° = straight ahead, 90° = right, -90° = left.
     */
    fun relativeBearing(deviceHeading: Float, targetBearing: Float): Float {
        var diff = targetBearing - deviceHeading
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return diff
    }

    /**
     * Formats distance in meters into human-readable representation.
     */
    fun formatDistance(meters: Double): String {
        return when {
            meters.isNaN() || meters < 0 -> "-- m"
            meters < 1000 -> "${meters.toInt()} m"
            else -> String.format("%.1f km", meters / 1000.0)
        }
    }

    /**
     * Converts a bearing angle (0°..360°) to cardinal abbreviation.
     */
    fun cardinalDirection(bearing: Float): String {
        val normalized = (bearing % 360f + 360f) % 360f
        val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = ((normalized + 11.25f) / 22.5f).toInt() % 16
        return directions[index]
    }

    /**
     * Exponential moving average angle filter that smoothly interpolates angles across the 0°/360° boundary.
     * Prevents the compass needle from spinning wildly when heading oscillates between 359° and 1°.
     */
    fun stabilizeAngle(previous: Float, current: Float, alpha: Float = 0.25f): Float {
        var diff = current - previous
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        val smoothed = previous + alpha * diff
        return (smoothed % 360f + 360f) % 360f
    }
}
