package com.example.offlinewalkietalkie.diagnostics

/**
 * Section 2, Section 8 & Section 19.14 Live Link Telemetry.
 *
 * Rules:
 * - Shows measured connection quality (RTT, packet loss, jitter, quality bars).
 * - NEVER claims or infers distance (e.g. no "2 km" or "15 meters" per Section 0).
 */
data class LinkTelemetry(
    val rttMs: Long = 0L,
    val packetLossPercent: Double = 0.0,
    val jitterMs: Double = 0.0,
    val qualityBars: Int = 5, // 1 to 5 signal bars
    val transportName: String = "Nearby Connections",
    val meshHopCount: Int = 1,
    val isReconnecting: Boolean = false
) {
    companion object {
        /**
         * Calculates 1-5 quality bars based on measured RTT, loss, and jitter.
         */
        fun calculateQualityBars(rttMs: Long, lossPercent: Double, jitterMs: Double): Int {
            if (lossPercent > 30.0 || rttMs > 400L) return 1
            if (lossPercent > 15.0 || rttMs > 250L || jitterMs > 60.0) return 2
            if (lossPercent > 5.0 || rttMs > 150L || jitterMs > 40.0) return 3
            if (rttMs > 80L || jitterMs > 20.0) return 4
            return 5
        }
    }
}
