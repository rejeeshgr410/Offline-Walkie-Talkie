package com.example.offlinewalkietalkie.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Audio output routing options for walkie-talkie operation.
 */
enum class AudioRoute(val displayName: String) {
    SPEAKER("Speakerphone"),
    EARPIECE("Private Earpiece"),
    BLUETOOTH_SCO("Bluetooth Headset"),
    WIRED_HEADSET("Wired Headset")
}

/**
 * Section 6 Audio Route & Focus Manager.
 *
 * Manages:
 * - Dynamic audio route selection (Speaker, Earpiece, Bluetooth SCO, Wired Headset)
 * - Hardware device attachment/detachment detection via AudioDeviceCallback and SCO broadcast receivers
 * - Transient audio focus requesting for walkie-talkie sessions
 * - Phone call interruption detection: releases floor, stops capture & playback
 */
class AudioRouteManager(private val context: Context) {

    companion object {
        private const val TAG = "TL/AudioRoute"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusGranted = false

    private val _currentRoute = MutableStateFlow(AudioRoute.SPEAKER)
    val currentRoute = _currentRoute.asStateFlow()

    private val _availableRoutes = MutableStateFlow<List<AudioRoute>>(listOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE))
    val availableRoutes = _availableRoutes.asStateFlow()

    private var savedMusicVolume: Int? = null
    private var savedVoiceCallVolume: Int? = null

    var onInterruptedByCall: (() -> Unit)? = null
    var onFocusRegained: (() -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.w(TAG, "Audio focus lost ($focusChange) - incoming call or higher priority audio")
                onInterruptedByCall?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus regained")
                onFocusRegained?.invoke()
            }
        }
    }

    // AudioDeviceCallback for API 23+ hardware detection
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAvailableRoutes()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshAvailableRoutes()
        }
    }

    // Broadcast receiver for SCO and Headset plug changes
    private val audioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                    Log.i(TAG, "SCO audio state changed: $state")
                    if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                        _currentRoute.value = AudioRoute.BLUETOOTH_SCO
                    } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED && _currentRoute.value == AudioRoute.BLUETOOTH_SCO) {
                        setAudioRoute(AudioRoute.SPEAKER)
                    }
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    val plugged = intent.getIntExtra("state", 0) == 1
                    Log.i(TAG, "Wired headset plug state: $plugged")
                    refreshAvailableRoutes()
                    if (plugged) {
                        setAudioRoute(AudioRoute.WIRED_HEADSET)
                    } else if (_currentRoute.value == AudioRoute.WIRED_HEADSET) {
                        setAudioRoute(AudioRoute.SPEAKER)
                    }
                }
            }
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(audioReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(audioReceiver, filter)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register audioReceiver", e)
        }
        refreshAvailableRoutes()
    }

    fun refreshAvailableRoutes() {
        val routes = mutableListOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE)
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)

        val hasBt = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        if (hasBt || audioManager.isBluetoothScoAvailableOffCall) {
            routes.add(AudioRoute.BLUETOOTH_SCO)
        }

        val hasWired = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        if (hasWired) {
            routes.add(AudioRoute.WIRED_HEADSET)
        }

        _availableRoutes.value = routes.distinct()
        if (_currentRoute.value !in _availableRoutes.value) {
            setAudioRoute(AudioRoute.SPEAKER)
        }
    }

    /**
     * Backward-compatible convenience wrapper.
     */
    fun setSpeakerphone(enabled: Boolean) {
        setAudioRoute(if (enabled) AudioRoute.SPEAKER else AudioRoute.EARPIECE)
    }

    /**
     * Switch active audio route between Speakerphone, Earpiece, Bluetooth SCO, and Wired Headset.
     */
    fun setAudioRoute(route: AudioRoute) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        Log.i(TAG, "Switching audio route to $route")

        when (route) {
            AudioRoute.SPEAKER -> {
                stopBluetoothScoInternal()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) audioManager.setCommunicationDevice(speaker)
                    else audioManager.clearCommunicationDevice()
                }
                _currentRoute.value = AudioRoute.SPEAKER
            }
            AudioRoute.EARPIECE -> {
                stopBluetoothScoInternal()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) audioManager.setCommunicationDevice(earpiece)
                    else audioManager.clearCommunicationDevice()
                }
                _currentRoute.value = AudioRoute.EARPIECE
            }
            AudioRoute.BLUETOOTH_SCO -> {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val btDev = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    if (btDev != null) {
                        val ok = audioManager.setCommunicationDevice(btDev)
                        Log.i(TAG, "setCommunicationDevice(BLUETOOTH) -> $ok")
                    }
                }
                startBluetoothScoInternal()
                _currentRoute.value = AudioRoute.BLUETOOTH_SCO
            }
            AudioRoute.WIRED_HEADSET -> {
                stopBluetoothScoInternal()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val wiredDev = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    }
                    if (wiredDev != null) audioManager.setCommunicationDevice(wiredDev)
                }
                _currentRoute.value = AudioRoute.WIRED_HEADSET
            }
        }
    }

    private fun startBluetoothScoInternal() {
        try {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                Log.i(TAG, "startBluetoothSco requested")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start Bluetooth SCO: ${e.message}")
        }
    }

    private fun stopBluetoothScoInternal() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                Log.i(TAG, "stopBluetoothSco requested")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop Bluetooth SCO: ${e.message}")
        }
    }

    fun requestAudioFocus(): Boolean {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
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

    fun ensureVolume(useSpeaker: Boolean) {
        val stream = if (useSpeaker) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
        val label = if (useSpeaker) "STREAM_MUSIC" else "STREAM_VOICE_CALL"
        try {
            val current = audioManager.getStreamVolume(stream)
            val max = audioManager.getStreamMaxVolume(stream)
            val target = (max * 0.7).toInt().coerceAtLeast(1)
            if (current >= target) return
            if (useSpeaker) {
                if (savedMusicVolume == null) savedMusicVolume = current
            } else {
                if (savedVoiceCallVolume == null) savedVoiceCallVolume = current
            }
            audioManager.setStreamVolume(stream, target, 0)
        } catch (e: Throwable) {
            Log.w(TAG, "could not adjust $label volume: ${e.message}")
        }
    }

    fun clearRouting() {
        Log.i(TAG, "clearRouting -> MODE_NORMAL")
        stopBluetoothScoInternal()
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            context.unregisterReceiver(audioReceiver)
        } catch (e: Exception) {}
    }
}
