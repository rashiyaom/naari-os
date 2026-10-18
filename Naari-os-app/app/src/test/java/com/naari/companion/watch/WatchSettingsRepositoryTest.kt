package com.naari.companion.watch

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.protocol.ControlResponse
import com.naari.companion.protocol.ControlStatus
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.WatchCapabilities
import com.naari.companion.protocol.WatchMode
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchStatusSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSettingsRepositoryTest {

    private class FakeBleSessionManager : BleSessionManager {
        val connectionStateFlow = MutableStateFlow(BleConnectionState.Ready)
        override val connectionState: StateFlow<BleConnectionState> = connectionStateFlow.asStateFlow()

        val incomingRequestsFlow = MutableSharedFlow<WatchRequest>(replay = 1)
        override val incomingRequests: SharedFlow<WatchRequest> = incomingRequestsFlow

        val negotiatedMtuFlow = MutableStateFlow(512)
        override val negotiatedMtu: StateFlow<Int> = negotiatedMtuFlow.asStateFlow()

        val hasV2Flow = MutableStateFlow(true)
        override val hasV2ControlService: StateFlow<Boolean> = hasV2Flow.asStateFlow()

        var lastSentCommandJson: String? = null
        var shouldSucceed = true

        override fun connect(deviceAddress: String) {}
        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)

        override suspend fun sendControlCommand(commandJson: String): Result<Unit> {
            lastSentCommandJson = commandJson
            return if (shouldSucceed) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("GATT command failed on fake link"))
            }
        }

        override fun disconnect() {}
    }

    @Test
    fun capabilityGating_whenCapabilityMissing_rejectsOperationAndSetsFailedState() = runTest {
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultWatchSettingsRepository(fakeBle)

        // Capabilities are NONE by default (standard v1 watch)
        assertEquals(WatchCapabilities.NONE, repo.capabilities.value)

        // Attempting to set wallpaper must fail and NOT send BLE command
        val wallpaperResult = repo.setWallpaper(2)
        assertTrue(wallpaperResult.isFailure)
        assertNull(fakeBle.lastSentCommandJson)
        val wallpaperState = repo.wallpaperState.value
        assertNull(wallpaperState.currentValue)
        assertTrue(wallpaperState.transition is SettingTransitionState.Failed)
        assertEquals("Not supported by this watch firmware", (wallpaperState.transition as SettingTransitionState.Failed).reason)

        // Attempting to toggle fall detection must fail
        val fallResult = repo.setFallDetection(true)
        assertTrue(fallResult.isFailure)
        val fallState = repo.fallDetectionState.value
        assertNull(fallState.currentValue)
        assertTrue(fallState.transition is SettingTransitionState.Failed)

        // Attempting brightness, volume, mode, find watch must all fail
        assertTrue(repo.setBrightness(80).isFailure)
        assertTrue(repo.setVolume(50).isFailure)
        assertTrue(repo.setWatchMode(WatchMode.SAFE).isFailure)
        assertTrue(repo.findWatch(15).isFailure)
        assertTrue(repo.syncTime().isFailure)
        assertTrue(repo.safeCheckIn("1234").isFailure)
    }

    @Test
    fun stateTransitions_whenSupportedAndCommandSucceeds_transitionsToApplied() = runTest {
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultWatchSettingsRepository(fakeBle)

        repo.setCapabilities(WatchCapabilities.ALL)

        val result = repo.setWallpaper(3)
        assertTrue(result.isSuccess)
        assertNotNull(fakeBle.lastSentCommandJson)
        assertTrue(fakeBle.lastSentCommandJson!!.contains("\"set_wallpaper\""))

        val state = repo.wallpaperState.value
        assertEquals(3, state.currentValue)
        assertEquals(SettingTransitionState.Applied, state.transition)
    }

    @Test
    fun stateTransitions_whenSupportedAndCommandFails_transitionsToFailedWithoutMutatingValue() = runTest {
        val fakeBle = FakeBleSessionManager()
        fakeBle.shouldSucceed = false
        val repo = DefaultWatchSettingsRepository(fakeBle)

        repo.setCapabilities(WatchCapabilities.ALL)

        val result = repo.setWallpaper(4)
        assertTrue(result.isFailure)

        val state = repo.wallpaperState.value
        assertNull(state.currentValue) // Never saved without confirmation!
        assertTrue(state.transition is SettingTransitionState.Failed)
    }

    @Test
    fun fallDetectionSwitch_neverShowsSavedBeforeDeviceConfirmation() = runTest {
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultWatchSettingsRepository(fakeBle)

        repo.setCapabilities(WatchCapabilities.ALL)

        // Initial snapshot from watch: fall detection is false
        repo.handleStatusSnapshot(
            WatchStatusSnapshot(
                fallDetectionEnabled = false,
                capabilities = WatchCapabilities.ALL
            )
        )
        assertEquals(false, repo.fallDetectionState.value.currentValue)

        // Simulate failed attempt to toggle to true
        fakeBle.shouldSucceed = false
        val result = repo.setFallDetection(true)
        assertTrue(result.isFailure)

        // Must still be false, never true!
        assertEquals(false, repo.fallDetectionState.value.currentValue)
        assertTrue(repo.fallDetectionState.value.transition is SettingTransitionState.Failed)
    }

    @Test
    fun handleIncomingResponse_appliedAndRejected_updatesStates() = runTest {
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultWatchSettingsRepository(fakeBle)
        repo.setCapabilities(WatchCapabilities.ALL)

        // Watch asynchronously notifies that wallpaper 1 was applied
        repo.handleIncomingResponse(
            ControlResponse(
                commandId = 101,
                status = ControlStatus.Applied,
                appliedData = mapOf("wallpaper" to 1)
            )
        )
        assertEquals(1, repo.wallpaperState.value.currentValue)
        assertEquals(SettingTransitionState.Applied, repo.wallpaperState.value.transition)

        // Watch rejects volume command
        repo.setVolume(90) // Enters applying
        repo.handleIncomingResponse(
            ControlResponse(
                commandId = 102,
                status = ControlStatus.Rejected,
                error = "HARDWARE_BUSY"
            )
        )
        assertTrue(repo.volumeState.value.transition is SettingTransitionState.Failed)
        assertEquals("HARDWARE_BUSY", (repo.volumeState.value.transition as SettingTransitionState.Failed).reason)
    }

    @Test
    fun handleStatusSnapshot_updatesTelemetryAndCapabilities() = runTest {
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultWatchSettingsRepository(fakeBle)

        val snapshot = WatchStatusSnapshot(
            bootId = 9999L,
            batteryPercent = 77,
            activeWallpaper = 0,
            brightness = 65,
            volume = 85,
            fallDetectionEnabled = true,
            mode = WatchMode.SAFE,
            capabilities = WatchCapabilities(wallpaper = true, brightness = true, fallDetection = true)
        )

        repo.handleStatusSnapshot(snapshot)

        assertEquals(snapshot, repo.statusSnapshot.value)
        assertTrue(repo.capabilities.value.wallpaper)
        assertTrue(repo.capabilities.value.brightness)
        assertTrue(repo.capabilities.value.fallDetection)
        assertFalse(repo.capabilities.value.volume) // volume was false

        assertEquals(0, repo.wallpaperState.value.currentValue)
        assertEquals(65, repo.brightnessState.value.currentValue)
        assertEquals(true, repo.fallDetectionState.value.currentValue)
        assertEquals(WatchMode.SAFE, repo.watchModeState.value.currentValue)
    }
}
