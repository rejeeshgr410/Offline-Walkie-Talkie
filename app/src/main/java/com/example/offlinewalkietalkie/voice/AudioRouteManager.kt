package com.example.offlinewalkietalkie.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Section 6 Audio Route & Focus Manager.
 *
 * Manages:
 * - Speakerphone vs Earpiece routing via AudioManager.setCommunicationDevice on API 31+,
 *   with fallback on older supported APIs.
 * - Transient audio focus requesting for walkie-talkie sessions.
 * - Phone call interruption detection: releases floor, stops capture & playback.
 */
class AudioRouteManager(private val context: Context) {

    private val TAG = "TL/AudioRoute"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusGranted = false

    /** Volume levels as the user had them, so a session can hand them back on teardown. */
    private var savedMusicVolume: Int? = null
    private var savedVoiceCallVolume: Int? = null

    var onInterruptedByCall: (() -> Unit)? = null
    var onFocusRegained: (() -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Incoming phone call or another app took audio focus
                onInterruptedByCall?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                onFocusRegained?.invoke()
            }
        }
    }

    /**
     * Request transient audio focus for walkie-talkie voice playback.
     */
    fun requestAudioFocus(): Boolean {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // Re-requesting builds a fresh AudioFocusRequest that replaces the tracked one,
        // leaving the previous request permanently un-abandoned.
        if (focusGranted) return true
        Log.i(TAG, "requestAudioFocus: mode set to MODE_IN_COMMUNICATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            audioFocusRequest = request
            val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusGranted = granted
            Log.i(TAG, "audioFocus granted=$granted")
            granted
        } else {
            @Suppress("DEPRECATION")
            val granted = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusGranted = granted
            Log.i(TAG, "audioFocus granted=$granted (legacy)")
            granted
        }
    }

    /**
     * Abandon audio focus when idle or session ends.
     */
    fun abandonAudioFocus() {
        if (!focusGranted) return
        focusGranted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    /**
     * Set communication device route: Speakerphone vs Earpiece.
     */
    fun setSpeakerphone(enabled: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // Always set this because some OEM devices (like Samsung) require it even on Android 12+
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enabled
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = if (enabled) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val devices = audioManager.availableCommunicationDevices
            val targetDevice = devices.firstOrNull { it.type == targetType }
            if (targetDevice != null) {
                val ok = audioManager.setCommunicationDevice(targetDevice)
                Log.i(TAG, "setCommunicationDevice(${if (enabled) "SPEAKER" else "EARPIECE"}) -> $ok")
            } else {
                Log.w(TAG, "Target device type $targetType not found, clearing")
                audioManager.clearCommunicationDevice()
            }
        }
    }

    /**
     * Speaker playback goes out as USAGE_MEDIA (STREAM_MUSIC); earpiece playback goes out as
     * USAGE_VOICE_COMMUNICATION (STREAM_VOICE_CALL). Boosting the other one does nothing.
     */
    fun ensureVolume(useSpeaker: Boolean) {
        val stream = if (useSpeaker) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
        val label = if (useSpeaker) "STREAM_MUSIC" else "STREAM_VOICE_CALL"
        try {
            val current = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            val target = (max * 0.7).toInt().coerceAtLeast(1)
            if (current >= target) {
                Log.i(TAG, "$label volume OK: $current/$max")
                return
            }
            if (useSpeaker) {
                if (savedMusicVolume == null) savedMusicVolume = current
            } else {
                if (savedVoiceCallVolume == null) savedVoiceCallVolume = current
            }
            audioManager.setStreamVolume(stream, target, 0)
            Log.i(TAG, "boosted $label volume $current -> $target (max=$max)")
        } catch (e: Throwable) {
            // Do Not Disturb policy can refuse volume changes.
            Log.w(TAG, "could not adjust $label volume: ${e.message}")
        }
    }

    private fun restoreVolumes() {
        savedMusicVolume?.let { original ->
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
                Log.i(TAG, "restored STREAM_MUSIC volume -> $original")
            } catch (e: Throwable) {
                Log.w(TAG, "could not restore STREAM_MUSIC volume: ${e.message}")
            }
        }
        savedVoiceCallVolume?.let { original ->
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, original, 0)
                Log.i(TAG, "restored STREAM_VOICE_CALL volume -> $original")
            } catch (e: Throwable) {
                Log.w(TAG, "could not restore STREAM_VOICE_CALL volume: ${e.message}")
            }
        }
        savedMusicVolume = null
        savedVoiceCallVolume = null
    }

    fun clearRouting() {
        Log.i(TAG, "clearRouting -> MODE_NORMAL")
        restoreVolumes()
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
