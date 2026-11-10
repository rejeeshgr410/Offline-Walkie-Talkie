package com.example.offlinewalkietalkie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.service.AssistantSkillReceiver
import com.example.offlinewalkietalkie.service.FloatingPttService
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import com.example.offlinewalkietalkie.ui.CoverScreenCockpit
import com.example.offlinewalkietalkie.ui.WalkieTalkieViewModel

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "TL/MainActivity"

        const val ACTION_START_SOS = "com.example.offlinewalkietalkie.action.START_SOS"
        const val ACTION_CANCEL_SOS = "com.example.offlinewalkietalkie.action.CANCEL_SOS"
        const val ACTION_OPEN_INTERCOM = "com.example.offlinewalkietalkie.action.OPEN_INTERCOM"
        const val ACTION_CLOSE_INTERCOM = "com.example.offlinewalkietalkie.action.CLOSE_INTERCOM"
    }

    private val viewModel: WalkieTalkieViewModel by viewModels()

    private val floatingPttReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FloatingPttService.BROADCAST_PTT_PRESS -> viewModel.requestPtt()
                FloatingPttService.BROADCAST_PTT_RELEASE -> viewModel.releasePtt()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Connect foreground notification and headset media actions
        WalkieTalkieForegroundService.onPttPressFromHeadset = {
            viewModel.requestPtt()
        }
        WalkieTalkieForegroundService.onPttReleaseFromHeadset = {
            viewModel.releasePtt()
        }
        WalkieTalkieForegroundService.onPttToggleFromNotification = {
            val current = viewModel.floorState.value
            if (current is FloorState.Idle) {
                viewModel.requestPtt()
            } else {
                viewModel.releasePtt()
            }
        }
        WalkieTalkieForegroundService.onLeaveFromNotification = {
            viewModel.leaveSession()
        }

        // Connect Voice Assistant & Agent Skills (matching Vivo SkillImpl / SkillProviderService)
        AssistantSkillReceiver.onSkillPttRequest = {
            viewModel.requestPtt()
        }
        AssistantSkillReceiver.onSkillPttRelease = {
            viewModel.releasePtt()
        }
        AssistantSkillReceiver.onSkillStartSos = { body ->
            viewModel.startSos(body)
        }
        AssistantSkillReceiver.onSkillCancelSos = {
            viewModel.stopSos()
        }

        // Register receiver for floating PTT bubble broadcasts
        val filter = IntentFilter().apply {
            addAction(FloatingPttService.BROADCAST_PTT_PRESS)
            addAction(FloatingPttService.BROADCAST_PTT_RELEASE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(floatingPttReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(floatingPttReceiver, filter)
        }

        // Handle initial launch intent
        handleIntent(intent, null)

        setContent {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Outer cover display adaptation for Clamshell / Flip devices (Vivo X Flip / Z Flip / Razr)
                if (maxHeight < 450.dp || maxWidth < 340.dp) {
                    CoverScreenCockpit(viewModel = viewModel)
                } else {
                    MainNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, null)
    }

    /**
     * Android 15+ (API 35+) ComponentCaller security verification.
     * Modeled after Vivo's HomePage2.Q:
     * Verifies that external callers initiating privileged actions (like emergency SOS or intercom)
     * are trusted packages or have verified origins before execution.
     */
    fun onNewIntentWithCaller(intent: Intent, callerPackage: String?) {
        handleIntent(intent, callerPackage)
    }

    private fun handleIntent(intent: Intent?, callerPackage: String?) {
        if (intent == null) return
        val action = intent.action ?: return

        // Security verification: If caller is specified, ensure it is internal or a trusted system entity
        if (callerPackage != null && callerPackage != packageName) {
            val isTrustedCaller = callerPackage.startsWith("com.android.") ||
                callerPackage.startsWith("com.google.android.") ||
                callerPackage.startsWith("com.vivo.")
            if (!isTrustedCaller) {
                Log.w(TAG, "Rejected unverified external caller $callerPackage for action $action")
                return
            }
        }

        Log.i(TAG, "Processing verified intent action: $action (caller=$callerPackage)")
        when (action) {
            ACTION_START_SOS -> {
                val body = intent.getStringExtra("sos_body") ?: "Emergency SOS Distress"
                viewModel.startSos(body)
            }
            ACTION_CANCEL_SOS -> {
                viewModel.stopSos()
            }
            ACTION_OPEN_INTERCOM -> {
                viewModel.requestPtt()
            }
            ACTION_CLOSE_INTERCOM -> {
                viewModel.releasePtt()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.hardwarePttManager.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.hardwarePttManager.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(floatingPttReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
