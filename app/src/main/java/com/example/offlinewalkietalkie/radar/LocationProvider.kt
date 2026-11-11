package com.example.offlinewalkietalkie.radar

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles offline device GPS and location acquisition.
 * Works without internet using native device GPS satellites, cellular/Wi-Fi triangulation,
 * and Android Fused/Passive location providers.
 */
class LocationProvider(private val context: Context) : LocationListener {

    companion object {
        private const val TAG = "TL/LocationProvider"
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    private val _isGpsAvailable = MutableStateFlow(false)
    val isGpsAvailable = _isGpsAvailable.asStateFlow()

    private var isTracking = false

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (locationManager == null) return

        refreshLocation()

        if (isTracking) return

        try {
            val providers = mutableListOf<String>()

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                providers.add(LocationManager.GPS_PROVIDER)
                _isGpsAvailable.value = true
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                providers.add(LocationManager.NETWORK_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                providers.add(LocationManager.PASSIVE_PROVIDER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                        providers.add(LocationManager.FUSED_PROVIDER)
                    }
                } catch (_: Throwable) {}
            }

            Log.i(TAG, "Registering active location listeners for providers: $providers")
            for (provider in providers) {
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        1500L, // 1.5 seconds for snappy updates
                        1.0f,  // 1 meter movement threshold
                        this,
                        Looper.getMainLooper()
                    )
                    isTracking = true
                } catch (e: SecurityException) {
                    Log.w(TAG, "Location permission missing for provider $provider", e)
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to request updates for provider $provider", e)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException during location tracking start", e)
        }
    }

    /**
     * Polls the freshest last known location across all available providers immediately.
     */
    @SuppressLint("MissingPermission")
    fun refreshLocation() {
        if (locationManager == null) return
        try {
            val candidates = mutableListOf<Location>()
            try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { candidates.add(it) } } catch (_: Throwable) {}
            try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { candidates.add(it) } } catch (_: Throwable) {}
            try { locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)?.let { candidates.add(it) } } catch (_: Throwable) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)?.let { candidates.add(it) } } catch (_: Throwable) {}
            }

            // Pick the best candidate: prefer fresh locations (< 1 hour), then best accuracy
            val now = System.currentTimeMillis()
            val best = candidates.minByOrNull { loc ->
                val ageSec = (now - loc.time) / 1000L
                val accuracyPenalty = if (loc.hasAccuracy()) loc.accuracy else 100f
                ageSec + accuracyPenalty * 2
            }

            if (best != null) {
                val current = _currentLocation.value
                if (current == null || best.time >= current.time || (best.hasAccuracy() && best.accuracy < (current.accuracy ?: 999f))) {
                    Log.i(TAG, "Updated initial GPS location: lat=${best.latitude}, lon=${best.longitude}, acc=${if (best.hasAccuracy()) best.accuracy else "N/A"}m from ${best.provider}")
                    _currentLocation.value = best
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read last known location without permissions", e)
        }
    }

    fun stopTracking() {
        if (!isTracking || locationManager == null) return
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            // Ignore
        }
        isTracking = false
    }

    override fun onLocationChanged(location: Location) {
        val prev = _currentLocation.value
        // Only accept if newer or significantly more accurate
        if (prev == null || location.time >= prev.time || (location.hasAccuracy() && location.accuracy < (prev.accuracy ?: 999f))) {
            _currentLocation.value = location
            Log.d(TAG, "Location fix: ${location.latitude}, ${location.longitude} (acc=${if (location.hasAccuracy()) location.accuracy else "N/A"}m via ${location.provider})")
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) _isGpsAvailable.value = true
        startTracking()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) _isGpsAvailable.value = false
    }
}
