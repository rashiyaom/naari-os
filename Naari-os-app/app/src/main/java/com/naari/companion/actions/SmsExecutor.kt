package com.naari.companion.actions

import android.content.Context
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.PhoneReplyStatus
import com.naari.companion.protocol.ProtocolV1Codec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Interface contract for executing cellular SMS dispatching.
 * Ref: PRD Section 7 & Master Prompt Task 9.
 */
interface SmsExecutor {
    suspend fun sendSms(
        requestId: Long,
        phone: String,
        message: String,
        lat: Double? = null,
        lon: Double? = null
    ): SmsExecutionResult
}

/**
 * Authoritative implementation of [SmsExecutor].
 *
 * Enforces:
 * - Dual-SIM default subscription routing (via [SmsTransport])
 * - Multipart text message dividing
 * - Dynamically registered BroadcastReceiver for Sent and Delivered callbacks
 * - Protocol sequence: reply 'received' first, then 'sent' upon carrier dispatch, then 'delivered' if confirmed
 * - Immutable ledger progression: Received -> Executing -> Sent -> Delivered / Failed
 * - 45s delivery receipt timeout retention of 'Sent' status (best-effort carrier receipts)
 * - SAFETY CONSTRAINT: NEVER auto-resend uncertain SMS
 */
class DefaultSmsExecutor(
    private val transport: SmsTransport,
    private val ledgerRepository: RequestLedgerRepository? = null,
    private val bleSessionManager: BleSessionManager? = null,
    private val deliveryTimeoutMs: Long = DEFAULT_DELIVERY_TIMEOUT_MS,
    private val sendTimeoutMs: Long = DEFAULT_SEND_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SmsExecutor {

    constructor(
        context: Context,
        ledgerRepository: RequestLedgerRepository? = null,
        bleSessionManager: BleSessionManager? = null,
        deliveryTimeoutMs: Long = DEFAULT_DELIVERY_TIMEOUT_MS,
        sendTimeoutMs: Long = DEFAULT_SEND_TIMEOUT_MS,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        transport = AndroidSmsTransport(context),
        ledgerRepository = ledgerRepository,
        bleSessionManager = bleSessionManager,
        deliveryTimeoutMs = deliveryTimeoutMs,
        sendTimeoutMs = sendTimeoutMs,
        ioDispatcher = ioDispatcher
    )

    override suspend fun sendSms(
        requestId: Long,
        phone: String,
        message: String,
        lat: Double?,
        lon: Double?
    ): SmsExecutionResult = withContext(ioDispatcher) {
        val cleanPhone = phone.trim()

        // 1. Validate phone number format
        if (!ProtocolV1Codec.validatePhoneNumber(cleanPhone) || cleanPhone.isEmpty()) {
            val errorReason = "Invalid phone number format: '$cleanPhone'"
            updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = errorReason)
            sendBleReply(requestId, PhoneReplyStatus.FAILED, errorReason)
            return@withContext SmsExecutionResult.Failed(errorReason)
        }

        // 2. Reply RECEIVED first over BLE and mark ledger entry as Executing
        sendBleReply(requestId, PhoneReplyStatus.RECEIVED, "SMS request accepted by phone")
        updateLedgerStatus(requestId, RequestLedgerStatus.Executing, detail = "Dispatching cellular SMS")

        // 3. Divide message into standard GSM / Unicode parts
        val parts = transport.divideMessage(message)
        val totalParts = parts.size.coerceAtLeast(1)

        val sentDeferred = CompletableDeferred<Result<Unit>>()
        val deliveredDeferred = CompletableDeferred<Result<Unit>>()

        val sentPartsCount = AtomicInteger(0)
        val deliveredPartsCount = AtomicInteger(0)
        val failedErrorCode = AtomicInteger(0)

        // 4. Dispatch via transport with per-part callbacks
        val registration = try {
            transport.sendSmsWithCallbacks(
                requestId = requestId,
                destinationAddress = cleanPhone,
                parts = parts,
                onSentPart = { _, resultCode ->
                    val failureReason = SmsResultMapper.mapSentResultCode(resultCode)
                    if (failureReason == null) {
                        if (sentPartsCount.incrementAndGet() == totalParts) {
                            sentDeferred.complete(Result.success(Unit))
                        }
                    } else {
                        failedErrorCode.set(resultCode)
                        sentDeferred.complete(Result.failure(IOException(failureReason)))
                    }
                },
                onDeliveredPart = { _, resultCode ->
                    if (resultCode == SmsResultMapper.RESULT_OK) {
                        if (deliveredPartsCount.incrementAndGet() == totalParts) {
                            deliveredDeferred.complete(Result.success(Unit))
                        }
                    } else {
                        deliveredDeferred.complete(
                            Result.failure(IOException("Carrier delivery receipt indicated failure code $resultCode"))
                        )
                    }
                }
            )
        } catch (e: Exception) {
            val errorMsg = "Telephony dispatch exception: ${e.message}"
            updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = errorMsg)
            sendBleReply(requestId, PhoneReplyStatus.FAILED, errorMsg)
            return@withContext SmsExecutionResult.Failed(errorMsg)
        }

        try {
            // 5. Await Sent confirmation
            val sentResult = withTimeoutOrNull(sendTimeoutMs) {
                sentDeferred.await()
            }

            if (sentResult == null || sentResult.isFailure) {
                val failureReason = sentResult?.exceptionOrNull()?.message
                    ?: "Telephony send timed out after ${sendTimeoutMs}ms without carrier response"
                val errCode = failedErrorCode.get().takeIf { it != 0 }
                updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = failureReason)
                sendBleReply(requestId, PhoneReplyStatus.FAILED, failureReason)
                return@withContext SmsExecutionResult.Failed(failureReason, errCode)
            }

            // Successfully sent to carrier
            updateLedgerStatus(requestId, RequestLedgerStatus.Sent, detail = "SMS transmitted to carrier network")
            sendBleReply(requestId, PhoneReplyStatus.SENT, "SMS sent to carrier", lat = lat, lon = lon)

            // 6. Await Delivered confirmation with delivery timeout (default 45s)
            val deliveryResult = withTimeoutOrNull(deliveryTimeoutMs) {
                deliveredDeferred.await()
            }

            if (deliveryResult != null && deliveryResult.isSuccess) {
                // Confirmed delivered
                updateLedgerStatus(requestId, RequestLedgerStatus.Delivered, detail = "Carrier confirmed delivery to destination")
                sendBleReply(requestId, PhoneReplyStatus.DELIVERED, "SMS delivered")
                return@withContext SmsExecutionResult.Success(
                    status = RequestLedgerStatus.Delivered,
                    detail = "SMS delivered to recipient"
                )
            } else {
                // Best-effort carrier delivery timeout: RETAIN Sent status. Never mark failed or auto-resend.
                val timeoutDetail = "SMS sent to carrier; delivery receipt timed out after ${deliveryTimeoutMs}ms"
                return@withContext SmsExecutionResult.Success(
                    status = RequestLedgerStatus.Sent,
                    detail = timeoutDetail
                )
            }
        } finally {
            try {
                registration.close()
            } catch (_: Exception) {
            }
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
        detail: String,
        lat: Double? = null,
        lon: Double? = null
    ) {
        bleSessionManager?.sendReply(
            PhoneReply(
                id = requestId,
                status = status,
                detail = detail,
                lat = lat,
                lon = lon
            )
        )
    }

    companion object {
        const val DEFAULT_DELIVERY_TIMEOUT_MS = 45_000L
        const val DEFAULT_SEND_TIMEOUT_MS = 15_000L
    }
}
