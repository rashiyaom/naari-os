package com.naari.companion.checkin

import com.naari.companion.actions.SmsExecutionResult
import com.naari.companion.actions.SmsExecutor
import com.naari.companion.data.Guardian
import com.naari.companion.guardians.GuardianSyncState
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.ledger.LedgerSubmissionResult
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.location.LocationFix
import com.naari.companion.location.LocationProvider
import com.naari.companion.location.LocationResult
import com.naari.companion.protocol.WatchRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeCheckInManagerTest {

    private class FakeLocationProvider : LocationProvider {
        var resultToReturn: LocationResult = LocationResult.Fresh(
            LocationFix(
                latitude = 28.6139,
                longitude = 77.2090,
                accuracyMeters = 15f
            )
        )

        override suspend fun acquireLocation(timeoutMs: Long): LocationResult = resultToReturn

        override fun composeLocationAlert(baseMessage: String?, locationResult: LocationResult): String {
            return "${baseMessage ?: ""} [Location Link]"
        }
    }

    private class FakeSmsExecutor : SmsExecutor {
        val sentMessages = mutableListOf<String>()
        val sentRecipients = mutableListOf<String>()

        override suspend fun sendSms(
            requestId: Long,
            phone: String,
            message: String,
            lat: Double?,
            lon: Double?
        ): SmsExecutionResult {
            sentRecipients.add(phone)
            sentMessages.add(message)
            return SmsExecutionResult.Success(status = RequestLedgerStatus.Sent, detail = "Sent")
        }
    }

    private class FakeGuardiansRepository : GuardiansRepository {
        val slot0Flow = MutableStateFlow<Guardian?>(null)
        val slot1Flow = MutableStateFlow<Guardian?>(null)

        override val slot0: StateFlow<Guardian?> = slot0Flow.asStateFlow()
        override val slot1: StateFlow<Guardian?> = slot1Flow.asStateFlow()
        override val syncState: StateFlow<GuardianSyncState> = MutableStateFlow(GuardianSyncState.SyncedToWatch)
        override val isPendingSync: StateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun saveGuardian(slotIndex: Int, name: String, phone: String) = Result.success(Unit)
        override suspend fun clearGuardian(slotIndex: Int) = Result.success(Unit)
        override suspend fun swapGuardians() = Result.success(Unit)
        override suspend fun syncProfileToWatch() = Result.success(Unit)
    }

    private class FakeLedgerRepository : RequestLedgerRepository {
        val submitted = mutableListOf<WatchRequest>()

        override val recentEntries: Flow<List<RequestLedgerEntry>> = flowOf(emptyList())

        override suspend fun submitRequest(
            request: WatchRequest,
            deviceAddress: String,
            timestamp: Long
        ): LedgerSubmissionResult {
            submitted.add(request)
            return LedgerSubmissionResult.Accepted(
                RequestLedgerEntry(1L, request.id, deviceAddress, request.type)
            )
        }

        override suspend fun transitionStatus(
            entryId: Long,
            newStatus: RequestLedgerStatus,
            detail: String?,
            failureReason: String?,
            timestamp: Long
        ) = Result.success(RequestLedgerEntry(1L, 1L, "dev", com.naari.companion.protocol.WatchRequestType.LOCATION_SMS))

        override suspend fun getById(entryId: Long): RequestLedgerEntry? = null
        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> = emptyList()
        override suspend fun recoverInflightOnStartup(timestamp: Long): Int = 0
    }

    @Test
    fun sendSafeCheckIn_withTwoGuardians_dispatchesBothWithMapsLink() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val location = FakeLocationProvider()
        val sms = FakeSmsExecutor()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeLedgerRepository()

        guardians.slot0Flow.value = Guardian(slotIndex = 0, name = "Mom", phone = "+919876543210")
        guardians.slot1Flow.value = Guardian(slotIndex = 1, name = "Dad", phone = "+919876543211")

        val manager = SafeCheckInManager(
            locationProvider = location,
            smsExecutor = sms,
            guardiansRepository = guardians,
            ledgerRepository = ledger,
            ioDispatcher = testDispatcher
        )

        val result = manager.sendSafeCheckIn()

        assertTrue(result.isSuccess)
        val checkIn = result.getOrThrow()
        assertEquals(2, checkIn.guardiansMessagedCount)
        assertNotNull(checkIn.mapsUrl)
        assertTrue(checkIn.mapsUrl!!.contains("28.6139,77.209"))

        assertEquals(2, sms.sentRecipients.size)
        assertTrue(sms.sentRecipients.contains("+919876543210"))
        assertTrue(sms.sentRecipients.contains("+919876543211"))

        // Assert message content contains reassuring tone
        assertTrue(sms.sentMessages[0].contains("Hi Mom, I have reached safely"))
        assertTrue(sms.sentMessages[0].contains("https://maps.google.com/?q=28.6139,77.209"))

        // Assert logged to request ledger
        assertEquals(2, ledger.submitted.size)
    }

    @Test
    fun sendSafeCheckIn_noGuardians_failsGracefully() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val location = FakeLocationProvider()
        val sms = FakeSmsExecutor()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeLedgerRepository()

        // Zero guardians configured
        guardians.slot0Flow.value = null
        guardians.slot1Flow.value = null

        val manager = SafeCheckInManager(
            locationProvider = location,
            smsExecutor = sms,
            guardiansRepository = guardians,
            ledgerRepository = ledger,
            ioDispatcher = testDispatcher
        )

        val result = manager.sendSafeCheckIn()

        assertTrue(result.isFailure)
        assertEquals(0, sms.sentRecipients.size)
    }
}
