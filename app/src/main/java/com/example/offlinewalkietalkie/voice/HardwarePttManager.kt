package com.example.offlinewalkietalkie.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages physical button PTT (Volume Down, Headset Hook) for screen-off pocket operation,
 * tactile haptic confirmations, and battery optimization verification for background endurance.
 */
class HardwarePttManager(
    private val context: Context,
    private val onPttPressed: () -> Unit,
    private val onPttReleased: () -> Unit
) {
    companion object {
        private const val TAG = "TL/HardwarePtt"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _isVolumeKeyPttEnabled = MutableStateFlow(true)
    val isVolumeKeyPttEnabled = _isVolumeKeyPttEnabled.asStateFlow()

    private val _isBluetoothScoActive = MutableStateFlow(false)
    val isBluetoothScoActive = _isBluetoothScoActive.asStateFlow()

    private var isVolumeDownHeld = false

    fun setVolumeKeyPttEnabled(enabled: Boolean) {
        _isVolumeKeyPttEnabled.value = enabled
        Log.i(TAG, "Hardware Volume PTT enabled: $enabled")
    }

    /**
     * Checks whether the app is exempt from Android Doze mode and background battery optimizations.
     */
    fun isBatteryOptimizationIgnored(): Boolean {
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    /**
     * Creates an intent requesting battery optimization exemption for uninterrupted background reception.
     */
    fun createBatteryOptimizationIntent(): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Resiliently requests battery optimization exemption with multi-tier OEM fallbacks.
     */
    fun requestBatteryOptimization(targetContext: Context = context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${targetContext.packageName}")
            }
            if (targetContext !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            targetContext.startActivity(intent)
        } catch (e: Throwable) {
            Log.w(TAG, "Direct ignore battery prompt failed, trying system list fallback", e)
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    if (targetContext !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                targetContext.startActivity(fallback)
            } catch (e2: Throwable) {
                try {
                    val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${targetContext.packageName}")
                        if (targetContext !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    targetContext.startActivity(appSettings)
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Intercepts key down events.
     * @return true if event was consumed (prevents Android system volume change).
     */
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!_isVolumeKeyPttEnabled.value) return false

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            if (!isVolumeDownHeld) {
                isVolumeDownHeld = true
                vibrateTick(50)
                Log.i(TAG, "Hardware PTT button PRESSED ($keyCode)")
                onPttPressed()
            }
            return true // Consume event
        }
        return false
    }

    /**
     * Intercepts key up events.
     * @return true if event was consumed.
     */
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!_isVolumeKeyPttEnabled.value) return false

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            if (isVolumeDownHeld) {
                isVolumeDownHeld = false
                vibrateTick(30)
                Log.i(TAG, "Hardware PTT button RELEASED ($keyCode)")
                onPttReleased()
            }
            return true // Consume event
        }
        return false
    }

    fun startBluetoothSco() {
        try {
            audioManager?.let { am ->
                if (am.isBluetoothScoAvailableOffCall) {
                    am.startBluetoothSco()
                    am.isBluetoothScoOn = true
                    _isBluetoothScoActive.value = true
                    Log.i(TAG, "Bluetooth SCO started for headset audio")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bluetooth SCO", e)
        }
    }

    fun stopBluetoothSco() {
        try {
            audioManager?.let { am ->
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
                _isBluetoothScoActive.value = false
                Log.i(TAG, "Bluetooth SCO stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Bluetooth SCO", e)
        }
    }

    private fun vibrateTick(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            // Ignore if vibration fails
        }
    }
}
