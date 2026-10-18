package com.naari.companion.home

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.ReadinessState
import com.naari.companion.data.Guardian
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.permissions.PermissionsSnapshot
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.WatchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
        val connectionStateFlow = MutableStateFlow(BleConnectionState.Disconnected)
        override val connectionState: StateFlow<BleConnectionState> = connectionStateFlow.asStateFlow()

        val incomingRequestsFlow = MutableSharedFlow<WatchRequest>(replay = 1)
        override val incomingRequests: SharedFlow<WatchRequest> = incomingRequestsFlow

        val negotiatedMtuFlow = MutableStateFlow(512)
        override val negotiatedMtu: StateFlow<Int> = negotiatedMtuFlow.asStateFlow()

        val lastHelloFlow = MutableStateFlow<Long?>(null)
        override val lastHelloTimestampMs: StateFlow<Long?> = lastHelloFlow.asStateFlow()

        val connectionStepFlow = MutableStateFlow(BleConnectionStep.Disconnected)
        override val connectionStep: StateFlow<BleConnectionStep> = connectionStepFlow.asStateFlow()

        val hasRequiredGattServiceFlow = MutableStateFlow(false)
        override val hasRequiredGattService: StateFlow<Boolean> = hasRequiredGattServiceFlow.asStateFlow()

        val batteryPercentFlow = MutableStateFlow<Int?>(null)
        override val batteryPercent: StateFlow<Int?> = batteryPercentFlow.asStateFlow()

        override fun connect(deviceAddress: String) {}
        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
    }

    private class FakeGuardiansRepository : GuardiansRepository {
        val slot0Flow = MutableStateFlow<Guardian?>(null)
        val slot1Flow = MutableStateFlow<Guardian?>(null)
        override val slot0: StateFlow<Guardian?> = slot0Flow.asStateFlow()
        override val slot1: StateFlow<Guardian?> = slot1Flow.asStateFlow()
        override val syncState: StateFlow<com.naari.companion.guardians.GuardianSyncState> =
            MutableStateFlow(com.naari.companion.guardians.GuardianSyncState.Empty)
        override val isPendingSync: StateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun saveGuardian(slotIndex: Int, name: String, phone: String): Result<Unit> {
            val g = Guardian(slotIndex = slotIndex, name = name, phone = phone)
            if (slotIndex == 0) slot0Flow.value = g else slot1Flow.value = g
            return Result.success(Unit)
        }

        override suspend fun clearGuardian(slotIndex: Int): Result<Unit> {
            if (slotIndex == 0) slot0Flow.value = null else slot1Flow.value = null
            return Result.success(Unit)
        }

        override suspend fun syncProfileToWatch(): Result<Unit> = Result.success(Unit)

        override suspend fun swapGuardians(): Result<Unit> {
            val temp = slot0Flow.value
            slot0Flow.value = slot1Flow.value?.copy(slotIndex = 0)
            slot1Flow.value = temp?.copy(slotIndex = 1)
            return Result.success(Unit)
        }
    }

    private class FakeRequestLedgerRepository : RequestLedgerRepository {
        val entriesFlow = MutableStateFlow<List<RequestLedgerEntry>>(emptyList())
        override val recentEntries: Flow<List<RequestLedgerEntry>> = entriesFlow

        override suspend fun submitRequest(
            request: WatchRequest,
            deviceAddress: String,
            timestamp: Long
        ): com.naari.companion.ledger.LedgerSubmissionResult =
            com.naari.companion.ledger.LedgerSubmissionResult.Accepted(
                RequestLedgerEntry(
                    id = 1L,
                    watchRequestId = request.id,
                    deviceAddress = deviceAddress,
                    type = request.type
                )
            )

        override suspend fun transitionStatus(
            entryId: Long,
            newStatus: RequestLedgerStatus,
            detail: String?,
            failureReason: String?,
            timestamp: Long
        ): Result<RequestLedgerEntry> {
            val entry = entriesFlow.value.find { it.id == entryId }
                ?: RequestLedgerEntry(
                    id = entryId,
                    watchRequestId = 1L,
                    deviceAddress = "00:11:22:33:44:55",
                    type = com.naari.companion.protocol.WatchRequestType.SMS,
                    status = newStatus
                )
            return Result.success(entry)
        }

        override suspend fun getById(entryId: Long): RequestLedgerEntry? =
            entriesFlow.value.find { it.id == entryId }

        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> =
            entriesFlow.value.filter { it.watchRequestId == watchRequestId }

        override suspend fun recoverInflightOnStartup(timestamp: Long): Int = 0
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        val completedFlow = MutableStateFlow(true)
        override val isOnboardingCompleted: Flow<Boolean> = completedFlow

        val addressFlow = MutableStateFlow<String?>("AA:BB:CC:DD:EE:FF")
        override val pairedDeviceAddress: Flow<String?> = addressFlow

        val nameFlow = MutableStateFlow<String?>("Naari Watch")
        override val pairedDeviceName: Flow<String?> = nameFlow

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            completedFlow.value = completed
        }

        override suspend fun savePairedDevice(address: String, name: String) {
            addressFlow.value = address
            nameFlow.value = name
        }

        override suspend fun clearPairedDevice() {
            addressFlow.value = null
            nameFlow.value = null
        }

        override suspend fun resetOnboarding() {
            completedFlow.value = false
            addressFlow.value = null
            nameFlow.value = null
        }
    }

    @Test
    fun readinessGate_neverShowsReady_withoutFreshHelloAcknowledgment() = runTest {
        val permissions = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeRequestLedgerRepository()
        val onboarding = FakeOnboardingRepository()

        // Configure connected link and discovered service, but NO hello handshake
        ble.connectionStateFlow.value = BleConnectionState.ConnectedServiceUnavailable
        ble.connectionStepFlow.value = BleConnectionStep.ServiceDiscovered
        ble.hasRequiredGattServiceFlow.value = true
        ble.lastHelloFlow.value = null // No hello handshake received

        val viewModel = HomeViewModel(permissions, ble, guardians, ledger, onboarding, externalScope = backgroundScope)
        runCurrent()

        val state = viewModel.uiState.value
        // Must NOT show Ready
        assertEquals(ReadinessState.ConnectedServiceUnavailable, state.readiness)
    }

    @Test
    fun readinessGate_neverShowsReady_whenHelloIsExpired() = runTest {
        val permissions = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeRequestLedgerRepository()
        val onboarding = FakeOnboardingRepository()

        ble.connectionStateFlow.value = BleConnectionState.ConnectedServiceUnavailable
        ble.connectionStepFlow.value = BleConnectionStep.ServiceDiscovered
        ble.hasRequiredGattServiceFlow.value = true
        // Stale hello from 50 seconds ago (expiration threshold is 45s)
        ble.lastHelloFlow.value = System.currentTimeMillis() - 50_000L

        val viewModel = HomeViewModel(permissions, ble, guardians, ledger, onboarding, externalScope = backgroundScope)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ReadinessState.ConnectedServiceUnavailable, state.readiness)
    }

    @Test
    fun readinessGate_neverShowsReady_whenPermissionsMissing() = runTest {
        val permissions = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeRequestLedgerRepository()
        val onboarding = FakeOnboardingRepository()

        // Fully connected with fresh hello
        ble.connectionStateFlow.value = BleConnectionState.Ready
        ble.connectionStepFlow.value = BleConnectionStep.ServiceDiscovered
        ble.hasRequiredGattServiceFlow.value = true
        ble.lastHelloFlow.value = System.currentTimeMillis() - 5_000L // Fresh (5s ago)

        // But SMS permission is revoked
        permissions.permissionsFlow.value = permissions.permissionsFlow.value.copy(smsGranted = false)

        val viewModel = HomeViewModel(permissions, ble, guardians, ledger, onboarding, externalScope = backgroundScope)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ReadinessState.PermissionLimited, state.readiness)
    }

    @Test
    fun readinessGate_neverShowsReady_whenBluetoothOff() = runTest {
        val permissions = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeRequestLedgerRepository()
        val onboarding = FakeOnboardingRepository()

        permissions.permissionsFlow.value = permissions.permissionsFlow.value.copy(bluetoothAdapterEnabled = false)

        val viewModel = HomeViewModel(permissions, ble, guardians, ledger, onboarding, externalScope = backgroundScope)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ReadinessState.BluetoothOff, state.readiness)
    }

    @Test
    fun readinessGate_showsReady_whenAllPreconditionsMetWithFreshHello() = runTest {
        val permissions = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeRequestLedgerRepository()
        val onboarding = FakeOnboardingRepository()

        ble.connectionStateFlow.value = BleConnectionState.Ready
        ble.connectionStepFlow.value = BleConnectionStep.ServiceDiscovered
        ble.hasRequiredGattServiceFlow.value = true
        ble.lastHelloFlow.value = System.currentTimeMillis() - 10_000L // 10s ago (< 45s)
        ble.batteryPercentFlow.value = 85

        val viewModel = HomeViewModel(permissions, ble, guardians, ledger, onboarding, externalScope = backgroundScope)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ReadinessState.Ready, state.readiness)
        assertEquals(85, state.batteryPercent)
        assertNotNull(state.lastSeenTimestampMs)
    }

    @Test
    fun inlineErrors_surfaceNearRelevantTiles_withoutFloodingDiagnostics() = runTest {
        val permissions = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val guardians = FakeGuardiansRepository()
        val ledger = FakeRequestLedgerRepository()
        val onboarding = FakeOnboardingRepository()

        // Zero guardians configured
        guardians.slot0Flow.value = null
        guardians.slot1Flow.value = null

        val viewModel = HomeViewModel(permissions, ble, guardians, ledger, onboarding, externalScope = backgroundScope)
        runCurrent()

        val state = viewModel.uiState.value
        // Guardians inline error is present
        assertNotNull(state.inlineErrorGuardians)
        assertTrue(state.inlineErrorGuardians!!.contains("guardians"))

        // Revoke call permission
        permissions.permissionsFlow.value = permissions.permissionsFlow.value.copy(callGranted = false)
        runCurrent()

        val state2 = viewModel.uiState.value
        assertNotNull(state2.inlineErrorSafety)
        assertTrue(state2.inlineErrorSafety!!.contains("Call permission"))
    }
}
