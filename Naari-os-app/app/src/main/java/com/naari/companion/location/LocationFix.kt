package com.naari.companion.location

import com.naari.companion.protocol.ProtocolConstants

/**
 * Immutable model representing an acquired geographic location fix.
 * Ref: PRD Section 7 & Master Prompt Task 11.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String = "gps",
    val isCached: Boolean = false
) {
    init {
        require(!latitude.isNaN() && !latitude.isInfinite() && latitude in ProtocolConstants.MIN_LATITUDE..ProtocolConstants.MAX_LATITUDE) {
            "Latitude $latitude out of valid bounds [${ProtocolConstants.MIN_LATITUDE}, ${ProtocolConstants.MAX_LATITUDE}]"
        }
        require(!longitude.isNaN() && !longitude.isInfinite() && longitude in ProtocolConstants.MIN_LONGITUDE..ProtocolConstants.MAX_LONGITUDE) {
            "Longitude $longitude out of valid bounds [${ProtocolConstants.MIN_LONGITUDE}, ${ProtocolConstants.MAX_LONGITUDE}]"
        }
    }

    fun ageSeconds(currentTime: Long = System.currentTimeMillis()): Long =
        ((currentTime - timestamp).coerceAtLeast(0L)) / 1000L

    fun isFresh(currentTime: Long = System.currentTimeMillis()): Boolean =
        !isCached && ageSeconds(currentTime) <= FRESHNESS_TARGET_SECONDS &&
                (accuracyMeters == null || accuracyMeters <= ACCURACY_TARGET_METERS)

    fun toMapUrl(): String = "https://maps.google.com/?q=$latitude,$longitude"

    companion object {
        const val FRESHNESS_TARGET_SECONDS = 30L
        const val ACCURACY_TARGET_METERS = 100f
        const val CACHE_EXPIRY_SECONDS = 120L
    }
}
