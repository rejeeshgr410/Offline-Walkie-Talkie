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
        return when (type) {
            TransportType.BLE_GATT -> CapabilityManager.isBleSupported(context)
            TransportType.BLE_L2CAP -> CapabilityManager.isBleSupported(context) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
            TransportType.WIFI_DIRECT -> CapabilityManager.isWifiDirectSupported(context)
            TransportType.WIFI_AWARE -> CapabilityManager.isWifiAwareSupported(context)
            TransportType.NEARBY_CONNECTIONS -> CapabilityManager.isGooglePlayServicesAvailable(context)
            TransportType.BLUETOOTH_CLASSIC -> CapabilityManager.isBluetoothEnabled(context)
        }
    }

    fun getDisplayName(type: TransportType): String {
        return when (type) {
            TransportType.BLE_GATT -> "Bluetooth LE Long-Range (Vivo Coded PHY)"
            TransportType.BLE_L2CAP -> "Bluetooth LE L2CAP CoC"
            TransportType.WIFI_DIRECT -> "Wi-Fi Direct P2P"
            TransportType.WIFI_AWARE -> "Wi-Fi Aware (NAN)"
            TransportType.NEARBY_CONNECTIONS -> "Google Nearby Connections"
            TransportType.BLUETOOTH_CLASSIC -> "Bluetooth Classic RFCOMM"
        }
    }
}
