package com.example.offlinewalkietalkie.transport

import android.content.Context
import com.example.offlinewalkietalkie.util.CapabilityManager

/**
 * Vivo-Style Walkie-Talkie Transport Selection Policy.
 *
 * Direct Bluetooth LE (GATT / Coded PHY) is the dedicated, universal transport medium:
 * - Sub-200ms connection latency.
 * - Up to 1 km outdoor line-of-sight range.
 * - Ultra-low battery consumption.
 * - 100% Offline: zero Wi-Fi, cellular, or Google Play Services dependency.
 */
object TransportSelector {

    fun selectTransport(
        context: Context,
        isChannel: Boolean = false,
        isRelayMesh: Boolean = false,
        preferBleGatt: Boolean = true
    ): TransportType {
        // Vivo OEM Walkie-Talkie transport medium: Direct BLE GATT
        return TransportType.BLE_GATT
    }

    fun isTransportAvailable(context: Context, type: TransportType): Boolean {
        return CapabilityManager.isBleSupported(context)
    }

    fun getDisplayName(type: TransportType): String {
        return "Bluetooth LE Long-Range (Vivo Coded PHY)"
    }
}
