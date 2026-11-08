package com.example.offlinewalkietalkie.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.offlinewalkietalkie.MainActivity
import com.example.offlinewalkietalkie.R
import kotlin.math.abs

/**
 * Floating PTT Bubble Overlay Service.
 * Renders a draggable, persistent PTT button over mapping apps, camera, and home screen.
 */
class FloatingPttService : Service() {

    companion object {
        private const val TAG = "TL/FloatingPtt"
        private const val NOTIFICATION_ID = 9021
        private const val CHANNEL_ID = "talkloop_floating_ptt"

        const val ACTION_START = "com.example.offlinewalkietalkie.action.START_FLOATING_PTT"
        const val ACTION_STOP = "com.example.offlinewalkietalkie.action.STOP_FLOATING_PTT"
        const val ACTION_UPDATE_STATE = "com.example.offlinewalkietalkie.action.UPDATE_FLOATING_STATE"

        const val EXTRA_STATE = "extra_state"
        const val EXTRA_SPEAKER_NAME = "extra_speaker_name"

        const val STATE_IDLE = "IDLE"
        const val STATE_TRANSMITTING = "TRANSMITTING"
        const val STATE_RECEIVING = "RECEIVING"
        const val STATE_SOS = "SOS"

        // Broadcast actions emitted by the floating view back to ViewModel / MainActivity
        const val BROADCAST_PTT_PRESS = "com.example.offlinewalkietalkie.broadcast.FLOATING_PTT_PRESS"
        const val BROADCAST_PTT_RELEASE = "com.example.offlinewalkietalkie.broadcast.FLOATING_PTT_RELEASE"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var orbBgDrawable: GradientDrawable? = null
    private var statusTextView: TextView? = null

    private var isTalking = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        setupFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_UPDATE_STATE -> {
                val state = intent.getStringExtra(EXTRA_STATE) ?: STATE_IDLE
                val speaker = intent.getStringExtra(EXTRA_SPEAKER_NAME) ?: ""
                updateOrbVisualState(state, speaker)
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingView() {
        if (floatingView != null || windowManager == null) return

        val windowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Programmatically create the floating pill container
        val density = resources.displayMetrics.density
        val pillSize = (68 * density).toInt()

        val rootLayout = FrameLayout(this).apply {
            this.layoutParams = FrameLayout.LayoutParams(pillSize, pillSize)
        }

        orbBgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(220, 20, 24, 30))
            setStroke((2 * density).toInt(), Color.argb(180, 0, 229, 255))
        }
        rootLayout.background = orbBgDrawable

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            val iconSize = (32 * density).toInt()
            this.layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        rootLayout.addView(icon)

        statusTextView = TextView(this).apply {
            text = "PTT"
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            this.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = (6 * density).toInt()
            }
        }
        rootLayout.addView(statusTextView)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false

        rootLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowLayoutParams.x
                    initialY = windowLayoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDrag = false

                    // If held, trigger PTT
                    rootLayout.postDelayed({
                        if (!isDrag && !isTalking) {
                            isTalking = true
                            sendBroadcast(Intent(BROADCAST_PTT_PRESS).setPackage(packageName))
                            updateOrbVisualState(STATE_TRANSMITTING, "TALKING")
                        }
                    }, 120)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDrag = true
                        if (isTalking) {
                            isTalking = false
                            sendBroadcast(Intent(BROADCAST_PTT_RELEASE).setPackage(packageName))
                            updateOrbVisualState(STATE_IDLE, "")
                        }
                    }
                    windowLayoutParams.x = initialX + deltaX
                    windowLayoutParams.y = initialY + deltaY
                    windowManager?.updateViewLayout(rootLayout, windowLayoutParams)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isTalking) {
                        isTalking = false
                        sendBroadcast(Intent(BROADCAST_PTT_RELEASE).setPackage(packageName))
                        updateOrbVisualState(STATE_IDLE, "")
                    } else if (!isDrag) {
                        // Quick click opens TalkLoop app
                        val launchIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(launchIntent)
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = rootLayout
        windowManager?.addView(rootLayout, windowLayoutParams)
        Log.i(TAG, "Floating PTT overlay view added")
    }

    private fun updateOrbVisualState(state: String, text: String) {
        val density = resources.displayMetrics.density
        when (state) {
            STATE_TRANSMITTING -> {
                orbBgDrawable?.setColor(Color.argb(240, 0, 200, 83)) // Vivid green
                orbBgDrawable?.setStroke((3 * density).toInt(), Color.WHITE)
                statusTextView?.text = "TALK"
            }
            STATE_RECEIVING -> {
                orbBgDrawable?.setColor(Color.argb(240, 3, 169, 244)) // Cyan / Light blue
                orbBgDrawable?.setStroke((3 * density).toInt(), Color.WHITE)
                statusTextView?.text = text.take(6).ifEmpty { "RECV" }
            }
            STATE_SOS -> {
                orbBgDrawable?.setColor(Color.argb(255, 229, 57, 53)) // Emergency Red
                orbBgDrawable?.setStroke((3 * density).toInt(), Color.YELLOW)
                statusTextView?.text = "SOS!"
            }
            else -> { // IDLE
                orbBgDrawable?.setColor(Color.argb(220, 20, 24, 30)) // Translucent dark glass
                orbBgDrawable?.setStroke((2 * density).toInt(), Color.argb(180, 0, 229, 255))
                statusTextView?.text = "PTT"
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TalkLoop Floating PTT",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating walkie-talkie PTT orb over other apps"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, FloatingPttService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TalkLoop Floating PTT Active")
            .setContentText("Tap floating orb to talk from any app")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close Overlay", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        floatingView = null
        Log.i(TAG, "Floating PTT service destroyed")
    }
}
