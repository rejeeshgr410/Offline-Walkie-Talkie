package com.example.offlinewalkietalkie.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores admission material encrypted at rest; never persists live radio or AUTH state. */
class SessionRecoveryStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "session_recovery",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun load(): String? = prefs.getString("group", null)
    fun save(checkpoint: String?) {
        val edit = prefs.edit()
        if (checkpoint == null) edit.remove("group") else edit.putString("group", checkpoint)
        check(edit.commit()) { "Could not save session recovery checkpoint" }
    }
}
