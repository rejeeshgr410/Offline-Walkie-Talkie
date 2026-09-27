package com.example.offlinewalkietalkie.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Section 12 Persistent Foreground Service.
 *
 * Runs during an active walkie-talkie session or channel:
 * - Declares foregroundServiceType="microphone|connectedDevice"
 * - Maintains CPU partial wake lock to allow screen-off voice reception (§12)
 * - Exposes ongoing notification with Open, Talk, Leave actions
 * - Stops immediately when the session ends
 */
class WalkieTalkieForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private lateinit var notificationController: NotificationController

    companion object {
        var onPttToggleFromNotification: (() -> Unit)? = null
        var onLeaveFromNotification: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        notificationController = NotificationController(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TalkLoop::ActiveSessionWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NotificationController.ACTION_STOP -> {
                stopServiceInternal()
                return START_NOT_STICKY
            }
            NotificationController.ACTION_TOGGLE_PTT -> {
                onPttToggleFromNotification?.invoke()
                return START_STICKY
            }
            NotificationController.ACTION_LEAVE -> {
                onLeaveFromNotification?.invoke()
                stopServiceInternal()
                return START_NOT_STICKY
            }
            NotificationController.ACTION_START, null -> {
                startServiceInternal(intent)
            }
        }
        return START_STICKY
    }

    private fun startServiceInternal(intent: Intent?) {
        val sessionName = intent?.getStringExtra(NotificationController.EXTRA_SESSION_NAME)
            ?: "Field Ops Alpha"
        val status = intent?.getStringExtra(NotificationController.EXTRA_STATUS)
            ?: "Encrypted (SAS Verified)"
        val speaker = intent?.getStringExtra(NotificationController.EXTRA_SPEAKER)
            ?: "Floor: Idle"

        val notification = notificationController.buildNotification(sessionName, status, speaker)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            val hasRecordAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasRecordAudio) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(NotificationController.NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                try {
                    startForeground(NotificationController.NOTIFICATION_ID, notification)
                } catch (e2: Exception) {}
            }
        } else {
            try {
                startForeground(NotificationController.NOTIFICATION_ID, notification)
            } catch (e: Exception) {}
        }

        if (!isRunning) {
            isRunning = true
            try {
                // No timeout: a session is call-like and may outlast any fixed limit. A
                // 10-minute cap silently ended screen-off reception mid-session. It is
                // always released in stopServiceInternal().
                wakeLock?.acquire()
            } catch (e: Exception) {
                // Ignore wake lock acquire failures
            }
        }
    }

    private fun stopServiceInternal() {
        isRunning = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopServiceInternal()
    }
}
