package com.example.offlinewalkietalkie.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceStartGateTest {
    @Test
    fun `background reconnect defers service start until activity is visible`() {
        val gate = ForegroundServiceStartGate()

        assertFalse(gate.onSessionChanged(active = true))
        assertTrue(gate.onActivityVisible())
        assertFalse(gate.onActivityVisible())
    }

    @Test
    fun `foreground session starts immediately and an OS denial retries once`() {
        val gate = ForegroundServiceStartGate()

        assertFalse(gate.onActivityVisible())
        assertTrue(gate.onSessionChanged(active = true))
        gate.onStartDenied()
        gate.onActivityHidden()
        assertTrue(gate.onActivityVisible())
        assertFalse(gate.onActivityVisible())
    }

    @Test
    fun `ending session clears a deferred start`() {
        val gate = ForegroundServiceStartGate()

        assertFalse(gate.onSessionChanged(active = true))
        assertFalse(gate.onSessionChanged(active = false))
        assertFalse(gate.onActivityVisible())
    }
}
