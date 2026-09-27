package com.example.offlinewalkietalkie.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectManagerTest {

    @Test
    fun testUntrustedPeerNeverAutoReconnects() = runTest {
        var reconnectCalled = false
        var sessionTerminatedReason: String? = null

        val manager = ReconnectManager(
            peerId = "untrusted_peer_123",
            isTrusted = false,
            isHost = false,
            doReconnect = {
                reconnectCalled = true
                true
            },
            scope = this
        )

        manager.onSessionTerminated = { reason ->
            sessionTerminatedReason = reason
        }

        manager.onConnectionLost()
        runCurrent()

        // Untrusted peer must immediately fail without any reconnect attempts
        assertFalse("Reconnect should NEVER be called for untrusted peer", reconnectCalled)
        assertTrue(manager.state.value is ReconnectState.Failed)
        assertEquals("Untrusted identity cannot auto-reconnect", (manager.state.value as ReconnectState.Failed).reason)
        assertNotNull(sessionTerminatedReason)
    }

    @Test
    fun testTrustedPeerExponentialBackoffReconnectSuccess() = runTest {
        var attempts = 0

        val manager = ReconnectManager(
            peerId = "trusted_peer_456",
            isTrusted = true,
            isHost = false,
            doReconnect = {
                attempts++
                // Succeed on attempt 2
                attempts == 2
            },
            scope = this
        )

        manager.onConnectionLost()
        runCurrent()

        // Attempt 1 scheduled after 1000ms
        assertTrue(manager.state.value is ReconnectState.Reconnecting)
        assertEquals(1, (manager.state.value as ReconnectState.Reconnecting).attempt)

        advanceTimeBy(1001)
        runCurrent()

        // Attempt 1 failed, Attempt 2 scheduled after 2000ms
        assertEquals(1, attempts)
        assertTrue(manager.state.value is ReconnectState.Reconnecting)
        assertEquals(2, (manager.state.value as ReconnectState.Reconnecting).attempt)

        advanceTimeBy(2001)
        runCurrent()

        // Attempt 2 succeeds
        assertEquals(2, attempts)
        assertEquals(ReconnectState.Connected, manager.state.value)
    }

    @Test
    fun testHostDisconnectFreezesFloorGrantsAndTimesOut() = runTest {
        var floorFrozen: Boolean? = null
        var sessionTerminated = false

        val manager = ReconnectManager(
            peerId = "host_node_789",
            isTrusted = true,
            isHost = true,
            doReconnect = { false }, // Never succeeds
            scope = this
        )

        manager.onFloorGrantsFrozen = { frozen ->
            floorFrozen = frozen
        }
        manager.onSessionTerminated = {
            sessionTerminated = true
        }

        manager.onConnectionLost()
        runCurrent()

        // §13: Host disconnect must freeze floor grants
        assertEquals(true, floorFrozen)

        // Advance through all 5 backoff delays (1s + 2s + 4s + 8s + 16s = 31s)
        advanceTimeBy(32000)
        runCurrent()

        assertTrue(manager.state.value is ReconnectState.Failed)
        assertTrue(sessionTerminated)
    }
}
