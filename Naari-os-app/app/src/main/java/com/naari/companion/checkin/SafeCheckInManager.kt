package com.naari.companion.checkin

import com.naari.companion.actions.SmsExecutionResult
import com.naari.companion.actions.SmsExecutor
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.location.LocationProvider
import com.naari.companion.location.LocationResult
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result data class for safe check-in dispatches.
 */
data class SafeCheckInResult(
    val guardiansMessagedCount: Int,
    val summary: String,
    val mapsUrl: String?
)

/**
 * Manager handling One-Tap "Reached Safely" Quick Check-In dispatches.
 */
@Singleton
class SafeCheckInManager(
    private val locationProvider: LocationProvider,
    private val smsExecutor: SmsExecutor,
    private val guardiansRepository: GuardiansRepository,
    private val ledgerRepository: RequestLedgerRepository,
    private val ioDispatcher: CoroutineDispatcher
) {
    @Inject
    constructor(
        locationProvider: LocationProvider,
        smsExecutor: SmsExecutor,
        guardiansRepository: GuardiansRepository,
        ledgerRepository: RequestLedgerRepository
    ) : this(
        locationProvider = locationProvider,
        smsExecutor = smsExecutor,
        guardiansRepository = guardiansRepository,
        ledgerRepository = ledgerRepository,
        ioDispatcher = Dispatchers.IO
    )

    /**
     * Executes a one-tap safe check-in, acquiring location and dispatching
     * reassuring safety texts to all configured guardians.
     */
    suspend fun sendSafeCheckIn(): Result<SafeCheckInResult> = withContext(ioDispatcher) {
        val slot0 = guardiansRepository.slot0.first()
        val slot1 = guardiansRepository.slot1.first()

        val guardians = listOfNotNull(slot0, slot1).filter { it.phone.isNotBlank() }
        if (guardians.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No guardians configured. Add a guardian first."))
        }

        // 1. Acquire current location
        val locationResult = locationProvider.acquireLocation()

        val (lat, lon) = when (locationResult) {
            is LocationResult.Fresh -> Pair(locationResult.fix.latitude, locationResult.fix.longitude)
            is LocationResult.Cached -> Pair(locationResult.fix.latitude, locationResult.fix.longitude)
            else -> Pair(null, null)
        }

        val mapsUrl = if (lat != null && lon != null) "https://maps.google.com/?q=$lat,$lon" else null

        // 2. Dispatch to each guardian
        var successCount = 0
        for (guardian in guardians) {
            val baseMessage = if (mapsUrl != null) {
                "Hi ${guardian.name}, I have reached safely at $mapsUrl - Sent via Naari OS"
            } else {
                "Hi ${guardian.name}, I have reached safely. - Sent via Naari OS"
            }

            val requestId = System.currentTimeMillis()

            ledgerRepository.submitRequest(
                request = WatchRequest(
                    id = requestId,
                    type = WatchRequestType.LOCATION_SMS,
                    phone = guardian.phone,
                    message = baseMessage
                ),
                deviceAddress = "PHONE_CHECKIN"
            )

            val result = smsExecutor.sendSms(
                requestId = requestId,
                phone = guardian.phone,
                message = baseMessage,
                lat = lat,
                lon = lon
            )

            if (result is SmsExecutionResult.Success) {
                successCount++
            }
        }

        if (successCount > 0) {
            val summary = "Safe check-in sent to $successCount guardian(s)"
            Result.success(SafeCheckInResult(successCount, summary, mapsUrl))
        } else {
            Result.failure(IllegalStateException("Failed to deliver check-in SMS to carrier"))
        }
    }
}
