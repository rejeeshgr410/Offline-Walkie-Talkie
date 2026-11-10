package com.example.offlinewalkietalkie.voice

import com.example.offlinewalkietalkie.protocol.VivoLeanProtocol
import org.junit.Assert.*
import org.junit.Test

class AdaptiveQosManagerTest {

    @Test
    fun testQosStateTransitionsOnRssi() {
        val qos = AdaptiveQosManager(initialCodecMode = VivoLeanProtocol.CODEC_MODE_2400)
        assertEquals(AdaptiveQosManager.QosState.GOOD, qos.qosState.value)

        var requestedMode = -1
        qos.onCodecChangeRequested = { mode ->
            requestedMode = mode
        }

        // Drop to -96 dBm (Below -95 dBm threshold -> BadQoSState)
        qos.onRssiUpdated(-96)
        assertEquals(AdaptiveQosManager.QosState.BAD, qos.qosState.value)
        assertEquals(VivoLeanProtocol.CODEC_MODE_1200, qos.currentCodecMode.value)
        assertEquals(VivoLeanProtocol.CODEC_MODE_1200, requestedMode)

        // Rise to -75 dBm (Above -80 dBm threshold -> GoodQoSState)
        qos.onRssiUpdated(-75)
        assertEquals(AdaptiveQosManager.QosState.GOOD, qos.qosState.value)
        assertEquals(VivoLeanProtocol.CODEC_MODE_2400, qos.currentCodecMode.value)
        assertEquals(VivoLeanProtocol.CODEC_MODE_2400, requestedMode)
    }

    @Test
    fun testRssiHysteresis() {
        val qos = AdaptiveQosManager(initialCodecMode = VivoLeanProtocol.CODEC_MODE_2400)
        qos.onRssiUpdated(-78) // Good
        assertEquals(AdaptiveQosManager.QosState.GOOD, qos.qosState.value)

        // In between -80 and -95 dBm (e.g. -85 dBm), state should remain GOOD due to hysteresis
        qos.onRssiUpdated(-85)
        assertEquals(AdaptiveQosManager.QosState.GOOD, qos.qosState.value)

        // Drop below -95 dBm -> BAD
        qos.onRssiUpdated(-98)
        assertEquals(AdaptiveQosManager.QosState.BAD, qos.qosState.value)

        // Climb to -88 dBm, state should remain BAD due to hysteresis until reaching >= -80 dBm
        qos.onRssiUpdated(-88)
        assertEquals(AdaptiveQosManager.QosState.BAD, qos.qosState.value)
    }

    @Test
    fun testRemoteCodecNegotiation() {
        val qos = AdaptiveQosManager()
        val accepted = qos.onRemoteCodecRequest(VivoLeanProtocol.CODEC_MODE_1600)
        assertTrue(accepted)
        assertEquals(VivoLeanProtocol.CODEC_MODE_1600, qos.currentCodecMode.value)

        // Invalid mode rejected
        val rejected = qos.onRemoteCodecRequest(99)
        assertFalse(rejected)
    }
}
