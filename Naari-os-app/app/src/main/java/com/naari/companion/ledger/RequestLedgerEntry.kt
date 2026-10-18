package com.naari.companion.ledger

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naari.companion.protocol.WatchRequestType

/**
 * Authoritative Room entity recording every watch-initiated safety action request.
 * Persisted BEFORE initiating any hardware side effects.
 * Indexed by (deviceAddress, watchRequestId, receivedAt) for fast 60s window deduplication.
 */
@Entity(
    tableName = "request_ledger",
    indices = [
        Index(value = ["deviceAddress", "watchRequestId"]),
        Index(value = ["receivedAt"]),
        Index(value = ["status"])
    ]
)
data class RequestLedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val watchRequestId: Long,
    val deviceAddress: String,
    val type: WatchRequestType,
    val targetPhone: String? = null,
    val messagePayload: String? = null,
    val receivedAt: Long = System.currentTimeMillis(),
    val status: RequestLedgerStatus = RequestLedgerStatus.Received,
    val detail: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val failureReason: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
