package com.naari.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local voice memos catalog.
 */
@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity): Int

    @Query("SELECT * FROM recordings ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE recordingId = :recordingId LIMIT 1")
    suspend fun getByRecordingId(recordingId: String): RecordingEntity?

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE recordings SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String): Int

    @Query("UPDATE recordings SET transferredBytes = :transferredBytes, syncState = :syncState, failureReason = :failureReason WHERE id = :id")
    suspend fun updateSyncProgress(
        id: Long,
        transferredBytes: Long,
        syncState: RecordingSyncState,
        failureReason: String?
    ): Int

    @Query("UPDATE recordings SET syncState = :syncState, localFilePath = :localFilePath, transferredBytes = :totalBytes, failureReason = NULL WHERE id = :id")
    suspend fun markVerified(
        id: Long,
        localFilePath: String,
        totalBytes: Long,
        syncState: RecordingSyncState = RecordingSyncState.VerifiedOnPhone
    ): Int
}
