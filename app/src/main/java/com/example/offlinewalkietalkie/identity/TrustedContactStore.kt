package com.example.offlinewalkietalkie.identity

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TrustedContactStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "trusted_contacts",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isContactTrusted(identityKey: String): Boolean {
        return sharedPrefs.getBoolean(identityKey, false)
    }

    fun trustContact(identityKey: String) {
        sharedPrefs.edit().putBoolean(identityKey, true).apply()
    }

    fun untrustContact(identityKey: String) {
        sharedPrefs.edit().remove(identityKey).apply()
    }
}
