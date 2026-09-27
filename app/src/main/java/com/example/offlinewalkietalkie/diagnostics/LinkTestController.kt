package com.example.offlinewalkietalkie.diagnostics

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Section 8 & Section 15 Test 9.1 Link Test Controller.
 *
 * Implements:
 * - Real-time RTT measurement via PING / PONG frames.
 * - Rolling packet loss and jitter calculation.
 * - 1 to 5 quality signal bars.
 * - Zero distance promises (§0).
 */
class LinkTestController(
    private val sendPing: suspend (peerId: String, timestampMs: Long) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _telemetry = MutableStateFlow(LinkTelemetry())
    val telemetry: StateFlow<LinkTelemetry> = _telemetry.asStateFlow()

    private val pendingPings = ConcurrentHashMap<Long, Long>() // timestamp -> sentEpochMs

    private var totalPingsSent = 0
    private var totalPongsReceived = 0
    private var rollingRttMs = 0L
    private var currentJitterMs = 0.0

    /**
     * Start a link test to measure RTT.
     */
    fun performLinkTest(peerId: String) {
        val now = System.currentTimeMillis()
        pendingPings[now] = now
        totalPingsSent++

        scope.launch {
            try {
                sendPing(peerId, now)
            } catch (e: Exception) {}
        }
    }

    /**
     * Handle incoming PONG response with original ping timestamp.
     */
    fun onPongReceived(originalTimestampMs: Long) {
        val sentEpoch = pendingPings.remove(originalTimestampMs) ?: return
        val now = System.currentTimeMillis()
        val rtt = (now - sentEpoch).coerceAtLeast(1L)

        totalPongsReceived++

        // Update rolling RTT
        rollingRttMs = if (rollingRttMs == 0L) rtt else (rollingRttMs * 3 + rtt) / 4

        updateTelemetry()
    }

    fun updateJitter(jitterMs: Double) {
        currentJitterMs = jitterMs
        updateTelemetry()
    }

    fun updateTransportInfo(transportName: String, meshHops: Int) {
        _telemetry.value = _telemetry.value.copy(
            transportName = transportName,
            meshHopCount = meshHops
        )
    }

    private fun updateTelemetry() {
        val lossPercent = if (totalPingsSent > 0) {
            val lost = (totalPingsSent - totalPongsReceived).coerceAtLeast(0)
            (lost.toDouble() / totalPingsSent.toDouble()) * 100.0
        } else {
            0.0
        }

        val bars = LinkTelemetry.calculateQualityBars(
            rttMs = rollingRttMs,
            lossPercent = lossPercent,
            jitterMs = currentJitterMs
        )

        _telemetry.value = _telemetry.value.copy(
            rttMs = rollingRttMs,
            packetLossPercent = lossPercent,
            jitterMs = currentJitterMs,
            qualityBars = bars
        )
    }

    fun reset() {
        pendingPings.clear()
        totalPingsSent = 0
        totalPongsReceived = 0
        rollingRttMs = 0L
        currentJitterMs = 0.0
        _telemetry.value = LinkTelemetry()
    }
}
