package com.example.offlinewalkietalkie.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.offlinewalkietalkie.protocol.PeerTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Monitors local device battery percentage and health.
 * Broadcasts battery telemetry to peers and maintains live peer battery roster.
 */
class TelemetryManager(
    private val context: Context,
    private val localPeerId: String,
    private val broadcastTelemetry: suspend (PeerTelemetry) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        private const val TAG = "TL/Telemetry"
        private const val TELEMETRY_INTERVAL_MS = 20000L // 20s heartbeat
    }

    private val _localBatteryPercent = MutableStateFlow(getInitialBatteryLevel())
    val localBatteryPercent = _localBatteryPercent.asStateFlow()

    private val _isLocalCharging = MutableStateFlow(false)
    val isLocalCharging = _isLocalCharging.asStateFlow()

    private val _peerTelemetry = MutableStateFlow<Map<String, PeerTelemetry>>(emptyMap())
    val peerTelemetry = _peerTelemetry.asStateFlow()

    private var heartbeatJob: Job? = null
    private var isReceiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                if (level >= 0 && scale > 0) {
                    val percent = (level * 100) / scale
                    _localBatteryPercent.value = percent
                    _isLocalCharging.value = isCharging
                }
            }
        }
    }

    private fun getInitialBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 80
        } catch (e: Exception) {
            80
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                context.registerReceiver(batteryReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register battery receiver", e)
            }
        }

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    val telemetry = PeerTelemetry(
                        peerId = localPeerId,
                        batteryPercent = _localBatteryPercent.value,
                        isCharging = _isLocalCharging.value,
                        rssi = 0,
                        timestamp = System.currentTimeMillis()
                    )
                    broadcastTelemetry(telemetry)
                } catch (e: Exception) {
                    Log.d(TAG, "Error in telemetry loop: ${e.message}")
                }
                delay(TELEMETRY_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // Ignore
            }
            isReceiverRegistered = false
        }
    }

    fun handleIncomingTelemetry(telemetry: PeerTelemetry) {
        val current = _peerTelemetry.value.toMutableMap()
        current[telemetry.peerId] = telemetry
        _peerTelemetry.value = current
        Log.d(TAG, "Telemetry from ${telemetry.peerId}: ${telemetry.batteryPercent}% (charging=${telemetry.isCharging})")
    }

    fun getBatteryPercentForPeer(peerId: String): Int? {
        return _peerTelemetry.value[peerId]?.batteryPercent
    }
}
