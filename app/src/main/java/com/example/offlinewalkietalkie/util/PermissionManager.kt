package com.example.offlinewalkietalkie.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionManager {

    /**
     * Nearby Connections permission matrix. Getting this wrong is silent: the API simply
     * reports MISSING_PERMISSION and no endpoint is ever discovered.
     *
     *  - API 29..31  ACCESS_FINE_LOCATION is mandatory for BLE scanning.
     *  - API 31+     BLUETOOTH_SCAN / ADVERTISE / CONNECT replace the legacy Bluetooth perms.
     *  - API 33+     NEARBY_WIFI_DEVICES is required for the Wi-Fi upgrade path.
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }

        // Still required on Android 12 (API 31) itself, alongside the new Bluetooth permissions.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isAudioPermissionGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.RECORD_AUDIO)

    /**
     * Every permission Nearby Connections needs on this API level, not just one of them.
     * A partial grant discovers nothing, so treating "any" as success hides the failure.
     */
    fun isRadioPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetooth = isGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                isGranted(context, Manifest.permission.BLUETOOTH_CONNECT) &&
                isGranted(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            val wifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                isGranted(context, Manifest.permission.NEARBY_WIFI_DEVICES)
            val location = Build.VERSION.SDK_INT > Build.VERSION_CODES.S ||
                isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
            bluetooth && wifi && location
        } else {
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /** The permissions still missing, for a targeted re-request. */
    fun missingPermissions(context: Context): List<String> =
        getRequiredPermissions().filterNot { isGranted(context, it) }

    fun hasAllPermissions(context: Context): Boolean =
        isAudioPermissionGranted(context) && isRadioPermissionGranted(context)
}
