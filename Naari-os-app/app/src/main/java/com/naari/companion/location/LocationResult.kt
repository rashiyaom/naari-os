package com.naari.companion.location

/**
 * Result outcome of attempting location acquisition.
 * Ref: PRD Section 7 & Master Prompt Task 11.
 */
sealed class LocationResult {
    /**
     * Fresh GPS or network fix acquired directly from hardware provider within freshness target (<= 30s).
     */
    data class Fresh(val fix: LocationFix) : LocationResult()

    /**
     * Fix acquired from cache (or fix with accuracy > 100m or age > 30s, but <= 120s cache expiry).
     * Truthfully labeled with age and never presented as fresh GPS.
     */
    data class Cached(val fix: LocationFix, val ageSeconds: Long) : LocationResult()

    /**
     * Timed out without receiving a fix, and no valid cached fix exists.
     */
    data class Timeout(val detail: String = "Location fix timed out without GPS/network response") : LocationResult()

    /**
     * Location permissions (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION) not granted.
     */
    data class PermissionDenied(val detail: String = "Location permissions not granted") : LocationResult()

    /**
     * Device location services / GPS toggle disabled in system settings.
     */
    data class Disabled(val detail: String = "Location services disabled on device") : LocationResult()

    /**
     * Hardware or provider error.
     */
    data class Error(val message: String) : LocationResult()
}
