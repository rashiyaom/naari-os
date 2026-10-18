package com.naari.companion.watch

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.WatchCapabilities
import com.naari.companion.protocol.WatchMode
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchStatusSnapshot
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
class WatchViewModelTest {

    private class FakeBleSessionManager : BleSessionManager {
        val connectionStateFlow = MutableStateFlow(BleConnectionState.Ready)
        override val connectionState: StateFlow<BleConnectionState> = connectionStateFlow.asStateFlow()

        val incomingRequestsFlow = MutableSharedFlow<WatchRequest>(replay = 1)
        override val incomingRequests: SharedFlow<WatchRequest> = incomingRequestsFlow

        val negotiatedMtuFlow = MutableStateFlow(512)
        override val negotiatedMtu: StateFlow<Int> = negotiatedMtuFlow.asStateFlow()

        val batteryPercentFlow = MutableStateFlow<Int?>(85)
        override val batteryPercent: StateFlow<Int?> = batteryPercentFlow.asStateFlow()

        val hasV2Flow = MutableStateFlow(false)
        override val hasV2ControlService: StateFlow<Boolean> = hasV2Flow.asStateFlow()

        override fun connect(deviceAddress: String) {}
        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)
        override suspend fun sendControlCommand(commandJson: String): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        val addressFlow = MutableStateFlow<String?>("AA:BB:CC:DD:EE:FF")
        override val pairedDeviceAddress: Flow<String?> = addressFlow

        val nameFlow = MutableStateFlow<String?>("Naari Watch Ultra")
        override val pairedDeviceName: Flow<String?> = nameFlow

        override val isOnboardingCompleted: Flow<Boolean> = MutableStateFlow(true)
        override suspend fun setOnboardingCompleted(completed: Boolean) {}
        override suspend fun savePairedDevice(address: String, name: String) {}
        override suspend fun clearPairedDevice() {}
        override suspend fun resetOnboarding() {}
    }

    @Test
    fun uiState_whenFirmwareIsV1_exposesZeroCapabilitiesAndShowsV1Badge() = runTest {
        val ble = FakeBleSessionManager()
        ble.hasV2Flow.value = false
        val onboarding = FakeOnboardingRepository()
        val settingsRepo = DefaultWatchSettingsRepository(ble)

        val viewModel = WatchViewModel(
            watchSettingsRepository = settingsRepo,
            bleSessionManager = ble,
            onboardingRepository = onboarding,
            externalScope = backgroundScope
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("Naari Watch Ultra", state.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", state.deviceAddress)
        assertEquals(85, state.batteryPercent)
        assertFalse(state.hasV2Service)
        assertEquals(WatchCapabilities.NONE, state.capabilities)
        assertFalse(state.capabilities.hasAnyCapability)
    }

    @Test
    fun uiState_whenActionAttemptedOnUnsupportedFeature_recordsInlineError() = runTest {
        val ble = FakeBleSessionManager()
        val onboarding = FakeOnboardingRepository()
        val settingsRepo = DefaultWatchSettingsRepository(ble)

        val viewModel = WatchViewModel(
            watchSettingsRepository = settingsRepo,
            bleSessionManager = ble,
            onboardingRepository = onboarding,
            externalScope = backgroundScope
        )
        runCurrent()

        // Calling selectWallpaper when wallpaper capability is false
        viewModel.selectWallpaper(2)
        runCurrent()

        val state = viewModel.uiState.value
        assertNotNull(state.inlineError)
        assertTrue(state.inlineError!!.contains("not supported"))

        // Clear error
        viewModel.clearInlineError()
        runCurrent()
        assertNull(viewModel.uiState.value.inlineError)
    }

    @Test
    fun uiState_whenCapabilitiesSupported_appliesSettingsSuccessfully() = runTest {
        val ble = FakeBleSessionManager()
        ble.hasV2Flow.value = true
        val onboarding = FakeOnboardingRepository()
        val settingsRepo = DefaultWatchSettingsRepository(ble)
        settingsRepo.setCapabilities(WatchCapabilities.ALL)

        val viewModel = WatchViewModel(
            watchSettingsRepository = settingsRepo,
            bleSessionManager = ble,
            onboardingRepository = onboarding,
            externalScope = backgroundScope
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.capabilities.wallpaper)

        // Select wallpaper 1
        viewModel.selectWallpaper(1)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.wallpaper.currentValue)
        assertEquals(SettingTransitionState.Applied, viewModel.uiState.value.wallpaper.transition)
        assertNull(viewModel.uiState.value.inlineError)

        // Toggle fall detection
        viewModel.toggleFallDetection(true)
        runCurrent()
        assertEquals(true, viewModel.uiState.value.fallDetection.currentValue)
        assertEquals(SettingTransitionState.Applied, viewModel.uiState.value.fallDetection.transition)
    }
}
