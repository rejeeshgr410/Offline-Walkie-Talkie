package com.example.offlinewalkietalkie.transport.bluetooth

/**
 * Pure, deterministic policy for a direct BLE link.
 *
 * GATT permits either phone to be the client, but starting both directions at
 * once is not a reliable connection strategy: it produces duplicate links,
 * duplicate pairing prompts and (on several Android Bluetooth stacks) a
 * STATUS_133 disconnect.  Pick one client deterministically instead.
 */
internal object BleConnectionPolicy {
    private const val UNKNOWN_ANDROID_ADDRESS = "02:00:00:00:00:00"

    fun shouldInitiateClient(
        localAddress: String?,
        remoteAddress: String,
        localName: String,
        remoteName: String
    ): Boolean {
        val local = localAddress?.uppercase()?.takeUnless { it == UNKNOWN_ANDROID_ADDRESS }
        if (local != null) return local < remoteAddress.uppercase()

        // A few Android builds redact the local adapter address.  Names keep
        // the election deterministic in that case.  With equal names neither
        // end auto-connects; the explicit Connect control remains available
        // and is safer than creating two racing GATT clients.
        return localName.lowercase() < remoteName.lowercase()
    }
}
