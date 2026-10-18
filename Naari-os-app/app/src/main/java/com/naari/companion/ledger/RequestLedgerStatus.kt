package com.naari.companion.ledger

/**
 * Immutable progression states for inbound watch requests.
 * Ref: PRD Section 7 & Master Prompt Task 7.
 */
enum class RequestLedgerStatus {
    /** Persisted to database; side-effect execution not yet started. */
    Received,

    /** Side effect initiated (SMS transmission queued, call intent launching). */
    Executing,

    /** SMS handed off to carrier telephony layer. */
    Sent,

    /** Confirmation received via SMS delivered callback PDU. */
    Delivered,

    /** Telephony call initiated via TelecomManager. */
    Calling,

    /** Requires phone user action (emergency dialer opened). */
    NeedsPhone,

    /** GPS coordinates acquired for location request. */
    Located,

    /** Terminal failure (SIM error, permission revoked, carrier rejection). */
    Failed,

    /**
     * App was killed or crashed while request was Executing.
     * Never auto-retried. Displayed with prominent "Unknown -- Check Phone" status.
     */
    UnknownOutcome;

    val isTerminal: Boolean
        get() = this in setOf(Delivered, NeedsPhone, Failed, UnknownOutcome)
}
