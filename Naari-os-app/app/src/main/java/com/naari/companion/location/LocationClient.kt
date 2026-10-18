package com.naari.companion.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Interface contract abstracting platform location acquisition.
 */
interface LocationClient {
    fun hasLocationPermission(): Boolean
    fun isLocationEnabled(): Boolean
    suspend fun getCurrentLocation(timeoutMs: Long): LocationFix?
    fun getLastKnownLocation(): LocationFix?
}

/**
 * Concrete Android implementation using Android's platform LocationManager.
 */
class AndroidLocationClient(
    private val context: Context
) : LocationClient {

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(LocationManager::class.java)
    }

    override fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(): LocationFix? {
        if (!hasLocationPermission()) return null
        val manager = locationManager ?: return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                val loc = manager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            } catch (_: Exception) {
            }
        }

        return bestLocation?.toLocationFix(isCached = true)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override suspend fun getCurrentLocation(timeoutMs: Long): LocationFix? = withTimeoutOrNull(timeoutMs) {
        if (!hasLocationPermission()) return@withTimeoutOrNull null
        val manager = locationManager ?: return@withTimeoutOrNull null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                val executor = Executors.newSingleThreadExecutor()
                continuation.invokeOnCancellation { signal.cancel(); executor.shutdown() }
                try {
                    val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else {
                        LocationManager.NETWORK_PROVIDER
                    }
                    manager.getCurrentLocation(
                        provider,
                        signal,
                        executor
                    ) { location ->
                        executor.shutdown()
                        if (continuation.isActive) {
                            continuation.resume(location?.toLocationFix(isCached = false))
                        }
                    }
                } catch (e: Exception) {
                    executor.shutdown()
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        } else {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            manager.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(location.toLocationFix(isCached = false))
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                continuation.invokeOnCancellation {
                    try {
                        manager.removeUpdates(listener)
                    } catch (_: Exception) {}
                }

                try {
                    val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else {
                        LocationManager.NETWORK_PROVIDER
                    }
                    manager.requestSingleUpdate(provider, listener, null)
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    private fun Location.toLocationFix(isCached: Boolean): LocationFix {
        return LocationFix(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            timestamp = time,
            provider = provider ?: "unknown",
            isCached = isCached
        )
    }
}
