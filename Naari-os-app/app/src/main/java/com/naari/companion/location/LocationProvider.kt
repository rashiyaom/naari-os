package com.naari.companion.location

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interface contract for acquiring fresh, bounded one-shot GPS fixes and composing guardian alerts.
 * Ref: PRD Section 7 & Master Prompt Task 11.
 */
interface LocationProvider {
    suspend fun acquireLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocationResult

    fun composeLocationAlert(
        baseMessage: String?,
        locationResult: LocationResult
    ): String

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}

/**
 * Authoritative implementation of [LocationProvider].
 *
 * Enforces:
 * - One-shot fresh location acquisition with bounded timeout
 * - Freshness target <= 30s and accuracy <= 100m
 * - Cached fallback labeled with its age and NEVER presented as fresh GPS
 * - Discards cached fixes older than 120s
 * - Handles approximate, denied, timeout, and offline/disabled states truthfully
 * - Never fabricates coordinates
 * - Formats human-readable guardian alert with Google Maps link
 */
class DefaultLocationProvider(
    private val client: LocationClient,
    private val freshnessTargetSeconds: Long = LocationFix.FRESHNESS_TARGET_SECONDS,
    private val accuracyTargetMeters: Float = LocationFix.ACCURACY_TARGET_METERS,
    private val cacheExpirySeconds: Long = LocationFix.CACHE_EXPIRY_SECONDS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LocationProvider {

    constructor(
        context: Context,
        freshnessTargetSeconds: Long = LocationFix.FRESHNESS_TARGET_SECONDS,
        accuracyTargetMeters: Float = LocationFix.ACCURACY_TARGET_METERS,
        cacheExpirySeconds: Long = LocationFix.CACHE_EXPIRY_SECONDS,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        client = AndroidLocationClient(context),
        freshnessTargetSeconds = freshnessTargetSeconds,
        accuracyTargetMeters = accuracyTargetMeters,
        cacheExpirySeconds = cacheExpirySeconds,
        ioDispatcher = ioDispatcher
    )

    override suspend fun acquireLocation(timeoutMs: Long): LocationResult = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()

        // 1. Check permissions
        if (!client.hasLocationPermission()) {
            val cached = client.getLastKnownLocation()
            if (cached != null && cached.ageSeconds(now) <= cacheExpirySeconds) {
                return@withContext LocationResult.Cached(cached, cached.ageSeconds(now))
            }
            return@withContext LocationResult.PermissionDenied()
        }

        // 2. Check location hardware enabled state
        if (!client.isLocationEnabled()) {
            val cached = client.getLastKnownLocation()
            if (cached != null && cached.ageSeconds(now) <= cacheExpirySeconds) {
                return@withContext LocationResult.Cached(cached, cached.ageSeconds(now))
            }
            return@withContext LocationResult.Disabled()
        }

        // 3. Attempt one-shot fresh acquisition within timeoutMs
        val freshFix = client.getCurrentLocation(timeoutMs)
        if (freshFix != null) {
            val age = freshFix.ageSeconds(now)
            val accuracy = freshFix.accuracyMeters

            if (age <= freshnessTargetSeconds && (accuracy == null || accuracy <= accuracyTargetMeters)) {
                return@withContext LocationResult.Fresh(freshFix)
            } else if (age <= cacheExpirySeconds) {
                // Approximate fix or fix older than target, but within cache window
                return@withContext LocationResult.Cached(freshFix, age)
            }
        }

        // 4. Fallback to cached fix if fresh acquisition failed or timed out
        val cached = client.getLastKnownLocation()
        if (cached != null && cached.ageSeconds(now) <= cacheExpirySeconds) {
            return@withContext LocationResult.Cached(cached, cached.ageSeconds(now))
        }

        LocationResult.Timeout()
    }

    override fun composeLocationAlert(
        baseMessage: String?,
        locationResult: LocationResult
    ): String {
        val prefix = if (!baseMessage.isNullOrBlank()) baseMessage.trim() else "Safety alert"
        return when (locationResult) {
            is LocationResult.Fresh -> {
                val acc = locationResult.fix.accuracyMeters?.let { " (accuracy: ±${it.toInt()}m)" } ?: ""
                "$prefix - Location: ${locationResult.fix.toMapUrl()}$acc"
            }
            is LocationResult.Cached -> {
                val acc = locationResult.fix.accuracyMeters?.let { " (±${it.toInt()}m)" } ?: ""
                "$prefix - Location: ${locationResult.fix.toMapUrl()}$acc (cached ${locationResult.ageSeconds}s ago)"
            }
            is LocationResult.Timeout -> {
                "$prefix - Location unavailable (GPS timeout)"
            }
            is LocationResult.PermissionDenied -> {
                "$prefix - Location unavailable (permission not granted)"
            }
            is LocationResult.Disabled -> {
                "$prefix - Location unavailable (location services disabled)"
            }
            is LocationResult.Error -> {
                "$prefix - Location unavailable (${locationResult.message})"
            }
        }
    }
}
