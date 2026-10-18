package com.naari.companion.guardians

/**
 * Truthful synchronization state of the 2-slot guardian profile with the watch hardware.
 * Ref: PRD Section 7 & 8: Never show "Saved" without qualifier if watch has not confirmed.
 */
sealed class GuardianSyncState {
    /** Both slots are unconfigured. */
    data object Empty : GuardianSyncState()

    /** Write was confirmed by watch via characteristic e2 write response. */
    data object SyncedToWatch : GuardianSyncState()

    /** Saved to Room database; watch is disconnected or unready. Will sync automatically upon connection. */
    data object PendingSync : GuardianSyncState()

    /** BLE write to characteristic e2 is currently in flight. */
    data object Syncing : GuardianSyncState()

    /** Saved to Room database, but BLE write to watch returned failure or timed out. */
    data class SyncFailed(val message: String) : GuardianSyncState()

    val displayText: String
        get() = when (this) {
            is Empty -> "Not configured"
            is SyncedToWatch -> "Saved to watch"
            is PendingSync -> "Saved on phone, will sync when watch connects"
            is Syncing -> "Syncing to watch..."
            is SyncFailed -> "Saved on phone, watch update failed"
        }
}
