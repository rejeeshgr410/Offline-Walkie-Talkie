package com.example.offlinewalkietalkie.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent

/**
 * Section 12 Persistent Foreground Service.
 *
 * Runs during an active walkie-talkie session or channel:
 * - Declares foregroundServiceType="microphone|connectedDevice"
 * - Maintains CPU partial wake lock to allow screen-off voice reception (§12)
 * - Exposes ongoing notification with Open, Talk, Leave actions
 * - Maintains active MediaSession to intercept wired/Bluetooth headset PTT buttons while screen is off
 * - Stops immediately when the session ends
 */
class WalkieTalkieForegroundService : Service() {

    companion object {
        private const val TAG = "TL/ForegroundService"
        var onPttToggleFromNotification: (() -> Unit)? = null
        var onPttPressFromHeadset: (() -> Unit)? = null
        var onPttReleaseFromHeadset: (() -> Unit)? = null
        var onLeaveFromNotification: (() -> Unit)? = null
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private lateinit var notificationController: NotificationController
    private var mediaSession: MediaSession? = null

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

        setupMediaSession()
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(this, "TalkLoopPttSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        } ?: return super.onMediaButtonEvent(mediaButtonIntent)

                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                when (keyEvent.action) {
                                    KeyEvent.ACTION_DOWN -> {
                                        if (keyEvent.repeatCount == 0) {
                                            Log.i(TAG, "Headset PTT Media Button DOWN (${keyEvent.keyCode})")
                                            onPttPressFromHeadset?.invoke() ?: onPttToggleFromNotification?.invoke()
                                        }
                                        return true
                                    }
                                    KeyEvent.ACTION_UP -> {
                                        Log.i(TAG, "Headset PTT Media Button UP (${keyEvent.keyCode})")
                                        onPttReleaseFromHeadset?.invoke()
                                        return true
                                    }
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })

                val stateBuilder = PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP
                    )
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                setPlaybackState(stateBuilder.build())
                isActive = true
                Log.i(TAG, "MediaSession active for wired & Bluetooth headset PTT interception")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize MediaSession: ${e.message}")
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

        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
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
