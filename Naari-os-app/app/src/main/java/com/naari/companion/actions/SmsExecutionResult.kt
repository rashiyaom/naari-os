package com.naari.companion.actions

import com.naari.companion.ledger.RequestLedgerStatus

/**
 * Outcome of watch-initiated SMS execution.
 */
sealed class SmsExecutionResult {
    /**
     * SMS was successfully dispatched.
     * Status is either [RequestLedgerStatus.Delivered] if carrier PDU confirmed delivery,
     * or [RequestLedgerStatus.Sent] if delivery receipt timed out (45s).
     */
    data class Success(
        val status: RequestLedgerStatus,
        val detail: String
    ) : SmsExecutionResult()

    /**
     * Terminal failure occurred (carrier error, radio off, invalid destination, etc.).
     */
    data class Failed(
        val reason: String,
        val errorCode: Int? = null
    ) : SmsExecutionResult()
}
