package com.example.offlinewalkietalkie.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Voice Assistant & AI Agent Skill Receiver.
 *
 * Modeled after Vivo's SkillImpl and OpenSkill / IntentKit architecture:
 * Allows Google Assistant, Gemini on Android, quick settings, or system automation
 * to command the walkie-talkie hands-free:
 * - ACTION_OPEN_INTERCOM: Requests PTT floor or joins default tactical channel.
 * - ACTION_CLOSE_INTERCOM: Releases PTT or terminates session.
 * - ACTION_START_SOS: Broadcasts an emergency distress alert with optional custom message.
 * - ACTION_CANCEL_SOS: Cancels active emergency distress beacon.
 */
class AssistantSkillReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TL/AssistantSkill"

        const val ACTION_OPEN_INTERCOM = "com.example.offlinewalkietalkie.action.OPEN_INTERCOM"
        const val ACTION_CLOSE_INTERCOM = "com.example.offlinewalkietalkie.action.CLOSE_INTERCOM"
        const val ACTION_START_SOS = "com.example.offlinewalkietalkie.action.START_SOS"
        const val ACTION_CANCEL_SOS = "com.example.offlinewalkietalkie.action.CANCEL_SOS"

        const val EXTRA_SOS_BODY = "sos_body"

        // Callback hooks for active foreground service or activity
        var onSkillPttRequest: (() -> Unit)? = null
        var onSkillPttRelease: (() -> Unit)? = null
        var onSkillStartSos: ((String) -> Unit)? = null
        var onSkillCancelSos: (() -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Assistant skill invoked: action=$action")

        when (action) {
            ACTION_OPEN_INTERCOM -> {
                onSkillPttRequest?.invoke()
            }
            ACTION_CLOSE_INTERCOM -> {
                onSkillPttRelease?.invoke()
            }
            ACTION_START_SOS -> {
                val body = intent.getStringExtra(EXTRA_SOS_BODY) ?: "Emergency SOS Distress"
                onSkillStartSos?.invoke(body)
            }
            ACTION_CANCEL_SOS -> {
                onSkillCancelSos?.invoke()
            }
        }
    }
}
