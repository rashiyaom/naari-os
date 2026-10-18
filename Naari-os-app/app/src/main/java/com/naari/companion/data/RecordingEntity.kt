package com.naari.companion.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Synchronization lifecycle states for watch audio memos.
 * Ref: PRD Section 3 (Recordings) & Master Prompt Task 17.
 */
enum class RecordingSyncState {
    CapturedOnWatch,
    WaitingForTransfer,
    Transferring,
    VerifiedOnPhone,
    Failed
}

/**
 * Room entity storing metadata and sync status of a voice memo.
 */
@Entity(
    tableName = "recordings",
    indices = [
        Index(value = ["recordingId"], unique = true),
        Index(value = ["timestampMs"])
    ]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recordingId: String,
    val watchDeviceAddress: String,
    val timestampMs: Long,
    val durationMs: Long,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    val sha256Hex: String,
    val localFilePath: String? = null,
    val title: String,
    val syncState: RecordingSyncState = RecordingSyncState.CapturedOnWatch,
    val failureReason: String? = null
)
