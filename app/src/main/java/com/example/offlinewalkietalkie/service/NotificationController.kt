package com.example.offlinewalkietalkie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.offlinewalkietalkie.MainActivity

/**
 * Section 12 Notification Controller.
 * Manages the ongoing active-session notification with:
 * - Session/channel name
 * - Security / Reconnect status
 * - Current speaker
 * - Quick actions: Open, Talk, Leave
 */
class NotificationController(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "talkloop_active_session"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.offlinewalkietalkie.ACTION_START"
        const val ACTION_STOP = "com.example.offlinewalkietalkie.ACTION_STOP"
        const val ACTION_TOGGLE_PTT = "com.example.offlinewalkietalkie.ACTION_TOGGLE_PTT"
        const val ACTION_LEAVE = "com.example.offlinewalkietalkie.ACTION_LEAVE"

        const val EXTRA_SESSION_NAME = "extra_session_name"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_SPEAKER = "extra_speaker"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Walkie-Talkie Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing offline walkie-talkie communication"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        sessionName: String = "Field Ops Alpha",
        status: String = "Encrypted (SAS Verified)",
        speaker: String = "Floor: Idle"
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val talkIntent = Intent(context, WalkieTalkieForegroundService::class.java).apply {
            action = ACTION_TOGGLE_PTT
        }
        val talkPendingIntent = PendingIntent.getService(
            context,
            1,
            talkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val leaveIntent = Intent(context, WalkieTalkieForegroundService::class.java).apply {
            action = ACTION_LEAVE
        }
        val leavePendingIntent = PendingIntent.getService(
            context,
            2,
            leaveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(sessionName)
            .setContentText("$status • $speaker")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, "Talk", talkPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Leave", leavePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun update(sessionName: String, status: String, speaker: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(sessionName, status, speaker))
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
