package com.naari.companion.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.protocol.WatchCapabilities
import com.naari.companion.protocol.WatchMode
import com.naari.companion.protocol.WatchStatusSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Complete UI State for the Watch controls destination.
 */
data class WatchUiState(
    val connectionState: BleConnectionState = BleConnectionState.Disconnected,
    val deviceStatus: DeviceStatus = DeviceStatus.NotPaired,
    val syncStatus: SyncStatus = SyncStatus.UpToDate(),
    val isConnected: Boolean = false,
    val hasV2Service: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val batteryPercent: Int? = null,
    val capabilities: WatchCapabilities = WatchCapabilities.NONE,
    val statusSnapshot: WatchStatusSnapshot? = null,
    val wallpaper: SettingItemState<Int> = SettingItemState(),
    val brightness: SettingItemState<Int> = SettingItemState(),
    val volume: SettingItemState<Int> = SettingItemState(),
    val fallDetection: SettingItemState<Boolean> = SettingItemState(),
    val mode: SettingItemState<WatchMode> = SettingItemState(),
    val findWatch: SettingItemState<Unit> = SettingItemState(),
    val timeSync: SettingItemState<Long> = SettingItemState(),
    val safeCheckIn: SettingItemState<Unit> = SettingItemState(),
    val inlineError: String? = null
)

private data class WatchDeviceDetails(
    val address: String?,
    val name: String?,
    val battery: Int?,
    val connState: BleConnectionState,
    val hasV2: Boolean
)

private data class WatchControlStates(
    val wallpaper: SettingItemState<Int>,
    val brightness: SettingItemState<Int>,
    val volume: SettingItemState<Int>,
    val fallDetection: SettingItemState<Boolean>,
    val mode: SettingItemState<WatchMode>,
    val findWatch: SettingItemState<Unit>,
    val timeSync: SettingItemState<Long>,
    val safeCheckIn: SettingItemState<Unit>
)

/**
 * ViewModel driving the capability-gated Watch controls screen.
 * Ref: PRD Section 6 & Task 15.
 */
@HiltViewModel
class WatchViewModel(
    private val watchSettingsRepository: WatchSettingsRepository,
    private val bleSessionManager: BleSessionManager,
    private val onboardingRepository: OnboardingRepository,
    private val deviceStateRepository: DeviceStateRepository,
    externalScope: CoroutineScope?
) : ViewModel() {

    @Inject
    constructor(
        watchSettingsRepository: WatchSettingsRepository,
        bleSessionManager: BleSessionManager,
        onboardingRepository: OnboardingRepository,
        deviceStateRepository: DeviceStateRepository
    ) : this(
        watchSettingsRepository = watchSettingsRepository,
        bleSessionManager = bleSessionManager,
        onboardingRepository = onboardingRepository,
        deviceStateRepository = deviceStateRepository,
        externalScope = null
    )

    // Overload constructor for testing
    constructor(
        watchSettingsRepository: WatchSettingsRepository,
        bleSessionManager: BleSessionManager? = null,
        onboardingRepository: OnboardingRepository? = null,
        externalScope: CoroutineScope? = null
    ) : this(
        watchSettingsRepository = watchSettingsRepository,
        bleSessionManager = bleSessionManager ?: createFallbackBleSession(),
        onboardingRepository = onboardingRepository ?: createFallbackOnboarding(),
        deviceStateRepository = createFallbackDeviceStateRepo(),
        externalScope = externalScope
    )

    private val scope = externalScope ?: viewModelScope

    private val _inlineError = MutableStateFlow<String?>(null)
    val inlineError: StateFlow<String?> = _inlineError.asStateFlow()

    private val deviceDetailsFlow = combine(
        bleSessionManager.connectionState,
        bleSessionManager.batteryPercent,
        bleSessionManager.hasV2ControlService,
        onboardingRepository.pairedDeviceAddress,
        onboardingRepository.pairedDeviceName
    ) { connState, battery, hasV2, addr, name ->
        WatchDeviceDetails(
            address = addr,
            name = name,
            battery = battery,
            connState = connState,
            hasV2 = hasV2
        )
    }

    private val controlStatesFlow = combine(
        watchSettingsRepository.wallpaperState,
        watchSettingsRepository.brightnessState,
        watchSettingsRepository.volumeState,
        watchSettingsRepository.fallDetectionState,
        watchSettingsRepository.watchModeState
    ) { wallpaper, brightness, volume, fall, mode ->
        WatchControlStates(
            wallpaper = wallpaper,
            brightness = brightness,
            volume = volume,
            fallDetection = fall,
            mode = mode,
            findWatch = watchSettingsRepository.findWatchState.value,
            timeSync = watchSettingsRepository.timeSyncState.value,
            safeCheckIn = watchSettingsRepository.safeCheckInState.value
        )
    }

    private data class WatchRepoState(
        val capabilities: WatchCapabilities,
        val snapshot: WatchStatusSnapshot?,
        val devStatus: DeviceStatus,
        val syncStat: SyncStatus
    )

    private val watchRepoStateFlow = combine(
        watchSettingsRepository.capabilities,
        watchSettingsRepository.statusSnapshot,
        deviceStateRepository.deviceStatus,
        deviceStateRepository.syncStatus
    ) { capabilities, snapshot, devStatus, syncStat ->
        WatchRepoState(capabilities, snapshot, devStatus, syncStat)
    }

    val uiState: StateFlow<WatchUiState> = combine(
        deviceDetailsFlow,
        controlStatesFlow,
        watchRepoStateFlow,
        _inlineError
    ) { details, controls, repoState, err ->
        WatchUiState(
            connectionState = details.connState,
            deviceStatus = repoState.devStatus,
            syncStatus = repoState.syncStat,
            isConnected = details.connState == BleConnectionState.Ready || details.connState == BleConnectionState.ConnectedServiceUnavailable,
            hasV2Service = details.hasV2,
            deviceName = details.name,
            deviceAddress = details.address,
            batteryPercent = details.battery,
            capabilities = repoState.capabilities,
            statusSnapshot = repoState.snapshot,
            wallpaper = controls.wallpaper,
            brightness = controls.brightness,
            volume = controls.volume,
            fallDetection = controls.fallDetection,
            mode = controls.mode,
            findWatch = controls.findWatch,
            timeSync = controls.timeSync,
            safeCheckIn = controls.safeCheckIn,
            inlineError = err
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = WatchUiState()
    )

    fun selectWallpaper(index: Int) {
        if (!uiState.value.capabilities.wallpaper) {
            _inlineError.value = "Wallpaper switching not supported by this watch firmware."
            return
        }
        deviceStateRepository.notifySyncStarted()
        scope.launch {
            val result = watchSettingsRepository.setWallpaper(index)
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to set wallpaper")
            }
        }
    }

    fun changeBrightness(percent: Int) {
        if (!uiState.value.capabilities.brightness) {
            _inlineError.value = "Brightness control not supported by this watch firmware."
            return
        }
        deviceStateRepository.notifySyncStarted()
        scope.launch {
            val result = watchSettingsRepository.setBrightness(percent)
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to change brightness")
            }
        }
    }

    fun changeVolume(percent: Int) {
        if (!uiState.value.capabilities.volume) {
            _inlineError.value = "Volume control not supported by this watch firmware."
            return
        }
        deviceStateRepository.notifySyncStarted()
        scope.launch {
            val result = watchSettingsRepository.setVolume(percent)
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to change volume")
            }
        }
    }

    fun toggleFallDetection(enabled: Boolean) {
        if (!uiState.value.capabilities.fallDetection) {
            _inlineError.value = "Fall detection not supported by this watch firmware."
            return
        }
        deviceStateRepository.notifySyncStarted()
        scope.launch {
            val result = watchSettingsRepository.setFallDetection(enabled)
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to toggle fall detection")
            }
        }
    }

    fun selectMode(mode: WatchMode) {
        if (!uiState.value.capabilities.watchMode) {
            _inlineError.value = "Operating mode selection not supported by this watch firmware."
            return
        }
        deviceStateRepository.notifySyncStarted()
        scope.launch {
            val result = watchSettingsRepository.setWatchMode(mode)
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to change mode")
            }
        }
    }

    fun findWatch() {
        if (!uiState.value.capabilities.findWatch) {
            _inlineError.value = "Find watch not supported by this watch firmware."
            return
        }
        scope.launch {
            watchSettingsRepository.findWatch()
        }
    }

    fun syncTime() {
        if (!uiState.value.capabilities.timeSync) {
            _inlineError.value = "Time synchronization not supported by this watch firmware."
            return
        }
        deviceStateRepository.notifySyncStarted()
        scope.launch {
            val result = watchSettingsRepository.syncTime()
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to sync watch time")
            }
        }
    }

    fun safeCheckIn() {
        if (!uiState.value.capabilities.safeCheckIn) {
            _inlineError.value = "Safe check-in not supported by this watch firmware."
            return
        }
        scope.launch {
            watchSettingsRepository.safeCheckIn("0000")
        }
    }

    fun clearInlineError() {
        _inlineError.value = null
    }

    companion object {
        private fun createFallbackBleSession(): BleSessionManager = object : BleSessionManager {
            override val connectionState = MutableStateFlow(BleConnectionState.Disconnected)
            override val connectionStep = MutableStateFlow(com.naari.companion.ble.BleConnectionStep.Disconnected)
            override val hasRequiredGattService = MutableStateFlow(false)
            override val hasV2ControlService = MutableStateFlow(false)
            override val negotiatedMtu = MutableStateFlow(23)
            override val lastHelloTimestampMs = MutableStateFlow<Long?>(null)
            override val batteryPercent = MutableStateFlow<Int?>(null)
            override val incomingRequests = kotlinx.coroutines.flow.MutableSharedFlow<com.naari.companion.protocol.WatchRequest>()
            override fun connect(deviceAddress: String) {}
            override suspend fun sendReply(reply: com.naari.companion.protocol.PhoneReply) = Result.success(Unit)
            override suspend fun syncProfile(profile: com.naari.companion.protocol.PhoneProfileMessage) = Result.success(Unit)
            override suspend fun sendControlCommand(commandJson: String) = Result.success(Unit)
            override fun disconnect() {}
        }

        private fun createFallbackOnboarding(): OnboardingRepository = object : OnboardingRepository {
            override val isOnboardingCompleted = MutableStateFlow(true)
            override val pairedDeviceAddress = MutableStateFlow<String?>(null)
            override val pairedDeviceName = MutableStateFlow<String?>(null)
            override suspend fun setOnboardingCompleted(completed: Boolean) {}
            override suspend fun savePairedDevice(address: String, name: String) {}
            override suspend fun clearPairedDevice() {}
            override suspend fun resetOnboarding() {}
        }

        private fun createFallbackDeviceStateRepo(): DeviceStateRepository = object : DeviceStateRepository {
            override val deviceStatus = MutableStateFlow<DeviceStatus>(DeviceStatus.NotPaired)
            override val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.UpToDate())
            override val readinessState = MutableStateFlow(com.naari.companion.ble.ReadinessState.Disconnected)
            override val batteryPercent = MutableStateFlow<Int?>(null)
            override val pairedDeviceAddress = MutableStateFlow<String?>(null)
            override val pairedDeviceName = MutableStateFlow<String?>(null)
            override val lastSyncTimestampMs = MutableStateFlow<Long?>(null)
            override fun notifySyncStarted() {}
            override fun notifySyncCompleted(timestampMs: Long) {}
            override fun notifySyncFailed(reason: String) {}
        }
    }
}
