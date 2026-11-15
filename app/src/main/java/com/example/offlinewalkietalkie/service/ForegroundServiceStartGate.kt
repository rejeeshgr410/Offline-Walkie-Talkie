package com.example.offlinewalkietalkie.service

/**
 * Keeps microphone foreground-service starts inside an activity-visible window.
 *
 * Android 12+ rejects most background starts, and Android 14+ applies the same
 * rule to the while-in-use microphone permission. A BLE reconnect can complete
 * after the activity has gone away, so the session remains valid but the
 * foreground notification is deferred until the user returns to the app.
 */
class ForegroundServiceStartGate {
    private var activityVisible = false
    private var sessionActive = false
    private var pendingStart = false

    fun onActivityVisible(): Boolean {
        activityVisible = true
        return sessionActive && pendingStart.also { pendingStart = false }
    }

    fun onActivityHidden() {
        activityVisible = false
    }

    fun onSessionChanged(active: Boolean): Boolean {
        sessionActive = active
        if (!active) {
            pendingStart = false
            return false
        }
        if (!activityVisible) {
            pendingStart = true
            return false
        }
        return true
    }

    /** Retry on the next visible lifecycle window after an OS policy rejection. */
    fun onStartDenied() {
        if (sessionActive) pendingStart = true
    }
}
