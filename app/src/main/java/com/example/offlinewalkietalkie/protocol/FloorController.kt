package com.example.offlinewalkietalkie.protocol

import com.example.offlinewalkietalkie.voice.BufferedAudioFrame
import com.example.offlinewalkietalkie.voice.PendingFloorBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionFloorMode {
    DIRECT_1_TO_1,
    CHANNEL_HOST_CONTROLLED
}

/**
 * Section 6 & Section 19.3/19.4 PTT Floor Controller.
 *
 * Handles:
 * - Direct 1:1 optimistic floor control with deterministic collision resolution by lower memberId.
 * - Host-controlled channel floor with 500 ms PendingFloorBuffer (zero clipped syllables).
 * - 60-second safety timeout.
 * - Local-only tone feedback cues.
 */
class FloorController(
    val localMemberId: Long,
    var mode: SessionFloorMode = SessionFloorMode.DIRECT_1_TO_1,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        const val COLLISION_WINDOW_MS = 300L
        const val SAFETY_TIMEOUT_MS = 60_000L // 60s safety timeout (§6 & §19)
    }

    private val _floorState = MutableStateFlow<FloorState>(FloorState.Idle)
    val floorState: StateFlow<FloorState> = _floorState.asStateFlow()

    val pendingBuffer = PendingFloorBuffer()

    private var safetyTimerJob: Job? = null
    private var lastPressTimestampMs: Long = 0L

    var onTransmitPendingFrames: ((List<BufferedAudioFrame>) -> Unit)? = null
    var onPlayBusyTone: (() -> Unit)? = null
    var onPlayPermissionChirp: (() -> Unit)? = null
    var onFloorReleased: (() -> Unit)? = null

    /**
     * User pressed the PTT control.
     */
    fun requestPtt() {
        val current = _floorState.value
        if (current !is FloorState.Idle) return

        lastPressTimestampMs = System.currentTimeMillis()

        if (mode == SessionFloorMode.DIRECT_1_TO_1) {
            // Direct 1:1 is optimistic: capture begins immediately
            _floorState.value = FloorState.Talking
            onPlayPermissionChirp?.invoke()
            startSafetyTimeout()
        } else {
            // Channel: request floor from host and buffer up to 500ms locally
            _floorState.value = FloorState.Requesting
            pendingBuffer.clear()
        }
    }

    /**
     * Record captured audio frame while requesting or talking.
     */
    fun onAudioCaptured(sequence: Int, timestampMs: Long, audioData: ByteArray): Boolean {
        when (_floorState.value) {
            is FloorState.Requesting -> {
                pendingBuffer.push(sequence, timestampMs, audioData)
                return false // Not sent live yet
            }
            is FloorState.Talking, is FloorState.Granted, is FloorState.HeldByMe -> {
                return true // Sent live
            }
            else -> return false
        }
    }

    /**
     * Direct 1:1 Collision Resolution (§19.3):
     * If peer frame / request received within collision window:
     * Lower memberId wins. Loser yields floor and plays local busy tone.
     *
     * @return true if local won, false if local lost
     */
    fun handleDirectCollision(peerMemberId: Long, peerPressTimestampMs: Long): Boolean {
        if (_floorState.value !is FloorState.Talking && _floorState.value !is FloorState.HeldByMe) {
            return false
        }

        val elapsedSinceLocalPress = System.currentTimeMillis() - lastPressTimestampMs
        if (elapsedSinceLocalPress <= COLLISION_WINDOW_MS) {
            val localWins = java.lang.Long.compareUnsigned(localMemberId, peerMemberId) < 0
            if (!localWins) {
                // Yield floor
                cancelSafetyTimeout()
                pendingBuffer.clear()
                _floorState.value = FloorState.Denied("Direct collision resolved to lower memberId")
                onPlayBusyTone?.invoke()
                _floorState.value = FloorState.Idle
                onFloorReleased?.invoke()
                return false
            }
            return true
        }
        return true
    }

    /**
     * Host granted floor in channel mode.
     */
    fun onFloorGranted() {
        if (_floorState.value is FloorState.Requesting) {
            _floorState.value = FloorState.Granted
            onPlayPermissionChirp?.invoke()

            // Drain buffered audio and transmit before live audio
            val bufferedFrames = pendingBuffer.drain()
            if (bufferedFrames.isNotEmpty()) {
                onTransmitPendingFrames?.invoke(bufferedFrames)
            }

            _floorState.value = FloorState.Talking
            startSafetyTimeout()
        }
    }

    /**
     * Host denied floor in channel mode.
     */
    fun onFloorDenied(reason: String = "Floor busy") {
        if (_floorState.value is FloorState.Requesting) {
            cancelSafetyTimeout()
            pendingBuffer.clear()
            _floorState.value = FloorState.Denied(reason)
            onPlayBusyTone?.invoke()
            _floorState.value = FloorState.Idle
        }
    }

    /**
     * User released the PTT control.
     */
    fun releasePtt() {
        val current = _floorState.value
        if (current is FloorState.Talking || current is FloorState.Granted || current is FloorState.HeldByMe || current is FloorState.Requesting) {
            cancelSafetyTimeout()
            pendingBuffer.clear()
            _floorState.value = FloorState.Releasing
            _floorState.value = FloorState.Idle
            onFloorReleased?.invoke()
        }
    }

    /**
     * Another member is holding the floor.
     */
    fun onFloorHeldByOther(peerId: String, peerName: String) {
        if (_floorState.value !is FloorState.Talking && _floorState.value !is FloorState.HeldByMe) {
            cancelSafetyTimeout()
            pendingBuffer.clear()
            _floorState.value = FloorState.HeldByOther(peerId, peerName)
        }
    }

    /**
     * Floor released by other member.
     */
    fun onFloorFreed() {
        val current = _floorState.value
        if (current is FloorState.HeldByOther) {
            _floorState.value = FloorState.Idle
        }
    }

    private fun startSafetyTimeout() {
        cancelSafetyTimeout()
        safetyTimerJob = scope.launch {
            delay(SAFETY_TIMEOUT_MS)
            // 60s safety timeout reached: auto-release
            releasePtt()
        }
    }

    private fun cancelSafetyTimeout() {
        safetyTimerJob?.cancel()
        safetyTimerJob = null
    }

    fun reset() {
        cancelSafetyTimeout()
        pendingBuffer.clear()
        _floorState.value = FloorState.Idle
    }
}
