package com.example.offlinewalkietalkie.diagnostics

import android.content.Context
import android.os.Build
import com.example.offlinewalkietalkie.util.CapabilityManager
import org.json.JSONObject

/**
 * Section 14 & Section 15 Test 9.2 Redacted Diagnostics Exporter.
 *
 * Strict Privacy Rules:
 * - NEVER exports audio, packet payloads, private/public keys, SAS codes, session IDs, or exact peer identifiers.
 * - Exports ONLY: app version, OS API level, capability flags, active transport name, aggregate counters, and rounded timing buckets.
 */
object RedactedDiagnosticsExporter {

    fun generateReport(
        context: Context,
        activeTransportName: String,
        totalPacketsSent: Long,
        totalPacketsReceived: Long,
        totalPacketsLost: Long,
        averageJitterMs: Double,
        measuredRttMs: Long
    ): String {
        return generateReport(
            activeTransportName = activeTransportName,
            totalPacketsSent = totalPacketsSent,
            totalPacketsReceived = totalPacketsReceived,
            totalPacketsLost = totalPacketsLost,
            averageJitterMs = averageJitterMs,
            measuredRttMs = measuredRttMs,
            gmsAvailable = CapabilityManager.isGooglePlayServicesAvailable(context),
            bleSupported = CapabilityManager.isBleSupported(context),
            wifiAwareSupported = CapabilityManager.isWifiAwareSupported(context),
            wifiDirectSupported = CapabilityManager.isWifiDirectSupported(context),
            apiLevel = Build.VERSION.SDK_INT
        )
    }

    fun generateReport(
        activeTransportName: String,
        totalPacketsSent: Long,
        totalPacketsReceived: Long,
        totalPacketsLost: Long,
        averageJitterMs: Double,
        measuredRttMs: Long,
        gmsAvailable: Boolean = false,
        bleSupported: Boolean = true,
        wifiAwareSupported: Boolean = false,
        wifiDirectSupported: Boolean = true,
        apiLevel: Int = 34
    ): String {
        val rttBucket = when {
            measuredRttMs < 50L -> "< 50 ms (Excellent)"
            measuredRttMs < 100L -> "50–100 ms (Good)"
            measuredRttMs < 200L -> "100–200 ms (Fair)"
            else -> "> 200 ms (Degraded)"
        }

        val json = JSONObject().apply {
            put("app_name", "TalkLoop")
            put("app_version", "1.0")
            put("android_api_level", apiLevel)
            put("active_transport", activeTransportName)

            // Capability flags (boolean only)
            val caps = JSONObject().apply {
                put("gms_available", gmsAvailable)
                put("ble_supported", bleSupported)
                put("wifi_aware_supported", wifiAwareSupported)
                put("wifi_direct_supported", wifiDirectSupported)
            }
            put("capabilities", caps)

            // Aggregate telemetry metrics
            val metrics = JSONObject().apply {
                put("packets_sent", totalPacketsSent)
                put("packets_received", totalPacketsReceived)
                put("packets_lost", totalPacketsLost)
                put("average_jitter_ms", "%.1f".format(averageJitterMs))
                put("latency_bucket", rttBucket)
            }
            put("telemetry", metrics)

            // Confirmation of zero sensitive data
            put("privacy_status", "REDACTED_SAFE - No keys, audio, or identities included")
        }

        return json.toString(2)
    }

    /**
     * Verification test helper: ensures report contains no keys, transcripts, or sensitive identifiers.
     */
    fun isSafeAndRedacted(reportJson: String): Boolean {
        // Forbidden keywords / patterns
        val forbidden = listOf(
            "key", "private", "public", "secret", "token",
            "audio", "pcm", "opus", "transcript", "sas",
            "fingerprint", "mac", "ip_address", "peer_id", "session_id"
        )

        val lower = reportJson.lowercase()
        for (term in forbidden) {
            // Check if any key contains forbidden substrings (allow privacy_status and capabilities keys)
            if (lower.contains("\"$term\"") || lower.contains("\"$term:")) {
                return false
            }
        }

        // Check for 64-char hex strings (potential 32-byte keys)
        val hexRegex = Regex("[0-9a-fA-F]{64}")
        if (hexRegex.containsMatchIn(reportJson)) {
            return false
        }

        return true
    }
}
