package com.naari.companion.safewalk

import android.content.Context
import android.content.ContextWrapper
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeWalkManagerTest {

    private class DummyContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): Context = this
    }

    private class FakeLocationProvider : LocationProvider {
        override suspend fun acquireLocation(timeoutMs: Long): LocationResult = LocationResult.Fresh(
            LocationFix(
                latitude = 28.7041,
                longitude = 77.1025,
                accuracyMeters = 20f
            )
        )

        override fun composeLocationAlert(baseMessage: String?, locationResult: LocationResult): String {
            return "${baseMessage ?: ""} https://maps.google.com/?q=28.7041,77.1025"
        }
    }

    private class FakeSmsExecutor : SmsExecutor {
        val sentMessages = mutableListOf<String>()
        val sentPhones = mutableListOf<String>()

        override suspend fun sendSms(
            requestId: Long,
            phone: String,
            message: String,
            lat: Double?,
            lon: Double?
        ): SmsExecutionResult {
            sentPhones.add(phone)
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
    fun startJourney_transitionsStateToActiveWithCorrectDuration() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val context = DummyContext()
        val location = FakeLocationProvider()
        val sms = FakeSmsExecutor()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeLedgerRepository()

        val manager = SafeWalkManager(
            context = context,
            locationProvider = location,
            smsExecutor = sms,
            guardiansRepository = guardians,
            ledgerRepository = ledger,
            ioDispatcher = testDispatcher
        )

        assertEquals(SafeWalkState.Idle, manager.state.value)

        manager.startJourney(20)

        val active = manager.state.value as SafeWalkState.Active
        assertEquals(20, active.totalMinutes)
        assertEquals(1200L, active.remainingSeconds)

        manager.cancel()
    }

    @Test
    fun extendJourney_addsExtraSecondsToActiveJourney() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val context = DummyContext()
        val location = FakeLocationProvider()
        val sms = FakeSmsExecutor()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeLedgerRepository()

        val manager = SafeWalkManager(
            context = context,
            locationProvider = location,
            smsExecutor = sms,
            guardiansRepository = guardians,
            ledgerRepository = ledger,
            ioDispatcher = testDispatcher
        )

        manager.startJourney(10)
        manager.extend(15)

        val active = manager.state.value as SafeWalkState.Active
        assertEquals(25, active.totalMinutes)
        assertEquals(1500L, active.remainingSeconds)

        manager.cancel()
    }

    @Test
    fun arriveSafely_cancelsTimerAndMarksCompleted() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val context = DummyContext()
        val location = FakeLocationProvider()
        val sms = FakeSmsExecutor()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeLedgerRepository()

        val manager = SafeWalkManager(
            context = context,
            locationProvider = location,
            smsExecutor = sms,
            guardiansRepository = guardians,
            ledgerRepository = ledger,
            ioDispatcher = testDispatcher
        )

        manager.startJourney(30)
        manager.arriveSafely()

        assertEquals(SafeWalkState.Completed, manager.state.value)
    }

    @Test
    fun cancel_returnsToIdleState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val context = DummyContext()
        val location = FakeLocationProvider()
        val sms = FakeSmsExecutor()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeLedgerRepository()

        val manager = SafeWalkManager(
            context = context,
            locationProvider = location,
            smsExecutor = sms,
            guardiansRepository = guardians,
            ledgerRepository = ledger,
            ioDispatcher = testDispatcher
        )

        manager.startJourney(15)
        manager.cancel()

        assertEquals(SafeWalkState.Idle, manager.state.value)
    }
}
