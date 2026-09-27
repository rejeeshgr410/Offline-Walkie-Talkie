package com.example.offlinewalkietalkie.session

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class ReconnectState {
    object Idle : ReconnectState()
    object Connected : ReconnectState()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int, val nextRetryMs: Long) : ReconnectState()
    data class Failed(val reason: String) : ReconnectState()
}

/**
 * Section 3 & Section 13 Reconnect Manager with Exponential Backoff.
 *
 * Rules:
 * - On transient loss, moves to Reconnecting and retries known peer/path with exponential backoff.
 * - An untrusted identity NEVER auto-reconnects (§3, §19.4).
 * - Host disconnect: freezes new floor grants for 10s while reconnecting; ends session cleanly if unreachable (§13).
 */
class ReconnectManager(
    val peerId: String,
    val isTrusted: Boolean,
    val isHost: Boolean,
    private val doReconnect: suspend (peerId: String) -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        val BACKOFF_DELAYS_MS = listOf(1000L, 2000L, 4000L, 8000L, 16000L)
        const val MAX_ATTEMPTS = 5
        const val HOST_DISCONNECT_GRACE_PERIOD_MS = 10_000L // 10s grace period (§13)
    }

    private val _state = MutableStateFlow<ReconnectState>(ReconnectState.Connected)
    val state: StateFlow<ReconnectState> = _state.asStateFlow()

    private var reconnectJob: Job? = null
    var onSessionTerminated: ((reason: String) -> Unit)? = null
    var onFloorGrantsFrozen: ((frozen: Boolean) -> Unit)? = null

    /**
     * Called when connection is lost.
     */
    fun onConnectionLost() {
        // Untrusted peer never auto-reconnects (§3 & §19.4)
        if (!isTrusted) {
            _state.value = ReconnectState.Failed("Untrusted identity cannot auto-reconnect")
            onSessionTerminated?.invoke("Connection lost with untrusted identity")
            return
        }

        if (isHost) {
            // Host disconnected: freeze floor grants for 10 seconds (§13)
            onFloorGrantsFrozen?.invoke(true)
        }

        startReconnectLoop()
    }

    private fun startReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (attempt in 1..MAX_ATTEMPTS) {
                val delayMs = BACKOFF_DELAYS_MS.getOrElse(attempt - 1) { 16000L }
                _state.value = ReconnectState.Reconnecting(
                    attempt = attempt,
                    maxAttempts = MAX_ATTEMPTS,
                    nextRetryMs = delayMs
                )

                delay(delayMs)

                val success = try {
                    doReconnect(peerId)
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    _state.value = ReconnectState.Connected
                    if (isHost) {
                        onFloorGrantsFrozen?.invoke(false)
                    }
                    return@launch
                }
            }

            // All attempts exhausted
            val failureReason = if (isHost) "Host unreachable after 10s grace period" else "Peer unreachable after 5 attempts"
            _state.value = ReconnectState.Failed(failureReason)
            onSessionTerminated?.invoke(failureReason)
        }
    }

    fun onConnected() {
        reconnectJob?.cancel()
        _state.value = ReconnectState.Connected
        if (isHost) {
            onFloorGrantsFrozen?.invoke(false)
        }
    }

    fun reset() {
        reconnectJob?.cancel()
        _state.value = ReconnectState.Idle
    }
}
