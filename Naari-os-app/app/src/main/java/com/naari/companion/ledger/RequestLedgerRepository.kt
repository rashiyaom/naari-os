package com.naari.companion.ledger

import com.naari.companion.protocol.WatchRequest
import kotlinx.coroutines.flow.Flow

/**
 * Result of submitting a watch request to the persistent ledger.
 */
sealed class LedgerSubmissionResult {
    /**
     * Fresh request accepted and persisted to the ledger in [RequestLedgerStatus.Received] state.
     */
    data class Accepted(val entry: RequestLedgerEntry) : LedgerSubmissionResult()

    /**
     * Request rejected because an entry with identical watchRequestId was already received
     * from the same device within the deduplication window (default 60 seconds).
     * Carries the existing entry so the caller can re-transmit the last known reply status without re-executing.
     */
    data class Duplicate(val existingEntry: RequestLedgerEntry) : LedgerSubmissionResult()
}

/**
 * Repository interface governing authoritative safety request ledger operations,
 * deduplication, crash recovery, and state transitions.
 * Ref: PRD Section 7 & Master Prompt Task 7.
 */
interface RequestLedgerRepository {
    val recentEntries: Flow<List<RequestLedgerEntry>>

    suspend fun submitRequest(
        request: WatchRequest,
        deviceAddress: String,
        timestamp: Long = System.currentTimeMillis()
    ): LedgerSubmissionResult

    suspend fun transitionStatus(
        entryId: Long,
        newStatus: RequestLedgerStatus,
        detail: String? = null,
        failureReason: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<RequestLedgerEntry>

    suspend fun getById(entryId: Long): RequestLedgerEntry?

    suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry>

    suspend fun recoverInflightOnStartup(timestamp: Long = System.currentTimeMillis()): Int
}

/**
 * Authoritative implementation of [RequestLedgerRepository].
 */
class DefaultRequestLedgerRepository(
    private val dao: RequestLedgerDao,
    private val deduplicationWindowMs: Long = DEDUPLICATION_WINDOW_MS
) : RequestLedgerRepository {

    override val recentEntries: Flow<List<RequestLedgerEntry>> = dao.observeAllRecent(100)

    override suspend fun submitRequest(
        request: WatchRequest,
        deviceAddress: String,
        timestamp: Long
    ): LedgerSubmissionResult {
        // 1. Deduplication check within the configured time window
        val cutoff = timestamp - deduplicationWindowMs
        val duplicates = dao.findRecentByWatchRequestId(
            deviceAddress = deviceAddress,
            watchRequestId = request.id,
            sinceTimestamp = cutoff
        )

        if (duplicates.isNotEmpty()) {
            return LedgerSubmissionResult.Duplicate(duplicates.first())
        }

        // 2. Persist fresh entry to ledger BEFORE initiating any side effect
        val newEntry = RequestLedgerEntry(
            watchRequestId = request.id,
            deviceAddress = deviceAddress,
            type = request.type,
            targetPhone = request.phone,
            messagePayload = request.message,
            receivedAt = timestamp,
            status = RequestLedgerStatus.Received,
            updatedAt = timestamp
        )

        val insertedId = dao.insert(newEntry)
        return LedgerSubmissionResult.Accepted(newEntry.copy(id = insertedId))
    }

    override suspend fun transitionStatus(
        entryId: Long,
        newStatus: RequestLedgerStatus,
        detail: String?,
        failureReason: String?,
        timestamp: Long
    ): Result<RequestLedgerEntry> {
        val entry = dao.getById(entryId)
            ?: return Result.failure(NoSuchElementException("Request ledger entry $entryId not found"))

        if (!isValidTransition(entry.status, newStatus)) {
            return Result.failure(
                IllegalStateException("Invalid state transition from ${entry.status} to $newStatus for ledger entry $entryId")
            )
        }

        dao.updateStatus(
            id = entryId,
            status = newStatus,
            detail = detail ?: entry.detail,
            failureReason = failureReason ?: entry.failureReason,
            updatedAt = timestamp
        )

        val updated = dao.getById(entryId) ?: entry.copy(status = newStatus, updatedAt = timestamp)
        return Result.success(updated)
    }

    override suspend fun getById(entryId: Long): RequestLedgerEntry? {
        return dao.getById(entryId)
    }

    override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> {
        return dao.getByWatchRequestId(watchRequestId)
    }

    override suspend fun recoverInflightOnStartup(timestamp: Long): Int {
        return dao.recoverInflightEntries(updatedAt = timestamp)
    }

    companion object {
        const val DEDUPLICATION_WINDOW_MS = 60_000L

        /**
         * Validates legal state transitions for the Request Ledger state machine.
         */
        fun isValidTransition(from: RequestLedgerStatus, to: RequestLedgerStatus): Boolean {
            if (from == to) return true // Idempotent updates allowed
            if (from.isTerminal) return false // Terminal states cannot transition further

            return when (from) {
                RequestLedgerStatus.Received -> to in setOf(
                    RequestLedgerStatus.Executing,
                    RequestLedgerStatus.NeedsPhone,
                    RequestLedgerStatus.Failed
                )

                RequestLedgerStatus.Executing -> to in setOf(
                    RequestLedgerStatus.Sent,
                    RequestLedgerStatus.Calling,
                    RequestLedgerStatus.Located,
                    RequestLedgerStatus.Delivered,
                    RequestLedgerStatus.NeedsPhone,
                    RequestLedgerStatus.Failed,
                    RequestLedgerStatus.UnknownOutcome
                )

                RequestLedgerStatus.Located -> to in setOf(
                    RequestLedgerStatus.Sent,
                    RequestLedgerStatus.Failed
                )

                RequestLedgerStatus.Sent -> to in setOf(
                    RequestLedgerStatus.Delivered,
                    RequestLedgerStatus.Failed
                )

                RequestLedgerStatus.Calling -> to in setOf(
                    RequestLedgerStatus.Delivered,
                    RequestLedgerStatus.Failed,
                    RequestLedgerStatus.UnknownOutcome
                )

                else -> false
            }
        }
    }
}
