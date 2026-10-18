package com.naari.companion.onboarding

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.ReadinessState
import com.naari.companion.data.Guardian
import com.naari.companion.guardians.GuardiansRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private class FakeOnboardingRepository : OnboardingRepository {
        val completedFlow = MutableStateFlow(false)
        override val isOnboardingCompleted: Flow<Boolean> = completedFlow

        val addressFlow = MutableStateFlow<String?>(null)
        override val pairedDeviceAddress: Flow<String?> = addressFlow

        val nameFlow = MutableStateFlow<String?>(null)
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
        var refreshCallCount = 0
        override val permissions: StateFlow<PermissionsSnapshot> = permissionsFlow.asStateFlow()
        override fun refreshPermissions() {
            refreshCallCount++
        }
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

        var lastConnectedAddress: String? = null
        var didDisconnect = false

        override fun connect(deviceAddress: String) {
            lastConnectedAddress = deviceAddress
        }

        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)

        override fun disconnect() {
            didDisconnect = true
        }
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

    @Test
    fun onboardingWizard_startsAtWelcome_andNavigatesSequentially() = runTest {
        val onboardingRepo = FakeOnboardingRepository()
        val permissionsRepo = FakePermissionsRepository()
        val guardiansRepo = FakeGuardiansRepository()
        val ble = FakeBleSessionManager()

        val viewModel = OnboardingViewModel(onboardingRepo, permissionsRepo, guardiansRepo, ble, externalScope = backgroundScope)
        runCurrent()

        // Initial step: Welcome (step 0)
        assertEquals(OnboardingStep.Welcome, viewModel.uiState.value.currentStep)

        // Advance to PairWatch (step 1)
        viewModel.nextStep()
        runCurrent()
        assertEquals(OnboardingStep.PairWatch, viewModel.uiState.value.currentStep)

        // Advance to GrantCapabilities (step 2)
        viewModel.nextStep()
        runCurrent()
        assertEquals(OnboardingStep.GrantCapabilities, viewModel.uiState.value.currentStep)

        // Advance to SetGuardians (step 3)
        viewModel.nextStep()
        runCurrent()
        assertEquals(OnboardingStep.SetGuardians, viewModel.uiState.value.currentStep)

        // Advance to VerifyReadiness (step 4)
        viewModel.nextStep()
        runCurrent()
        assertEquals(OnboardingStep.VerifyReadiness, viewModel.uiState.value.currentStep)

        // Advance to WearWatch (step 5)
        viewModel.nextStep()
        runCurrent()
        assertEquals(OnboardingStep.WearWatch, viewModel.uiState.value.currentStep)

        // Cannot advance past step 5
        viewModel.nextStep()
        runCurrent()
        assertEquals(OnboardingStep.WearWatch, viewModel.uiState.value.currentStep)

        // Navigate back to step 4
        viewModel.previousStep()
        runCurrent()
        assertEquals(OnboardingStep.VerifyReadiness, viewModel.uiState.value.currentStep)
    }

    @Test
    fun pairingAction_persistsDevice_andInitiatesBleSession() = runTest {
        val onboardingRepo = FakeOnboardingRepository()
        val permissionsRepo = FakePermissionsRepository()
        val guardiansRepo = FakeGuardiansRepository()
        val ble = FakeBleSessionManager()

        val viewModel = OnboardingViewModel(onboardingRepo, permissionsRepo, guardiansRepo, ble, externalScope = backgroundScope)
        runCurrent()

        viewModel.pairDevice("11:22:33:44:55:66", "Naari Watch Safe")
        runCurrent()

        assertNull(onboardingRepo.addressFlow.value)
        ble.connectionStateFlow.value = BleConnectionState.Ready
        runCurrent()
        assertEquals("11:22:33:44:55:66", onboardingRepo.addressFlow.value)
        assertEquals("Naari Watch Safe", onboardingRepo.nameFlow.value)
        assertEquals("11:22:33:44:55:66", ble.lastConnectedAddress)

        // Clear device
        viewModel.clearPairedDevice()
        runCurrent()

        assertNull(onboardingRepo.addressFlow.value)
        assertTrue(ble.didDisconnect)
    }

    @Test
    fun completeOnboarding_persistsCompletionFlag_andInvokesCallback() = runTest {
        val onboardingRepo = FakeOnboardingRepository()
        val permissionsRepo = FakePermissionsRepository()
        val guardiansRepo = FakeGuardiansRepository()
        val ble = FakeBleSessionManager()

        val viewModel = OnboardingViewModel(onboardingRepo, permissionsRepo, guardiansRepo, ble, externalScope = backgroundScope)
        runCurrent()

        var finishedCalled = false
        viewModel.completeOnboarding {
            finishedCalled = true
        }
        runCurrent()

        assertTrue(finishedCalled)
        assertTrue(onboardingRepo.completedFlow.value)
    }

    @Test
    fun pairingVisualState_transitionsSequentially_fromSearchToConnected() = runTest {
        val onboardingRepo = FakeOnboardingRepository()
        val permissionsRepo = FakePermissionsRepository()
        val guardiansRepo = FakeGuardiansRepository()
        val ble = FakeBleSessionManager()

        val viewModel = OnboardingViewModel(onboardingRepo, permissionsRepo, guardiansRepo, ble, externalScope = backgroundScope)
        runCurrent()

        // 1. Initial visual state is Idle
        assertEquals(PairingVisualState.Idle, viewModel.uiState.value.pairingVisualState)

        // 2. Start searching
        viewModel.startSearching()
        runCurrent()
        assertEquals(PairingVisualState.Searching, viewModel.uiState.value.pairingVisualState)

        // 3. Discovered device
        viewModel.onDeviceDiscovered("CC:DD:EE:11:22:33", "Naari Watch Pro")
        runCurrent()
        val foundState = viewModel.uiState.value.pairingVisualState
        assertTrue(foundState is PairingVisualState.DeviceFound)
        assertEquals("CC:DD:EE:11:22:33", (foundState as PairingVisualState.DeviceFound).address)
        assertEquals("Naari Watch Pro", foundState.name)

        // 4. Confirm and pair
        viewModel.confirmAndPair("CC:DD:EE:11:22:33", "Naari Watch Pro")
        runCurrent()
        assertTrue(viewModel.uiState.value.pairingVisualState is PairingVisualState.Pairing)
        assertNull(onboardingRepo.addressFlow.value)
        ble.connectionStateFlow.value = BleConnectionState.Ready
        runCurrent()
        val connectedState = viewModel.uiState.value.pairingVisualState
        assertTrue(connectedState is PairingVisualState.Connected)
        assertEquals("CC:DD:EE:11:22:33", (connectedState as PairingVisualState.Connected).address)
        assertEquals("CC:DD:EE:11:22:33", onboardingRepo.addressFlow.value)
        assertEquals("CC:DD:EE:11:22:33", ble.lastConnectedAddress)

        // 5. Unpair resets back to Idle
        viewModel.clearPairedDevice()
        runCurrent()
        assertEquals(PairingVisualState.Idle, viewModel.uiState.value.pairingVisualState)
        assertNull(onboardingRepo.addressFlow.value)
        assertTrue(ble.didDisconnect)
    }

    @Test
    fun pairingError_transitionsToErrorState_andCancellingResetsToIdle() = runTest {
        val onboardingRepo = FakeOnboardingRepository()
        val permissionsRepo = FakePermissionsRepository()
        val guardiansRepo = FakeGuardiansRepository()
        val ble = FakeBleSessionManager()

        val viewModel = OnboardingViewModel(onboardingRepo, permissionsRepo, guardiansRepo, ble, externalScope = backgroundScope)
        runCurrent()

        viewModel.startSearching()
        runCurrent()
        assertEquals(PairingVisualState.Searching, viewModel.uiState.value.pairingVisualState)

        viewModel.onPairingError("Bluetooth scan timed out")
        runCurrent()
        val errorState = viewModel.uiState.value.pairingVisualState
        assertTrue(errorState is PairingVisualState.Error)
        assertEquals("Bluetooth scan timed out", (errorState as PairingVisualState.Error).message)

        viewModel.cancelSearching()
        runCurrent()
        assertEquals(PairingVisualState.Idle, viewModel.uiState.value.pairingVisualState)
    }

    @Test
    fun refreshPermissions_delegatesToPermissionsRepository() = runTest {
        val onboardingRepo = FakeOnboardingRepository()
        val permissionsRepo = FakePermissionsRepository()
        val guardiansRepo = FakeGuardiansRepository()
        val ble = FakeBleSessionManager()

        val viewModel = OnboardingViewModel(onboardingRepo, permissionsRepo, guardiansRepo, ble, externalScope = backgroundScope)
        runCurrent()

        assertEquals(0, permissionsRepo.refreshCallCount)
        viewModel.refreshPermissions()
        assertEquals(1, permissionsRepo.refreshCallCount)
    }

    @Test
    fun guardianManagement_savesAndClearsGuardians_updatingUiState() = runTest {
        val onboardingRepo = FakeOnboardingRepository()
        val permissionsRepo = FakePermissionsRepository()
        val guardiansRepo = FakeGuardiansRepository()
        val ble = FakeBleSessionManager()

        val viewModel = OnboardingViewModel(onboardingRepo, permissionsRepo, guardiansRepo, ble, externalScope = backgroundScope)
        runCurrent()

        // Initially no guardians
        assertTrue(viewModel.uiState.value.guardians.isEmpty())

        // Save slot 0 (Primary)
        viewModel.saveGuardian(0, "Alice Smith", "+1234567890")
        runCurrent()

        assertEquals(1, viewModel.uiState.value.guardians.size)
        val g0 = viewModel.uiState.value.guardians.first()
        assertEquals(0, g0.slotIndex)
        assertEquals("Alice Smith", g0.name)
        assertEquals("+1234567890", g0.phone)

        // Save slot 1 (Secondary)
        viewModel.saveGuardian(1, "Bob Jones", "+1987654321")
        runCurrent()

        assertEquals(2, viewModel.uiState.value.guardians.size)
        val g1 = viewModel.uiState.value.guardians.find { it.slotIndex == 1 }
        assertNotNull(g1)
        assertEquals("Bob Jones", g1?.name)
        assertEquals("+1987654321", g1?.phone)

        // Clear slot 0
        viewModel.clearGuardian(0)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.guardians.size)
        assertEquals(1, viewModel.uiState.value.guardians.first().slotIndex)

        // Clear slot 1
        viewModel.clearGuardian(1)
        runCurrent()

        assertTrue(viewModel.uiState.value.guardians.isEmpty())
    }
    @Test
    fun pairingWithoutHandshakeTimesOutWithoutSavingFakePairedDevice() = runTest {
        val repo = FakeOnboardingRepository()
        val ble = FakeBleSessionManager()
        val vm = OnboardingViewModel(repo, FakePermissionsRepository(), FakeGuardiansRepository(), ble, externalScope = backgroundScope)
        vm.confirmAndPair("11:22:33:44:55:66", "Naari Watch")
        runCurrent()
        testScheduler.advanceTimeBy(110001)
        runCurrent()
        assertNull(repo.addressFlow.value)
        assertTrue(ble.didDisconnect)
        assertTrue(vm.uiState.value.pairingVisualState is PairingVisualState.Error)
    }

}
