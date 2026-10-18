package com.naari.companion.actions

import android.content.Context
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.PhoneReplyStatus
import com.naari.companion.protocol.ProtocolV1Codec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interface contract for executing cellular phone call requests.
 * Ref: PRD Section 7 & Master Prompt Task 10.
 */
interface CallExecutor {
    suspend fun executeCall(
        requestId: Long,
        phoneNumber: String
    ): CallExecutionResult
}

/**
 * Authoritative implementation of [CallExecutor].
 *
 * Enforces:
 * - Direct placement via TelecomManager with CALL_PHONE consent
 * - 'calling' status means launch was accepted by telephony system, NOT answered
 * - Emergency numbers (112, 911, 1091, 100, 108, etc.) MUST open system dialer and return 'needs_phone'
 * - Missing CALL_PHONE permission MUST open system dialer and return 'needs_phone'
 * - SAFETY RULE: Never label dialer opening as placed call
 * - SAFETY RULE: Zero automatic test calls
 * - Updates Room Request Ledger persistently before/after actions
 * - Replies over BLE with matching protocol statuses (received -> calling / needs_phone / failed)
 */
class DefaultCallExecutor(
    private val launcher: CallLauncher,
    private val emergencyDetector: EmergencyNumberDetector,
    private val ledgerRepository: RequestLedgerRepository? = null,
    private val bleSessionManager: BleSessionManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CallExecutor {

    constructor(
        context: Context,
        ledgerRepository: RequestLedgerRepository? = null,
        bleSessionManager: BleSessionManager? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        launcher = AndroidCallLauncher(context),
        emergencyDetector = DefaultEmergencyNumberDetector(context),
        ledgerRepository = ledgerRepository,
        bleSessionManager = bleSessionManager,
        ioDispatcher = ioDispatcher
    )

    override suspend fun executeCall(
        requestId: Long,
        phoneNumber: String
    ): CallExecutionResult = withContext(ioDispatcher) {
        val cleanPhone = phoneNumber.trim()

        // 1. Validate phone number format
        if (!ProtocolV1Codec.validatePhoneNumber(cleanPhone) || cleanPhone.isEmpty()) {
            val errorReason = "Invalid phone number format: '$cleanPhone'"
            updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = errorReason)
            sendBleReply(requestId, PhoneReplyStatus.FAILED, errorReason)
            return@withContext CallExecutionResult.Failed(errorReason)
        }

        // 2. Send RECEIVED reply first over BLE and mark ledger as Executing
        sendBleReply(requestId, PhoneReplyStatus.RECEIVED, "Call request accepted by phone")
        updateLedgerStatus(requestId, RequestLedgerStatus.Executing, detail = "Evaluating call routing")

        // 3. Emergency number check: redirect to dialer with needs_phone
        if (emergencyDetector.isEmergencyNumber(cleanPhone)) {
            val reason = "Emergency number '$cleanPhone' requires confirmation in phone dialer"
            launcher.openDialer(cleanPhone)
            updateLedgerStatus(requestId, RequestLedgerStatus.NeedsPhone, detail = reason)
            sendBleReply(requestId, PhoneReplyStatus.NEEDS_PHONE, reason)
            return@withContext CallExecutionResult.NeedsPhone(reason)
        }

        // 4. Check CALL_PHONE permission consent
        if (!launcher.hasCallPermission()) {
            val reason = "CALL_PHONE permission not granted; opened phone dialer"
            launcher.openDialer(cleanPhone)
            updateLedgerStatus(requestId, RequestLedgerStatus.NeedsPhone, detail = reason)
            sendBleReply(requestId, PhoneReplyStatus.NEEDS_PHONE, reason)
            return@withContext CallExecutionResult.NeedsPhone(reason)
        }

        // 5. Attempt direct call placement
        val placed = launcher.placeDirectCall(cleanPhone)
        if (placed) {
            // 'calling' means launch accepted by TelecomManager, not answered.
            val detail = "Direct call placement initiated to $cleanPhone"
            updateLedgerStatus(requestId, RequestLedgerStatus.Calling, detail = detail)
            sendBleReply(requestId, PhoneReplyStatus.CALLING, "Call launched")
            CallExecutionResult.Calling(detail)
        } else {
            // Direct placement failed; fallback to dialer
            val reason = "Direct call placement unavailable; opened phone dialer"
            launcher.openDialer(cleanPhone)
            updateLedgerStatus(requestId, RequestLedgerStatus.NeedsPhone, detail = reason)
            sendBleReply(requestId, PhoneReplyStatus.NEEDS_PHONE, reason)
            CallExecutionResult.NeedsPhone(reason)
        }
    }

    private suspend fun updateLedgerStatus(
        requestId: Long,
        status: RequestLedgerStatus,
        detail: String? = null,
        failureReason: String? = null
    ) {
        val repo = ledgerRepository ?: return
        val entries = repo.getByWatchRequestId(requestId)
        val entry = entries.firstOrNull() ?: return
        repo.transitionStatus(
            entryId = entry.id,
            newStatus = status,
            detail = detail,
            failureReason = failureReason
        )
    }

    private suspend fun sendBleReply(
        requestId: Long,
        status: PhoneReplyStatus,
        detail: String
    ) {
        bleSessionManager?.sendReply(
            PhoneReply(
                id = requestId,
                status = status,
                detail = detail
            )
        )
    }
}
