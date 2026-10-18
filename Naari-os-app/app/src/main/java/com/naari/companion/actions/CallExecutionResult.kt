package com.naari.companion.actions

import com.naari.companion.ledger.RequestLedgerStatus

/**
 * Result outcome of executing a watch-initiated voice call request.
 * Ref: PRD Section 7 & Master Prompt Task 10.
 */
sealed class CallExecutionResult {
    /**
     * Call launch was accepted by Android OS / TelecomManager.
     * Status is [RequestLedgerStatus.Calling].
     * Note: 'calling' means launch was accepted, NOT that the recipient answered.
     */
    data class Calling(
        val detail: String = "Call placement accepted by telephony subsystem"
    ) : CallExecutionResult()

    /**
     * Direct call placement is unavailable or target number is an emergency service.
     * Phone system dialer was opened with pre-filled number.
     * Status is [RequestLedgerStatus.NeedsPhone].
     * SAFETY RULE: Never label dialer opening as placed call.
     */
    data class NeedsPhone(
        val reason: String
    ) : CallExecutionResult()

    /**
     * Terminal failure (e.g. invalid phone number syntax).
     * Status is [RequestLedgerStatus.Failed].
     */
    data class Failed(
        val reason: String
    ) : CallExecutionResult()
}
