package com.example.offlinewalkietalkie.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.offlinewalkietalkie.channel.TextMessage
import com.example.offlinewalkietalkie.channel.TextDeliveryStatus
import org.json.JSONArray
import org.json.JSONObject

/** Bounded encrypted history. Interrupted outgoing messages remain explicitly retryable. */
class TextMessageStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "text_history", MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): List<TextMessage> = decode(prefs.getString("messages", "[]") ?: "[]")
    fun save(messages: List<TextMessage>) {
        check(prefs.edit().putString("messages", encode(messages.takeLast(1000))).commit())
    }

    companion object {
        fun encode(messages: List<TextMessage>): String = JSONArray().apply {
            messages.forEach { m -> put(JSONObject().put("id", m.messageId).put("sequence", m.sequence)
                .put("sender", m.senderMemberId).put("name", m.senderName).put("content", m.content)
                .put("timestamp", m.timestampEpochMs).put("status", m.status.name).put("read", m.isRead).put("outgoing", m.isOutgoing)) }
        }.toString()

        fun decode(json: String): List<TextMessage> = runCatching {
            val rows = JSONArray(json)
            ((rows.length() - 1000).coerceAtLeast(0) until rows.length()).mapNotNull { index -> runCatching {
                val m = rows.getJSONObject(index)
                val status = TextDeliveryStatus.valueOf(m.getString("status"))
                TextMessage(m.getString("id"), m.getInt("sequence"), m.getLong("sender"),
                    m.getString("name"), m.getString("content"), m.getLong("timestamp"),
                    if (status == TextDeliveryStatus.SENDING) TextDeliveryStatus.FAILED else status,
                    m.optBoolean("read"), m.optBoolean("outgoing"))
            }.getOrNull() }
        }.getOrDefault(emptyList())
    }
}
