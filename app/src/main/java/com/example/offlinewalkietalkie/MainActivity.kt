package com.example.offlinewalkietalkie

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.offlinewalkietalkie.protocol.FloorState
import com.example.offlinewalkietalkie.service.WalkieTalkieForegroundService
import com.example.offlinewalkietalkie.ui.WalkieTalkieViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: WalkieTalkieViewModel by viewModels()

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

        setContent {
            MainNavigation(viewModel = viewModel)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (viewModel.isInSession.value && event?.repeatCount == 0) {
                viewModel.requestPtt()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (viewModel.isInSession.value) {
                viewModel.releasePtt()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
