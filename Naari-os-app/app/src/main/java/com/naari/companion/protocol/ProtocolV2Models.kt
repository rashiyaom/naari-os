package com.naari.companion.protocol

/**
 * Operating mode of the watch hardware.
 */
enum class WatchMode {
    SAFE,
    WATCH,
    EMERGENCY
}

/**
 * Negotiated hardware and firmware capabilities for the watch.
 * Ref: PRD Section 6 & docs/PROTOCOL_V2.md.
 */
data class WatchCapabilities(
    val wallpaper: Boolean = false,
    val brightness: Boolean = false,
    val volume: Boolean = false,
    val fallDetection: Boolean = false,
    val watchMode: Boolean = false,
    val findWatch: Boolean = false,
    val timeSync: Boolean = false,
    val safeCheckIn: Boolean = false,
    val profileReadBack: Boolean = false,
    val audioTransfer: Boolean = false
) {
    val hasAnyCapability: Boolean
        get() = wallpaper || brightness || volume || fallDetection || watchMode ||
                findWatch || timeSync || safeCheckIn || profileReadBack || audioTransfer

    companion object {
        /**
         * Standard Protocol v1 firmware capability snapshot (no v2 controls supported).
         */
        val NONE = WatchCapabilities()

        /**
         * Full Protocol v2 firmware capability snapshot.
         */
        val ALL = WatchCapabilities(
            wallpaper = true,
            brightness = true,
            volume = true,
            fallDetection = true,
            watchMode = true,
            findWatch = true,
            timeSync = true,
            safeCheckIn = true,
            profileReadBack = true,
            audioTransfer = true
        )

        /**
         * Parse capabilities from a list of capability strings reported by watch firmware.
         */
        fun fromList(list: List<String>): WatchCapabilities {
            val set = list.toSet()
            return WatchCapabilities(
                wallpaper = set.contains("wallpaper"),
                brightness = set.contains("brightness"),
                volume = set.contains("volume"),
                fallDetection = set.contains("fall_detection"),
                watchMode = set.contains("watch_mode"),
                findWatch = set.contains("find_watch"),
                timeSync = set.contains("time_sync"),
                safeCheckIn = set.contains("safe_check_in"),
                profileReadBack = set.contains("profile_read"),
                audioTransfer = set.contains("audio_transfer")
            )
        }
    }
}

/**
 * Snapshot of watch state returned by get_status or asynchronous event notifications.
 */
data class WatchStatusSnapshot(
    val bootId: Long = 0L,
    val sequence: Long = 0L,
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val activeWallpaper: Int? = null,
    val brightness: Int? = null,
    val volume: Int? = null,
    val fallDetectionEnabled: Boolean? = null,
    val mode: WatchMode? = null,
    val capabilities: WatchCapabilities = WatchCapabilities.NONE,
    val lastSyncTimestampMs: Long = System.currentTimeMillis()
)

/**
 * Outcome status of a Protocol v2 control command.
 */
enum class ControlStatus {
    Applied,
    Accepted,
    Rejected
}

/**
 * Control response received from the watch.
 */
data class ControlResponse(
    val version: Int = 2,
    val commandId: Int,
    val status: ControlStatus,
    val error: String? = null,
    val appliedData: Map<String, Any?> = emptyMap()
)
