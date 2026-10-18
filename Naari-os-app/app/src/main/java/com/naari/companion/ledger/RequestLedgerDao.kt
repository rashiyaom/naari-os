package com.naari.companion.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for request ledger operations.
 * Ref: PRD Section 7 & Master Prompt Task 7.
 */
@Dao
interface RequestLedgerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RequestLedgerEntry): Long

    @Update
    suspend fun update(entry: RequestLedgerEntry)

    @Query(
        """
        UPDATE request_ledger
        SET status = :status,
            detail = :detail,
            failureReason = :failureReason,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Long,
        status: RequestLedgerStatus,
        detail: String? = null,
        failureReason: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query("SELECT * FROM request_ledger WHERE id = :id")
    suspend fun getById(id: Long): RequestLedgerEntry?

    @Query("SELECT * FROM request_ledger WHERE watchRequestId = :watchRequestId ORDER BY receivedAt DESC")
    suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry>

    @Query(
        """
        SELECT * FROM request_ledger
        WHERE deviceAddress = :deviceAddress
          AND watchRequestId = :watchRequestId
          AND receivedAt >= :sinceTimestamp
        ORDER BY receivedAt DESC
        """
    )
    suspend fun findRecentByWatchRequestId(
        deviceAddress: String,
        watchRequestId: Long,
        sinceTimestamp: Long
    ): List<RequestLedgerEntry>

    @Query("SELECT * FROM request_ledger WHERE status = 'Executing' ORDER BY receivedAt ASC")
    suspend fun getRecentInflight(): List<RequestLedgerEntry>

    @Query("SELECT * FROM request_ledger ORDER BY receivedAt DESC LIMIT :limit")
    fun observeAllRecent(limit: Int = 100): Flow<List<RequestLedgerEntry>>

    @Query(
        """
        UPDATE request_ledger
        SET status = 'UnknownOutcome',
            detail = 'In-flight during app restart; outcome unknown',
            updatedAt = :updatedAt
        WHERE status = 'Executing'
        """
    )
    suspend fun recoverInflightEntries(updatedAt: Long = System.currentTimeMillis()): Int
}
