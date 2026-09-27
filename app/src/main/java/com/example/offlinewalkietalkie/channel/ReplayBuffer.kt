package com.example.offlinewalkietalkie.channel

import com.example.offlinewalkietalkie.voice.AudioPlayback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/**
 * Section 14 In-Memory Audio Replay Buffer.
 *
 * Rules:
 * - Holds up to 3 most recent received voice transmissions.
 * - Defaults off, opt-in per session.
 * - In-memory ONLY: cleared at session end, process recreation, or identity warning.
 * - Zero disk persistence.
 */
class ReplayBuffer(
    private val playback: AudioPlayback? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        const val MAX_TRANSMISSIONS = 3
    }

    var isEnabled: Boolean = false
        set(value) {
            field = value
            if (!value) {
                clear()
            }
        }

    private val transmissions = Collections.synchronizedList(mutableListOf<ReplayTransmission>())
    
    private val _transmissionList = MutableStateFlow<List<ReplayTransmission>>(emptyList())
    val transmissionList: StateFlow<List<ReplayTransmission>> = _transmissionList.asStateFlow()

    private var currentRecordingFrames = mutableListOf<ByteArray>()
    private var currentSpeakerId: Long = 0L
    private var currentSpeakerName: String = ""

    private var replayJob: Job? = null

    /**
     * Called when a peer begins talking (floor granted).
     */
    fun onTransmissionStarted(speakerId: Long, speakerName: String) {
        if (!isEnabled) return
        currentRecordingFrames.clear()
        currentSpeakerId = speakerId
        currentSpeakerName = speakerName
    }

    /**
     * Accumulate PCM frame for the ongoing transmission.
     */
    fun onFrameReceived(pcmBytes: ByteArray) {
        if (!isEnabled) return
        currentRecordingFrames.add(pcmBytes.clone())
    }

    /**
     * Called when the peer stops talking (floor released).
     */
    fun onTransmissionCompleted() {
        if (!isEnabled || currentRecordingFrames.isEmpty()) return

        val transmission = ReplayTransmission(
            senderMemberId = currentSpeakerId,
            senderName = currentSpeakerName,
            frames = currentRecordingFrames.toList()
        )
        currentRecordingFrames.clear()

        synchronized(transmissions) {
            transmissions.add(transmission)
            while (transmissions.size > MAX_TRANSMISSIONS) {
                transmissions.removeAt(0) // Keep only last 3 transmissions (§14)
            }
            _transmissionList.value = transmissions.toList()
        }
    }

    /**
     * Replay the specified transmission through AudioPlayback.
     */
    fun playTransmission(transmissionId: String, onFinished: () -> Unit = {}) {
        val target = synchronized(transmissions) {
            transmissions.firstOrNull { it.id == transmissionId }
        } ?: return

        replayJob?.cancel()
        replayJob = scope.launch {
            playback?.start()
            for (frame in target.frames) {
                playback?.playFrame(frame)
                delay(20) // 20ms frame cadence
            }
            onFinished()
        }
    }

    /**
     * Replay the most recent transmission.
     */
    fun playLatest(onFinished: () -> Unit = {}) {
        val latest = synchronized(transmissions) { transmissions.lastOrNull() }
        if (latest != null) {
            playTransmission(latest.id, onFinished)
        }
    }

    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
    }

    /**
     * Strictly clear in-memory buffer.
     */
    fun clear() {
        stopReplay()
        currentRecordingFrames.clear()
        synchronized(transmissions) {
            transmissions.clear()
            _transmissionList.value = emptyList()
        }
    }
}
