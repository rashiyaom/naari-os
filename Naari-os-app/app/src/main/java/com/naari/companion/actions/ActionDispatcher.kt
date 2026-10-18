package com.naari.companion.actions

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ledger.LedgerSubmissionResult
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.location.LocationExecutor
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.PhoneReplyStatus
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central orchestrator wiring inbound BLE watch requests to the persistent request ledger
 * and delegating execution to the respective action executors (SMS, Call, Location).
 * Ref: PRD Section 7 & Master Prompt Task 12.
 */
interface ActionDispatcher {
    /**
     * Starts observing [BleSessionManager.incomingRequests] on the provided [scope].
     */
    fun startListening(scope: CoroutineScope): Job

    /**
     * Submits and dispatches an inbound [WatchRequest].
     * Deduplicates identical requests received within the deduplication window (60s).
     */
    suspend fun dispatchRequest(request: WatchRequest, deviceAddress: String): Result<Unit>

    /**
     * Manually retries a failed or interrupted action request with explicit user confirmation.
     * Enforces duplicate-risk protection: never retries already-delivered or active actions.
     */
    suspend fun retryRequest(entryId: Long): Result<Unit>
}

/**
 * Authoritative implementation of [ActionDispatcher].
 *
 * Enforces:
 * - Persisting request identity to Room ledger BEFORE initiating any hardware side effect
 * - Deduplicating re-transmitted requests within 60s window (ZERO second send)
 * - Routing by type: SMS, CALL, LOCATION, LOCATION_SMS
 * - Manual retry with strict state validation (only Failed or UnknownOutcome)
 * - SAFETY RULE: NEVER automatically replay an uncertain action
 */
class DefaultActionDispatcher(
    private val bleSessionManager: BleSessionManager,
    private val ledgerRepository: RequestLedgerRepository,
    private val smsExecutor: SmsExecutor,
    private val callExecutor: CallExecutor,
    private val locationExecutor: LocationExecutor,
    private val onboardingRepository: com.naari.companion.onboarding.OnboardingRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ActionDispatcher {

    override fun startListening(scope: CoroutineScope): Job {
        return scope.launch(ioDispatcher) {
            bleSessionManager.incomingRequests.collect { request ->
                val deviceAddress = resolveCurrentDeviceAddress()
                dispatchRequest(request, deviceAddress)
            }
        }
    }

    override suspend fun dispatchRequest(
        request: WatchRequest,
        deviceAddress: String
    ): Result<Unit> = withContext(ioDispatcher) {
        // 1. Submit to ledger for deduplication and persistence BEFORE executing
        val submission = ledgerRepository.submitRequest(request, deviceAddress)

        when (submission) {
            is LedgerSubmissionResult.Duplicate -> {
                // Deduplicate: Re-transmit the last known status on the reply characteristic without re-executing
                val existing = submission.existingEntry
                val replyStatus = mapLedgerStatusToReplyStatus(existing.status)
                if (replyStatus != null) {
                    bleSessionManager.sendReply(
                        PhoneReply(
                            id = existing.watchRequestId,
                            status = replyStatus,
                            detail = existing.detail ?: "Deduplicated request"
                        )
                    )
                }
                return@withContext Result.success(Unit)
            }

            is LedgerSubmissionResult.Accepted -> {
                // Execute side effect based on request type
                executeActionForRequest(request)
                return@withContext Result.success(Unit)
            }
        }
    }

    override suspend fun retryRequest(entryId: Long): Result<Unit> = withContext(ioDispatcher) {
        val entry = ledgerRepository.getById(entryId)
            ?: return@withContext Result.failure(NoSuchElementException("Ledger entry $entryId not found"))

        // SAFETY CONSTRAINT: Only failed or interrupted requests can be manually retried
        if (entry.status != RequestLedgerStatus.Failed && entry.status != RequestLedgerStatus.UnknownOutcome) {
            return@withContext Result.failure(
                IllegalStateException("Cannot retry request with status ${entry.status}. Only Failed or UnknownOutcome entries can be retried.")
            )
        }

        // Transition status to Executing with user-retry note
        ledgerRepository.transitionStatus(
            entryId = entry.id,
            newStatus = RequestLedgerStatus.Executing,
            detail = "Manual user retry initiated"
        )

        val retryRequest = WatchRequest(
            id = entry.watchRequestId,
            type = entry.type,
            phone = entry.targetPhone,
            message = entry.messagePayload
        )

        executeActionForRequest(retryRequest)
        Result.success(Unit)
    }

    private suspend fun executeActionForRequest(request: WatchRequest) {
        when (request.type) {
            WatchRequestType.SMS -> {
                smsExecutor.sendSms(
                    requestId = request.id,
                    phone = request.phone ?: "",
                    message = request.message ?: ""
                )
            }
            WatchRequestType.CALL -> {
                callExecutor.executeCall(
                    requestId = request.id,
                    phoneNumber = request.phone ?: ""
                )
            }
            WatchRequestType.LOCATION -> {
                locationExecutor.executeLocation(
                    requestId = request.id
                )
            }
            WatchRequestType.LOCATION_SMS -> {
                locationExecutor.executeLocationSms(
                    requestId = request.id,
                    phoneNumber = request.phone ?: "",
                    baseMessage = request.message
                )
            }
        }
    }

    private suspend fun resolveCurrentDeviceAddress(): String {
        return onboardingRepository?.pairedDeviceAddress?.firstOrNull() ?: "BONDED_WATCH"
    }

    companion object {
        fun mapLedgerStatusToReplyStatus(status: RequestLedgerStatus): PhoneReplyStatus? {
            return when (status) {
                RequestLedgerStatus.Received, RequestLedgerStatus.Executing -> PhoneReplyStatus.RECEIVED
                RequestLedgerStatus.Sent -> PhoneReplyStatus.SENT
                RequestLedgerStatus.Delivered -> PhoneReplyStatus.DELIVERED
                RequestLedgerStatus.Located -> PhoneReplyStatus.LOCATED
                RequestLedgerStatus.Calling -> PhoneReplyStatus.CALLING
                RequestLedgerStatus.NeedsPhone -> PhoneReplyStatus.NEEDS_PHONE
                RequestLedgerStatus.Failed, RequestLedgerStatus.UnknownOutcome -> PhoneReplyStatus.FAILED
            }
        }
    }
}
