package com.example.offlinewalkietalkie.voice

import android.util.Log
import com.example.offlinewalkietalkie.protocol.VivoLeanProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-Time Adaptive Bitrate QoS State Machine.
 *
 * Reverse-engineered directly from Vivo's OriginOS walkie-talkie engine (nj.b.e & nj.e):
 * - Evaluates real-time peer signal strength (RSSI in dBm).
 * - Level 2 (GoodQoSState): RSSI >= -80 dBm -> High-fidelity mode (AMR-NB or Codec2 3200/2400).
 * - Level 1 (ModerateQoSState): -80 dBm > RSSI > -95 dBm -> Standard robust mode (Codec2 1600 / AMR-NB 5.9k).
 * - Level 0 (BadQoSState): RSSI <= -95 dBm -> Extreme-range survival mode (Codec2 1400 / 1200 / 700).
 * - Dynamic codec negotiation via TYPE_CODEC_REQ (0x0A) and TYPE_CODEC_ACK (0x0B).
 */
class AdaptiveQosManager(
    initialCodecMode: Int = VivoLeanProtocol.CODEC_MODE_2400
) {
    companion object {
        private const val TAG = "TL/AdaptiveQoS"

        // Thresholds matching nj.b.e lines 20-25
        const val RSSI_GOOD_THRESHOLD = -80 // dBm
        const val RSSI_BAD_THRESHOLD = -95 // dBm

        const val QOS_LEVEL_BAD = 0
        const val QOS_LEVEL_MODERATE = 1
        const val QOS_LEVEL_GOOD = 2
    }

    enum class QosState(val level: Int) {
        BAD(QOS_LEVEL_BAD),
        MODERATE(QOS_LEVEL_MODERATE),
        GOOD(QOS_LEVEL_GOOD)
    }

    private val _qosState = MutableStateFlow(QosState.GOOD)
    val qosState: StateFlow<QosState> = _qosState.asStateFlow()

    private val _currentCodecMode = MutableStateFlow(initialCodecMode)
    val currentCodecMode: StateFlow<Int> = _currentCodecMode.asStateFlow()

    private val _lastRssi = MutableStateFlow(-70)
    val lastRssi: StateFlow<Int> = _lastRssi.asStateFlow()

    var onCodecChangeRequested: ((targetMode: Int) -> Unit)? = null

    /**
     * Feed updated RSSI measurement into state machine.
     * Matches nj.b.e logic:
     * `eVar.f18479s = iIntValue >= -80 ? 2 : iIntValue <= -95 ? 0 : eVar.f18479s;`
     */
    fun onRssiUpdated(rssi: Int) {
        _lastRssi.value = rssi

        val previousState = _qosState.value
        val newState = when {
            rssi >= RSSI_GOOD_THRESHOLD -> QosState.GOOD
            rssi <= RSSI_BAD_THRESHOLD -> QosState.BAD
            else -> previousState // Hysteresis: retain current state between -80 and -95 dBm
        }

        if (newState != previousState) {
            _qosState.value = newState
            Log.i(TAG, "QoS State Transition: $previousState -> $newState (RSSI: $rssi dBm)")
            handleStateTransition(newState)
        }
    }

    private fun handleStateTransition(state: QosState) {
        val targetCodecMode = when (state) {
            QosState.GOOD -> VivoLeanProtocol.CODEC_MODE_2400 // Balanced high quality
            QosState.MODERATE -> VivoLeanProtocol.CODEC_MODE_1600 // Standard robust
            QosState.BAD -> VivoLeanProtocol.CODEC_MODE_1200 // Survival low bitrate for extreme range
        }

        if (targetCodecMode != _currentCodecMode.value) {
            Log.i(TAG, "Triggering Adaptive Codec Renegotiation: 0x${Integer.toHexString(_currentCodecMode.value)} -> 0x${Integer.toHexString(targetCodecMode)}")
            _currentCodecMode.value = targetCodecMode
            onCodecChangeRequested?.invoke(targetCodecMode)
        }
    }

    /**
     * Process incoming CODEC_REQ packet from peer.
     * Returns true if accepted.
     */
    fun onRemoteCodecRequest(requestedMode: Int): Boolean {
        Log.i(TAG, "Peer requested codec change to mode: $requestedMode")
        if (requestedMode in VivoLeanProtocol.CODEC_MODE_1600..VivoLeanProtocol.CODEC_MODE_450) {
            _currentCodecMode.value = requestedMode
            return true
        }
        return false
    }

    /**
     * Manual override of codec mode (e.g. from user settings).
     */
    fun setManualCodecMode(mode: Int) {
        _currentCodecMode.value = mode
        onCodecChangeRequested?.invoke(mode)
    }
}
