package com.example.offlinewalkietalkie.transport

import android.content.Context
import com.example.offlinewalkietalkie.util.CapabilityManager

/**
 * Section 5 & Section 19.15 Transport Selection and Migration Policy.
 *
 * Selection Rules:
 * 1. Primary: Nearby Connections where Google Play Services is available.
 * 2. Non-GMS Fallbacks (e.g. Huawei):
 *    - 1:1: BLE L2CAP CoC, or Wi-Fi Aware/Direct.
 *    - Channels: Wi-Fi Aware where supported; otherwise Wi-Fi Direct with host as group owner.
 *    - Mesh Relaying: Wi-Fi Aware where hardware supports multiple peers; honest label when unavailable.
 * 3. Never crash, block, or show internet-related errors when Play Services is absent.
 */
object TransportSelector {

    fun selectTransport(
        context: Context,
        isChannel: Boolean = false,
        isRelayMesh: Boolean = false,
        preferBleGatt: Boolean = true
    ): TransportType {
        // Phase 3: BLE GATT (Long-Range BLE with Coded PHY) is primary for Walkie-Talkie
        // when BLE is supported, offering sub-200ms connection, up to 1km range, low battery,
        // and zero Wi-Fi disruption.
        if (preferBleGatt && CapabilityManager.isBleSupported(context)) {
            return TransportType.BLE_GATT
        }

        // 1. Google Play Services available -> Nearby Connections
        if (CapabilityManager.isGooglePlayServicesAvailable(context)) {
            return TransportType.NEARBY_CONNECTIONS
        }

        // 2. Non-GMS fallbacks
        if (isRelayMesh) {
            // Mesh relaying on non-GMS prefers Wi-Fi Aware if supported
            if (CapabilityManager.isWifiAwareSupported(context)) {
                return TransportType.WIFI_AWARE
            }
            // BLE cannot do multi-hop standalone mesh (§5 & §19.15)
            // Fall back to BLE for 1:1 links
            if (CapabilityManager.isBleSupported(context)) {
                return TransportType.BLE_L2CAP
            }
        }

        if (isChannel) {
            // Group / Channels: Wi-Fi Aware if supported, else Wi-Fi Direct (host group owner)
            if (CapabilityManager.isWifiAwareSupported(context)) {
                return TransportType.WIFI_AWARE
            }
            if (CapabilityManager.isWifiDirectSupported(context)) {
                return TransportType.WIFI_DIRECT
            }
            if (CapabilityManager.isBleSupported(context)) {
                return TransportType.BLE_L2CAP
            }
        }

        // 3. Direct 1:1: BLE L2CAP CoC is universal on API 29+
        if (CapabilityManager.isBleSupported(context)) {
            return TransportType.BLE_L2CAP
        }

        if (CapabilityManager.isWifiDirectSupported(context)) {
            return TransportType.WIFI_DIRECT
        }

        return TransportType.BLUETOOTH_CLASSIC
    }

    fun isTransportAvailable(context: Context, type: TransportType): Boolean {
        return when (type) {
            TransportType.NEARBY_CONNECTIONS -> CapabilityManager.isGooglePlayServicesAvailable(context)
            TransportType.BLE_GATT -> CapabilityManager.isBleSupported(context)
            TransportType.BLE_L2CAP -> CapabilityManager.isBleSupported(context)
            TransportType.WIFI_AWARE -> CapabilityManager.isWifiAwareSupported(context)
            TransportType.WIFI_DIRECT -> CapabilityManager.isWifiDirectSupported(context)
            TransportType.BLUETOOTH_CLASSIC -> CapabilityManager.isBluetoothEnabled(context)
        }
    }

    fun getDisplayName(type: TransportType): String {
        return when (type) {
            TransportType.NEARBY_CONNECTIONS -> "Google Nearby Connections (Offline)"
            TransportType.BLE_GATT -> "Bluetooth LE Long-Range (GATT / Coded PHY)"
            TransportType.BLE_L2CAP -> "Bluetooth LE (L2CAP CoC Native)"
            TransportType.WIFI_AWARE -> "Wi-Fi Aware (NAN P2P)"
            TransportType.WIFI_DIRECT -> "Wi-Fi Direct (P2P Group)"
            TransportType.BLUETOOTH_CLASSIC -> "Bluetooth Classic (Legacy)"
        }
    }
}
