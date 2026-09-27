package com.example.offlinewalkietalkie.diagnostics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RedactedDiagnosticsExporterTest {

    @Test
    fun testGenerateReportContainsExpectedNonSensitiveFields() {
        val report = RedactedDiagnosticsExporter.generateReport(
            activeTransportName = "BLE_L2CAP",
            totalPacketsSent = 1250L,
            totalPacketsReceived = 1240L,
            totalPacketsLost = 10L,
            averageJitterMs = 12.4,
            measuredRttMs = 45L,
            gmsAvailable = false,
            bleSupported = true,
            wifiAwareSupported = false,
            wifiDirectSupported = true,
            apiLevel = 34
        )

        assertNotNull(report)
        val json = JSONObject(report)

        assertEquals("TalkLoop", json.getString("app_name"))
        assertEquals("1.0", json.getString("app_version"))
        assertEquals(34, json.getInt("android_api_level"))
        assertEquals("BLE_L2CAP", json.getString("active_transport"))

        val caps = json.getJSONObject("capabilities")
        assertFalse(caps.getBoolean("gms_available"))
        assertTrue(caps.getBoolean("ble_supported"))

        val telemetry = json.getJSONObject("telemetry")
        assertEquals(1250L, telemetry.getLong("packets_sent"))
        assertEquals(1240L, telemetry.getLong("packets_received"))
        assertEquals(10L, telemetry.getLong("packets_lost"))
        assertEquals("< 50 ms (Excellent)", telemetry.getString("latency_bucket"))

        assertTrue("Generated report must pass privacy redaction check", RedactedDiagnosticsExporter.isSafeAndRedacted(report))
    }

    @Test
    fun testRedactionRejectsSensitiveDataInjections() {
        // Safe report
        val safeReport = """
            {
              "app_name": "TalkLoop",
              "telemetry": { "packets_sent": 100 }
            }
        """.trimIndent()
        assertTrue(RedactedDiagnosticsExporter.isSafeAndRedacted(safeReport))

        // Injected secret key
        val keyReport = """
            {
              "key": "abcdef12345"
            }
        """.trimIndent()
        assertFalse(RedactedDiagnosticsExporter.isSafeAndRedacted(keyReport))

        // Injected raw audio
        val audioReport = """
            {
              "audio": "base64encodedbytes"
            }
        """.trimIndent()
        assertFalse(RedactedDiagnosticsExporter.isSafeAndRedacted(audioReport))

        // Injected transcript
        val transcriptReport = """
            {
              "transcript": "hello world"
            }
        """.trimIndent()
        assertFalse(RedactedDiagnosticsExporter.isSafeAndRedacted(transcriptReport))

        // Injected 32-byte (64-hex char) raw key
        val hexReport = """
            {
              "debug_token": "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0"
            }
        """.trimIndent()
        assertFalse(RedactedDiagnosticsExporter.isSafeAndRedacted(hexReport))
    }
}
