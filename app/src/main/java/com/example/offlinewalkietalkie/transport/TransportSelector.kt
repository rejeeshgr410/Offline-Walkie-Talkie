package com.example.offlinewalkietalkie.transport

import android.content.Context
import com.example.offlinewalkietalkie.util.CapabilityManager

/**
 * TalkLoop transport capability policy.
 *
 * BLE GATT is the conservative default while direct-link reliability is being
 * field-qualified. Optional PHY requests and capability checks do not guarantee
 * a negotiated PHY, a particular range, latency, or battery result.
 */
object TransportSelector {

    /** Bearers which are deliberately excluded from the normal release path. */
    fun isExperimental(type: TransportType): Boolean = when (type) {
        TransportType.BLE_L2CAP,
        TransportType.WIFI_AWARE,
        TransportType.WIFI_DIRECT,
        TransportType.BLUETOOTH_CLASSIC -> true
        else -> false
    }

    fun selectTransport(
        context: Context,
        isChannel: Boolean = false,
        isRelayMesh: Boolean = false,
        preferBleGatt: Boolean = true
    ): TransportType {
        // Direct BLE GATT remains the release candidate until other bearers pass field tests.
        return TransportType.BLE_GATT
    }

    fun isTransportAvailable(context: Context, type: TransportType): Boolean {
        return when (type) {
            TransportType.BLE_GATT -> CapabilityManager.isBleSupported(context)
            // These bearers remain available only to explicit experimental builds.
            // They are not safe to select in the shipping app until their own two-device
            // pairing, audio, reconnect, and background test matrices are complete.
            TransportType.BLE_L2CAP,
            TransportType.WIFI_DIRECT,
            TransportType.WIFI_AWARE -> false
            TransportType.NEARBY_CONNECTIONS -> CapabilityManager.isGooglePlayServicesAvailable(context)
            // There is no Bluetooth Classic transport registered in TransportManager.
            // Reporting it unavailable avoids silently falling back to a different bearer.
            TransportType.BLUETOOTH_CLASSIC -> context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH)
        }
    }

    fun unavailableReason(context: Context, type: TransportType): String? = when {
        isTransportAvailable(context, type) -> null
        isExperimental(type) -> "Experimental transport; not enabled until physical qualification is complete"
        type == TransportType.BLUETOOTH_CLASSIC -> "Bluetooth Classic is not implemented in this build"
        else -> "Not supported on this device"
    }

    fun getDisplayName(type: TransportType): String {
        return when (type) {
            TransportType.BLE_GATT -> "Bluetooth LE Direct"
            TransportType.BLE_L2CAP -> "Bluetooth LE L2CAP CoC (Experimental)"
            TransportType.WIFI_DIRECT -> "Wi-Fi Direct P2P (Experimental)"
            TransportType.WIFI_AWARE -> "Wi-Fi Aware (Experimental)"
            TransportType.NEARBY_CONNECTIONS -> "Google Nearby Connections"
            TransportType.BLUETOOTH_CLASSIC -> "Bluetooth Classic RFCOMM"
        }
    }
}
