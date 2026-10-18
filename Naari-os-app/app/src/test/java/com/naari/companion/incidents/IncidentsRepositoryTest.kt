package com.naari.companion.incidents

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.data.IncidentLog
import com.naari.companion.data.IncidentLogDao
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.WatchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentsRepositoryTest {

    private class FakeIncidentLogDao : IncidentLogDao {
        val insertedLogs = mutableListOf<IncidentLog>()
        val logsFlow = MutableStateFlow<List<IncidentLog>>(emptyList())
        private var idCounter = 1L

        override suspend fun insert(incident: IncidentLog): Long {
            val id = idCounter++
            val saved = incident.copy(id = id)
            insertedLogs.add(saved)
            logsFlow.value = insertedLogs.toList()
            return id
        }

        override fun observeAll(): Flow<List<IncidentLog>> = logsFlow

        override suspend fun getById(id: Long): IncidentLog? = insertedLogs.find { it.id == id }

        override suspend fun getByWatchIncidentId(watchIncidentId: Long): IncidentLog? =
            insertedLogs.findLast { it.watchIncidentId == watchIncidentId }

        override suspend fun updateOutcome(
            id: Long,
            endedAt: Long,
            outcome: String,
            note: String?
        ): Int {
            val idx = insertedLogs.indexOfFirst { it.id == id }
            if (idx >= 0) {
                val updated = insertedLogs[idx].copy(endedAt = endedAt, outcome = outcome, note = note)
                insertedLogs[idx] = updated
                logsFlow.value = insertedLogs.toList()
                return 1
            }
            return 0
        }
    }

    private class FakeBleSessionManager : BleSessionManager {
        val connectionStateFlow = MutableStateFlow(BleConnectionState.Ready)
        override val connectionState: StateFlow<BleConnectionState> = connectionStateFlow.asStateFlow()

        val incomingRequestsFlow = MutableSharedFlow<WatchRequest>(replay = 1)
        override val incomingRequests: SharedFlow<WatchRequest> = incomingRequestsFlow

        val negotiatedMtuFlow = MutableStateFlow(512)
        override val negotiatedMtu: StateFlow<Int> = negotiatedMtuFlow.asStateFlow()

        var lastSentCommandJson: String? = null

        override fun connect(deviceAddress: String) {}
        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)
        override suspend fun sendControlCommand(commandJson: String): Result<Unit> {
            lastSentCommandJson = commandJson
            return Result.success(Unit)
        }
        override fun disconnect() {}
    }

    @Test
    fun duplicateWatchIncidentEvent_neverSpawnsSecondCountdownOrAlert() = runTest {
        val fakeDao = FakeIncidentLogDao()
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultIncidentsRepository(fakeDao, fakeBle)

        val incidentId = 501L
        val now = System.currentTimeMillis()
        val deadline = now + 30_000L

        // 1. Initial event from watch
        val firstIsNew = repo.onWatchIncidentEvent(
            incidentId = incidentId,
            trigger = IncidentTrigger.FallDetected,
            state = IncidentLifecycleState.Countdown,
            watchDeadlineMs = deadline
        )
        assertTrue("First event must be recognized as new", firstIsNew)
        assertEquals(1, fakeDao.insertedLogs.size)
        val active = repo.activeIncident.value
        assertNotNull(active)
        assertEquals(incidentId, active!!.incidentId)
        assertEquals(IncidentLifecycleState.Countdown, active.state)

        // 2. Duplicate re-broadcast event from watch (same incident ID)
        val secondIsNew = repo.onWatchIncidentEvent(
            incidentId = incidentId,
            trigger = IncidentTrigger.FallDetected,
            state = IncidentLifecycleState.Countdown,
            watchDeadlineMs = deadline
        )
        assertFalse("Duplicate event MUST NOT be treated as a new incident", secondIsNew)

        // GATE CHECK: Exactly ONE database record must exist (no duplicate countdown or alert)
        assertEquals("Must NOT create duplicate audit log entries", 1, fakeDao.insertedLogs.size)
        assertEquals("Active incident ID must remain unchanged", incidentId, repo.activeIncident.value?.incidentId)
    }

    @Test
    fun watchIsAuthorityForCountdown_derivedRemainingSecondsMatchesWatchDeadline() = runTest {
        val fakeDao = FakeIncidentLogDao()
        val repo = DefaultIncidentsRepository(fakeDao)

        val fixedNow = 1_000_000L
        val watchDeadline = fixedNow + 25_000L // Watch says 25 seconds remaining

        repo.onWatchIncidentEvent(
            incidentId = 701L,
            trigger = IncidentTrigger.ManualSos,
            state = IncidentLifecycleState.Countdown,
            watchDeadlineMs = watchDeadline
        )

        val active = repo.activeIncident.value
        assertNotNull(active)
        // Phone must derive remaining seconds strictly against watch deadline
        assertEquals(25, active!!.remainingSeconds(fixedNow))
        assertEquals(15, active.remainingSeconds(fixedNow + 10_000L))
        assertEquals(0, active.remainingSeconds(watchDeadline + 5_000L)) // Expired
    }

    @Test
    fun safeDemoMode_isVisiblyMarkedAndDoesNotDispatchRealActions() = runTest {
        val fakeDao = FakeIncidentLogDao()
        val repo = DefaultIncidentsRepository(fakeDao)

        repo.startSafeDemo(countdownSeconds = 30)

        val active = repo.activeIncident.value
        assertNotNull(active)
        assertTrue("Demo incident must have isDemo flag set to true", active!!.isDemo)
        assertEquals(IncidentTrigger.SafeDemo, active.trigger)
        assertEquals(IncidentLifecycleState.Countdown, active.state)

        // Verifies audit log entry
        assertEquals(1, fakeDao.insertedLogs.size)
        assertEquals("SAFE_DEMO_ACTIVE", fakeDao.insertedLogs[0].outcome)
    }

    @Test
    fun remoteSosCancel_requiresDeliberatePin_logsOutcomeAndUpdatesState() = runTest {
        val fakeDao = FakeIncidentLogDao()
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultIncidentsRepository(fakeDao, fakeBle)

        // Initiate incident
        repo.onWatchIncidentEvent(
            incidentId = 888L,
            trigger = IncidentTrigger.FallDetected,
            state = IncidentLifecycleState.Countdown,
            watchDeadlineMs = System.currentTimeMillis() + 30_000L
        )

        // Reject invalid short PIN
        val shortPinResult = repo.cancelActiveSos("12", "Too short")
        assertTrue(shortPinResult.isFailure)
        assertEquals(IncidentLifecycleState.Countdown, repo.activeIncident.value?.state)

        // Deliberate PIN cancellation
        val successResult = repo.cancelActiveSos("1234", "User confirmed safe")
        assertTrue(successResult.isSuccess)
        assertEquals(IncidentLifecycleState.Cancelled, repo.activeIncident.value?.state)

        // Verify BLE cancel command sent
        assertNotNull(fakeBle.lastSentCommandJson)
        assertTrue(fakeBle.lastSentCommandJson!!.contains("\"cancel_sos\""))

        // Verify Room audit log updated
        val updatedLog = fakeDao.insertedLogs[0]
        assertEquals("CANCELLED", updatedLog.outcome)
        assertTrue(updatedLog.note!!.contains("User confirmed safe"))
    }
}
