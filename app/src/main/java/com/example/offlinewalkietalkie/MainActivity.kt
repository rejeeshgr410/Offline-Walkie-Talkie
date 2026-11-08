package com.example.offlinewalkietalkie

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.service.FloatingPttService
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import com.example.offlinewalkietalkie.ui.WalkieTalkieViewModel

class MainActivity : ComponentActivity() {
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

        // Connect foreground notification actions
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

        setContent {
            MainNavigation(viewModel = viewModel)
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
