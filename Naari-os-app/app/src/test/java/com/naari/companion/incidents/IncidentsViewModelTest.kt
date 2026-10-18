package com.naari.companion.incidents

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.ReadinessState
import com.naari.companion.data.IncidentLog
import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.permissions.PermissionsSnapshot
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.WatchRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentsViewModelTest {

    private class FakeIncidentsRepository : IncidentsRepository {
        val activeIncidentFlow = MutableStateFlow<ActiveIncident?>(null)
        override val activeIncident: StateFlow<ActiveIncident?> = activeIncidentFlow.asStateFlow()

        val logsFlow = MutableStateFlow<List<IncidentLog>>(emptyList())
        override val incidentLogs: Flow<List<IncidentLog>> = logsFlow

        var lastCancelledPin: String? = null

        override suspend fun onWatchIncidentEvent(
            incidentId: Long,
            trigger: IncidentTrigger,
            state: IncidentLifecycleState,
            watchDeadlineMs: Long?,
            isDemo: Boolean
        ): Boolean {
            activeIncidentFlow.value = ActiveIncident(
                incidentId = incidentId,
                trigger = trigger,
                state = state,
                watchDeadlineTimestampMs = watchDeadlineMs,
                isDemo = isDemo
            )
            return true
        }

        override suspend fun addActionToActiveIncident(action: IncidentActionItem) {}

        override suspend fun startSafeDemo(countdownSeconds: Int) {
            activeIncidentFlow.value = ActiveIncident(
                incidentId = 9999L,
                trigger = IncidentTrigger.SafeDemo,
                state = IncidentLifecycleState.Countdown,
                watchDeadlineTimestampMs = System.currentTimeMillis() + (countdownSeconds * 1000L),
                isDemo = true
            )
        }

        override suspend fun cancelActiveSos(disarmPin: String, reason: String): Result<Unit> {
            lastCancelledPin = disarmPin
            val current = activeIncidentFlow.value
            if (current != null) {
                activeIncidentFlow.value = current.copy(state = IncidentLifecycleState.Cancelled)
            }
            return Result.success(Unit)
        }

        override suspend fun resolveActiveIncident(outcome: String, note: String?) {
            activeIncidentFlow.value = null
        }
    }

    private class FakePermissionsRepository : PermissionsRepository {
        val permissionsFlow = MutableStateFlow(
            PermissionsSnapshot(
                bluetoothGranted = true,
                bluetoothAdapterEnabled = true,
                smsGranted = true,
                callGranted = true,
                locationState = LocationPermissionState.Precise,
                locationServiceEnabled = true,
                notificationsGranted = true,
                contactsGranted = true
            )
        )
        override val permissions: StateFlow<PermissionsSnapshot> = permissionsFlow.asStateFlow()
        override fun refreshPermissions() {}
        override fun getRequiredBluetoothPermissions(): List<String> = emptyList()
        override fun getRequiredSafetyPermissions(): List<String> = emptyList()
    }

    private class FakeBleSessionManager : BleSessionManager {
        val connectionStateFlow = MutableStateFlow(BleConnectionState.Ready)
        override val connectionState: StateFlow<BleConnectionState> = connectionStateFlow.asStateFlow()

        val incomingRequestsFlow = MutableSharedFlow<WatchRequest>(replay = 1)
        override val incomingRequests: SharedFlow<WatchRequest> = incomingRequestsFlow

        val negotiatedMtuFlow = MutableStateFlow(512)
        override val negotiatedMtu: StateFlow<Int> = negotiatedMtuFlow.asStateFlow()

        val lastHelloFlow = MutableStateFlow<Long?>(System.currentTimeMillis())
        override val lastHelloTimestampMs: StateFlow<Long?> = lastHelloFlow.asStateFlow()

        val connStepFlow = MutableStateFlow(BleConnectionStep.ServiceDiscovered)
        override val connectionStep: StateFlow<BleConnectionStep> = connStepFlow.asStateFlow()

        val hasGattFlow = MutableStateFlow(true)
        override val hasRequiredGattService: StateFlow<Boolean> = hasGattFlow.asStateFlow()

        override fun connect(deviceAddress: String) {}
        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
    }

    @Test
    fun uiState_initialState_displaysReadyAndNoActiveIncident() = runTest {
        val incidentsRepo = FakeIncidentsRepository()
        val permissionsRepo = FakePermissionsRepository()
        val ble = FakeBleSessionManager()

        val viewModel = IncidentsViewModel(
            incidentsRepository = incidentsRepo,
            permissionsRepository = permissionsRepo,
            bleSessionManager = ble,
            externalScope = backgroundScope
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ReadinessState.Ready, state.readinessState)
        assertTrue(state.isConnected)
        assertNull(state.activeIncident)
        assertEquals(0, state.remainingCountdownSeconds)
        assertNull(state.cancelError)
    }

    @Test
    fun uiState_whenWatchEventArrives_relaysAuthorityCountdown() = runTest {
        val incidentsRepo = FakeIncidentsRepository()
        val permissionsRepo = FakePermissionsRepository()
        val ble = FakeBleSessionManager()

        val viewModel = IncidentsViewModel(
            incidentsRepository = incidentsRepo,
            permissionsRepository = permissionsRepo,
            bleSessionManager = ble,
            externalScope = backgroundScope
        )
        runCurrent()

        // Incident arrives with 20-second authority countdown from watch
        val now = System.currentTimeMillis()
        incidentsRepo.onWatchIncidentEvent(
            incidentId = 444L,
            trigger = IncidentTrigger.FallDetected,
            state = IncidentLifecycleState.Countdown,
            watchDeadlineMs = now + 20_000L
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.activeIncident)
        assertEquals(444L, state.activeIncident!!.incidentId)
        assertEquals(IncidentTrigger.FallDetected, state.activeIncident!!.trigger)
        assertFalse(state.activeIncident!!.isDemo)
        assertTrue(state.remainingCountdownSeconds in 19..21)
    }

    @Test
    fun uiState_safeDemoMode_isMarkedDemoWithoutExternalDispatch() = runTest {
        val incidentsRepo = FakeIncidentsRepository()
        val permissionsRepo = FakePermissionsRepository()
        val ble = FakeBleSessionManager()

        val viewModel = IncidentsViewModel(
            incidentsRepository = incidentsRepo,
            permissionsRepository = permissionsRepo,
            bleSessionManager = ble,
            externalScope = backgroundScope
        )
        runCurrent()

        viewModel.startSafeDemo()
        runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.activeIncident)
        assertTrue("Active incident must be marked demo", state.activeIncident!!.isDemo)
        assertEquals(IncidentTrigger.SafeDemo, state.activeIncident!!.trigger)
    }

    @Test
    fun cancelActiveSos_withPin_triggersDeliberateCancellation() = runTest {
        val incidentsRepo = FakeIncidentsRepository()
        val permissionsRepo = FakePermissionsRepository()
        val ble = FakeBleSessionManager()

        val viewModel = IncidentsViewModel(
            incidentsRepository = incidentsRepo,
            permissionsRepository = permissionsRepo,
            bleSessionManager = ble,
            externalScope = backgroundScope
        )
        runCurrent()

        incidentsRepo.startSafeDemo()
        runCurrent()

        viewModel.cancelActiveSos("9999", "User safe disarm")
        runCurrent()

        assertEquals("9999", incidentsRepo.lastCancelledPin)
        assertEquals(IncidentLifecycleState.Cancelled, viewModel.uiState.value.activeIncident?.state)
        assertNull(viewModel.uiState.value.cancelError)
    }
}
