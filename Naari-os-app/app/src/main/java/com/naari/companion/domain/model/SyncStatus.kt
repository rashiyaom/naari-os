package com.naari.companion.domain.model

/**
 * Authoritative synchronization state between companion phone and watch firmware.
 * Ref: PRD Section 3 & Master Refactor Plan Phase 2.
 */
sealed interface SyncStatus {
    /** Watch memory is in sync with phone (contacts, settings, clock) */
    data class UpToDate(val timestampMs: Long = System.currentTimeMillis()) : SyncStatus

    /** Actively serializing and writing changes to watch GATT characteristics */
    object Updating : SyncStatus

    /** Failed to synchronize changes to watch with user-friendly reason */
    data class Failed(val reason: String) : SyncStatus

    val isUpdating: Boolean
        get() = this is Updating

    val isSuccess: Boolean
        get() = this is UpToDate

    val displayText: String
        get() = when (this) {
            is UpToDate -> "Synced"
            is Updating -> "Syncing..."
            is Failed -> "Sync failed"
        }
}
