package com.example.offlinewalkietalkie.sos

import android.location.Location
import android.util.Log
import com.example.offlinewalkietalkie.protocol.SosAck
import com.example.offlinewalkietalkie.protocol.SosBeacon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Manages offline SOS distress beacon broadcasting, multi-hop mesh forwarding,
 * high-priority audible alerting, and delivery acknowledgment receipts.
 */
class SosManager(
    private val localSenderId: String,
    private val localSenderName: String,
    private val broadcastFrame: suspend (SosBeacon, ingressPeerId: String?) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "TL/SosManager"
        private const val BROADCAST_INTERVAL_MS = 4000L
    }

    private val alarmPlayer = EmergencyAlarmPlayer()

    private val _isBroadcastingSos = MutableStateFlow(false)
    val isBroadcastingSos = _isBroadcastingSos.asStateFlow()

    private val _mySosBeacon = MutableStateFlow<SosBeacon?>(null)
    val mySosBeacon = _mySosBeacon.asStateFlow()

    private val _incomingAlerts = MutableStateFlow<List<SosBeacon>>(emptyList())
    val incomingAlerts = _incomingAlerts.asStateFlow()

    private val _isAlarmPlaying = MutableStateFlow(false)
    val isAlarmPlaying = _isAlarmPlaying.asStateFlow()

    private val _acknowledgedResponders = MutableStateFlow<List<SosAck>>(emptyList())
    val acknowledgedResponders = _acknowledgedResponders.asStateFlow()

    var onSendAck: (suspend (SosAck, ingressPeerId: String?) -> Unit)? = null
    var onBroadcastCancel: (suspend (String) -> Unit)? = null

    private var broadcastJob: Job? = null

    // Deduplication cache for incoming alerts: key = "${senderId}_${timestamp}"
    private val seenAlertKeys = Collections.synchronizedSet(mutableSetOf<String>())

    fun startSos(
        message: String,
        location: Location?,
        batteryPercent: Int
    ) {
        val beacon = SosBeacon(
            senderId = localSenderId,
            senderName = localSenderName,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            altitude = location?.altitude ?: 0.0,
            batteryPercent = batteryPercent,
            distressMessage = message,
            timestamp = System.currentTimeMillis(),
            ttl = 3
        )

        _mySosBeacon.value = beacon
        _isBroadcastingSos.value = true
        _acknowledgedResponders.value = emptyList()

        broadcastJob?.cancel()
        broadcastJob = scope.launch {
            Log.i(TAG, "Starting SOS broadcast beacon: '${beacon.distressMessage}' at (${beacon.latitude}, ${beacon.longitude})")
            while (isActive && _isBroadcastingSos.value) {
                try {
                    broadcastFrame(_mySosBeacon.value ?: beacon, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting SOS beacon", e)
                }
                delay(BROADCAST_INTERVAL_MS)
            }
        }
    }

    fun stopSos() {
        Log.i(TAG, "Stopping SOS broadcast")
        _isBroadcastingSos.value = false
        broadcastJob?.cancel()
        broadcastJob = null
        _mySosBeacon.value = null
        _acknowledgedResponders.value = emptyList()
        scope.launch {
            try {
                onBroadcastCancel?.invoke(localSenderId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast SOS_CANCEL: ${e.message}")
            }
        }
    }

    fun handleIncomingSosCancel(senderId: String) {
        Log.i(TAG, "Distress sender $senderId cancelled emergency SOS - dismissing alert and silencing alarm")
        dismissAlert(senderId)
        silenceAlarm()
    }

    suspend fun handleIncomingSos(beacon: SosBeacon, ingressPeerId: String?) {
        // Drop our own broadcast
        if (beacon.senderId == localSenderId) return

        val alertKey = "${beacon.senderId}_${beacon.timestamp}"
        val isFirstTimeSeen = seenAlertKeys.add(alertKey)

        // Update list of alerts
        val currentList = _incomingAlerts.value.filter { it.senderId != beacon.senderId }.toMutableList()
        currentList.add(0, beacon)
        _incomingAlerts.value = currentList

        // Immediately transmit SOS_ACK back across mesh to distress sender
        val ack = SosAck(
            sosSenderId = beacon.senderId,
            sosTimestamp = beacon.timestamp,
            responderId = localSenderId,
            responderName = localSenderName,
            ackTimestamp = System.currentTimeMillis()
        )
        try {
            onSendAck?.invoke(ack, ingressPeerId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send SOS_ACK: ${e.message}")
        }

        if (isFirstTimeSeen) {
            Log.w(TAG, "EMERGENCY SOS ALERT RECEIVED from ${beacon.senderName}: '${beacon.distressMessage}' at (${beacon.latitude}, ${beacon.longitude})")
            alarmPlayer.startAlarm()
            _isAlarmPlaying.value = true
        }

        // Multi-hop mesh relay: decrement TTL and forward
        if (beacon.ttl > 1) {
            val relayBeacon = beacon.copy(ttl = beacon.ttl - 1)
            Log.i(TAG, "Mesh relaying SOS beacon from ${beacon.senderName} (remaining TTL=${relayBeacon.ttl})")
            broadcastFrame(relayBeacon, ingressPeerId)
        }
    }

    fun handleIncomingSosAck(ack: SosAck) {
        if (ack.sosSenderId != localSenderId) return
        val current = _acknowledgedResponders.value
        if (current.none { it.responderId == ack.responderId }) {
            _acknowledgedResponders.value = current + ack
            Log.i(TAG, "Distress beacon ACK receipt confirmed from responder: ${ack.responderName} (${ack.responderId})")
        }
    }

    fun silenceAlarm() {
        Log.i(TAG, "Emergency siren silenced by user")
        alarmPlayer.stopAlarm()
        _isAlarmPlaying.value = false
    }

    fun dismissAlert(senderId: String) {
        val filtered = _incomingAlerts.value.filter { it.senderId != senderId }
        _incomingAlerts.value = filtered
        if (filtered.isEmpty()) {
            silenceAlarm()
        }
    }
}
