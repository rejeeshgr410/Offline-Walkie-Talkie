package com.example.offlinewalkietalkie.data

import org.json.JSONObject

data class TrustedContact(
    val contactId: String,
    val displayName: String,
    val publicKeyHex: String,
    val sasCode: String,
    val isVerified: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("contactId", contactId)
            put("displayName", displayName)
            put("publicKeyHex", publicKeyHex)
            put("sasCode", sasCode)
            put("isVerified", isVerified)
            put("lastSeen", lastSeen)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TrustedContact {
            return TrustedContact(
                contactId = json.getString("contactId"),
                displayName = json.getString("displayName"),
                publicKeyHex = json.getString("publicKeyHex"),
                sasCode = json.getString("sasCode"),
                isVerified = json.optBoolean("isVerified", false),
                lastSeen = json.optLong("lastSeen", System.currentTimeMillis())
            )
        }
    }
}
