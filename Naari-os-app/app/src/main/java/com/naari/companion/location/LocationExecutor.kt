package com.naari.companion.location

import android.content.Context
import com.naari.companion.actions.DefaultSmsExecutor
import com.naari.companion.actions.SmsExecutionResult
import com.naari.companion.actions.SmsExecutor
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.PhoneReplyStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interface contract for executing watch-initiated location and location_sms actions.
 * Ref: PRD Section 7 & Master Prompt Task 11.
 */
interface LocationExecutor {
    suspend fun executeLocation(
        requestId: Long
    ): LocationResult

    suspend fun executeLocationSms(
        requestId: Long,
        phoneNumber: String,
        baseMessage: String?
    ): SmsExecutionResult
}

/**
 * Authoritative implementation of [LocationExecutor].
 *
 * Enforces:
 * - Acknowledges durable receipt: sends 'received' reply over BLE first
 * - Updates Room Request Ledger persistently before/after each stage
 * - For 'location': returns 'located' with finite lat/lon if fresh or cached; 'failed' if unavailable
 * - For 'location_sms': acquires location, composes alert with map link, and dispatches SMS
 * - SAFETY RULE: Location failures must not silently prevent safety message: sends explicit location-unavailable text when SMS is permitted
 */
class DefaultLocationExecutor(
    private val locationProvider: LocationProvider,
    private val smsExecutor: SmsExecutor,
    private val ledgerRepository: RequestLedgerRepository? = null,
    private val bleSessionManager: BleSessionManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LocationExecutor {

    constructor(
        context: Context,
        ledgerRepository: RequestLedgerRepository? = null,
        bleSessionManager: BleSessionManager? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        locationProvider = DefaultLocationProvider(context),
        smsExecutor = DefaultSmsExecutor(context, ledgerRepository, bleSessionManager),
        ledgerRepository = ledgerRepository,
        bleSessionManager = bleSessionManager,
        ioDispatcher = ioDispatcher
    )

    override suspend fun executeLocation(requestId: Long): LocationResult = withContext(ioDispatcher) {
        // 1. Reply RECEIVED first over BLE and update ledger to Executing
        sendBleReply(requestId, PhoneReplyStatus.RECEIVED, "Location request accepted by phone")
        updateLedgerStatus(requestId, RequestLedgerStatus.Executing, detail = "Acquiring GPS location")

        // 2. Acquire location fix
        val result = locationProvider.acquireLocation()

        when (result) {
            is LocationResult.Fresh -> {
                val detail = "Fresh GPS fix (±${result.fix.accuracyMeters?.toInt() ?: 0}m)"
                updateLedgerLocation(requestId, RequestLedgerStatus.Located, result.fix.latitude, result.fix.longitude, detail)
                sendBleReply(
                    requestId = requestId,
                    status = PhoneReplyStatus.LOCATED,
                    detail = detail,
                    lat = result.fix.latitude,
                    lon = result.fix.longitude
                )
            }
            is LocationResult.Cached -> {
                val detail = "Cached fix (${result.ageSeconds}s ago)"
                updateLedgerLocation(requestId, RequestLedgerStatus.Located, result.fix.latitude, result.fix.longitude, detail)
                sendBleReply(
                    requestId = requestId,
                    status = PhoneReplyStatus.LOCATED,
                    detail = detail,
                    lat = result.fix.latitude,
                    lon = result.fix.longitude
                )
            }
            is LocationResult.Timeout -> {
                val errorReason = "Location acquisition timed out"
                updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = errorReason)
                sendBleReply(requestId, PhoneReplyStatus.FAILED, errorReason)
            }
            is LocationResult.PermissionDenied -> {
                val errorReason = "Location permission denied"
                updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = errorReason)
                sendBleReply(requestId, PhoneReplyStatus.FAILED, errorReason)
            }
            is LocationResult.Disabled -> {
                val errorReason = "Location services disabled"
                updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = errorReason)
                sendBleReply(requestId, PhoneReplyStatus.FAILED, errorReason)
            }
            is LocationResult.Error -> {
                val errorReason = "Location error: ${result.message}"
                updateLedgerStatus(requestId, RequestLedgerStatus.Failed, failureReason = errorReason)
                sendBleReply(requestId, PhoneReplyStatus.FAILED, errorReason)
            }
        }

        result
    }

    override suspend fun executeLocationSms(
        requestId: Long,
        phoneNumber: String,
        baseMessage: String?
    ): SmsExecutionResult = withContext(ioDispatcher) {
        // 1. Acquire location
        val locationResult = locationProvider.acquireLocation()

        // 2. Compose alert text (with Google Maps link or explicit "location unavailable")
        val alertMessage = locationProvider.composeLocationAlert(baseMessage, locationResult)

        // 3. Extract coordinates if available (Fresh or Cached) so watch firmware caches them
        val (lat, lon) = when (locationResult) {
            is LocationResult.Fresh -> Pair(locationResult.fix.latitude, locationResult.fix.longitude)
            is LocationResult.Cached -> Pair(locationResult.fix.latitude, locationResult.fix.longitude)
            else -> Pair(null, null)
        }

        // 4. Dispatch cellular SMS with coordinates
        smsExecutor.sendSms(requestId, phoneNumber, alertMessage, lat, lon)
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

    private suspend fun updateLedgerLocation(
        requestId: Long,
        status: RequestLedgerStatus,
        lat: Double,
        lon: Double,
        detail: String
    ) {
        val repo = ledgerRepository ?: return
        val entries = repo.getByWatchRequestId(requestId)
        val entry = entries.firstOrNull() ?: return
        repo.transitionStatus(
            entryId = entry.id,
            newStatus = status,
            detail = detail
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
}
