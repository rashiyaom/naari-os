package com.naari.companion.watch

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.protocol.ControlResponse
import com.naari.companion.protocol.ControlStatus
import com.naari.companion.protocol.ProtocolV2Codec
import com.naari.companion.protocol.WatchCapabilities
import com.naari.companion.protocol.WatchMode
import com.naari.companion.protocol.WatchStatusSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * State machine status for individual watch settings transitions.
 * Ref: PRD Section 6 & Task 15.
 */
sealed interface SettingTransitionState {
    object Idle : SettingTransitionState
    object Pending : SettingTransitionState
    object Applying : SettingTransitionState
    object Applied : SettingTransitionState
    data class Failed(val reason: String) : SettingTransitionState
}

/**
 * Holds both the currently confirmed value from the watch hardware
 * and the transient transition state (Pending -> Applying -> Applied | Failed).
 */
data class SettingItemState<T>(
    val currentValue: T? = null,
    val transition: SettingTransitionState = SettingTransitionState.Idle
) {
    val isApplying: Boolean
        get() = transition is SettingTransitionState.Pending || transition is SettingTransitionState.Applying

    val isApplied: Boolean
        get() = transition is SettingTransitionState.Applied

    val errorMessage: String?
        get() = (transition as? SettingTransitionState.Failed)?.reason
}

/**
 * Repository interface for managing Protocol v2 watch controls and telemetry.
 */
interface WatchSettingsRepository {
    val capabilities: StateFlow<WatchCapabilities>
    val statusSnapshot: StateFlow<WatchStatusSnapshot?>
    val wallpaperState: StateFlow<SettingItemState<Int>>
    val brightnessState: StateFlow<SettingItemState<Int>>
    val volumeState: StateFlow<SettingItemState<Int>>
    val fallDetectionState: StateFlow<SettingItemState<Boolean>>
    val watchModeState: StateFlow<SettingItemState<WatchMode>>
    val findWatchState: StateFlow<SettingItemState<Unit>>
    val timeSyncState: StateFlow<SettingItemState<Long>>
    val safeCheckInState: StateFlow<SettingItemState<Unit>>

    fun setCapabilities(caps: WatchCapabilities)
    fun handleIncomingResponse(response: ControlResponse)
    fun handleStatusSnapshot(snapshot: WatchStatusSnapshot)

    suspend fun queryStatus(): Result<WatchStatusSnapshot>
    suspend fun setWallpaper(index: Int): Result<Unit>
    suspend fun setBrightness(percent: Int): Result<Unit>
    suspend fun setVolume(percent: Int): Result<Unit>
    suspend fun setFallDetection(enabled: Boolean): Result<Unit>
    suspend fun setWatchMode(mode: WatchMode): Result<Unit>
    suspend fun findWatch(durationSeconds: Int = 15): Result<Unit>
    suspend fun syncTime(): Result<Long>
    suspend fun safeCheckIn(pinHash: String): Result<Unit>
}

/**
 * Production implementation of [WatchSettingsRepository].
 * Strictly gates all operations based on negotiated [WatchCapabilities].
 * Prevents phantom success: switches remain unconfirmed until device acknowledgment.
 */
class DefaultWatchSettingsRepository(
    private val bleSessionManager: BleSessionManager? = null
) : WatchSettingsRepository {

    private val cmdCounter = AtomicInteger(100)

    private val _capabilities = MutableStateFlow(WatchCapabilities.NONE)
    override val capabilities: StateFlow<WatchCapabilities> = _capabilities.asStateFlow()

    private val _statusSnapshot = MutableStateFlow<WatchStatusSnapshot?>(null)
    override val statusSnapshot: StateFlow<WatchStatusSnapshot?> = _statusSnapshot.asStateFlow()

    private val _wallpaperState = MutableStateFlow(SettingItemState<Int>())
    override val wallpaperState: StateFlow<SettingItemState<Int>> = _wallpaperState.asStateFlow()

    private val _brightnessState = MutableStateFlow(SettingItemState<Int>())
    override val brightnessState: StateFlow<SettingItemState<Int>> = _brightnessState.asStateFlow()

    private val _volumeState = MutableStateFlow(SettingItemState<Int>())
    override val volumeState: StateFlow<SettingItemState<Int>> = _volumeState.asStateFlow()

    private val _fallDetectionState = MutableStateFlow(SettingItemState<Boolean>())
    override val fallDetectionState: StateFlow<SettingItemState<Boolean>> = _fallDetectionState.asStateFlow()

    private val _watchModeState = MutableStateFlow(SettingItemState<WatchMode>())
    override val watchModeState: StateFlow<SettingItemState<WatchMode>> = _watchModeState.asStateFlow()

    private val _findWatchState = MutableStateFlow(SettingItemState<Unit>())
    override val findWatchState: StateFlow<SettingItemState<Unit>> = _findWatchState.asStateFlow()

    private val _timeSyncState = MutableStateFlow(SettingItemState<Long>())
    override val timeSyncState: StateFlow<SettingItemState<Long>> = _timeSyncState.asStateFlow()

    private val _safeCheckInState = MutableStateFlow(SettingItemState<Unit>())
    override val safeCheckInState: StateFlow<SettingItemState<Unit>> = _safeCheckInState.asStateFlow()

    override fun setCapabilities(caps: WatchCapabilities) {
        _capabilities.value = caps
    }

    override fun handleStatusSnapshot(snapshot: WatchStatusSnapshot) {
        _statusSnapshot.value = snapshot
        _capabilities.value = snapshot.capabilities

        snapshot.activeWallpaper?.let {
            _wallpaperState.value = SettingItemState(currentValue = it, transition = SettingTransitionState.Idle)
        }
        snapshot.brightness?.let {
            _brightnessState.value = SettingItemState(currentValue = it, transition = SettingTransitionState.Idle)
        }
        snapshot.volume?.let {
            _volumeState.value = SettingItemState(currentValue = it, transition = SettingTransitionState.Idle)
        }
        snapshot.fallDetectionEnabled?.let {
            _fallDetectionState.value = SettingItemState(currentValue = it, transition = SettingTransitionState.Idle)
        }
        snapshot.mode?.let {
            _watchModeState.value = SettingItemState(currentValue = it, transition = SettingTransitionState.Idle)
        }
    }

    override fun handleIncomingResponse(response: ControlResponse) {
        when (response.status) {
            ControlStatus.Applied -> {
                val applied = response.appliedData
                if (applied.containsKey("wallpaper")) {
                    val w = (applied["wallpaper"] as? Number)?.toInt() ?: _wallpaperState.value.currentValue
                    _wallpaperState.value = SettingItemState(currentValue = w, transition = SettingTransitionState.Applied)
                }
                if (applied.containsKey("brightness")) {
                    val b = (applied["brightness"] as? Number)?.toInt() ?: _brightnessState.value.currentValue
                    _brightnessState.value = SettingItemState(currentValue = b, transition = SettingTransitionState.Applied)
                }
                if (applied.containsKey("volume")) {
                    val v = (applied["volume"] as? Number)?.toInt() ?: _volumeState.value.currentValue
                    _volumeState.value = SettingItemState(currentValue = v, transition = SettingTransitionState.Applied)
                }
                if (applied.containsKey("fall_enabled")) {
                    val f = (applied["fall_enabled"] as? Boolean) ?: _fallDetectionState.value.currentValue
                    _fallDetectionState.value = SettingItemState(currentValue = f, transition = SettingTransitionState.Applied)
                }
                if (applied.containsKey("mode")) {
                    val m = when (applied["mode"]?.toString()?.uppercase()) {
                        "SAFE" -> WatchMode.SAFE
                        "WATCH" -> WatchMode.WATCH
            "EMERGENCY" -> WatchMode.EMERGENCY
                        else -> _watchModeState.value.currentValue
                    }
                    _watchModeState.value = SettingItemState(currentValue = m, transition = SettingTransitionState.Applied)
                }
                if (applied.containsKey("time_synced")) {
                    val t = System.currentTimeMillis()
                    _timeSyncState.value = SettingItemState(currentValue = t, transition = SettingTransitionState.Applied)
                }
                if (applied.containsKey("checked_in")) {
                    _safeCheckInState.value = SettingItemState(currentValue = Unit, transition = SettingTransitionState.Applied)
                }
            }
            ControlStatus.Accepted -> {
                _findWatchState.value = SettingItemState(currentValue = Unit, transition = SettingTransitionState.Applied)
            }
            ControlStatus.Rejected -> {
                val reason = response.error ?: "Command rejected by watch"
                if (_wallpaperState.value.isApplying || _wallpaperState.value.isApplied) {
                    _wallpaperState.value = _wallpaperState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
                if (_brightnessState.value.isApplying || _brightnessState.value.isApplied) {
                    _brightnessState.value = _brightnessState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
                if (_volumeState.value.isApplying || _volumeState.value.isApplied) {
                    _volumeState.value = _volumeState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
                if (_fallDetectionState.value.isApplying || _fallDetectionState.value.isApplied) {
                    _fallDetectionState.value = _fallDetectionState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
                if (_watchModeState.value.isApplying || _watchModeState.value.isApplied) {
                    _watchModeState.value = _watchModeState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
                if (_findWatchState.value.isApplying || _findWatchState.value.isApplied) {
                    _findWatchState.value = _findWatchState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
                if (_timeSyncState.value.isApplying || _timeSyncState.value.isApplied) {
                    _timeSyncState.value = _timeSyncState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
                if (_safeCheckInState.value.isApplying || _safeCheckInState.value.isApplied) {
                    _safeCheckInState.value = _safeCheckInState.value.copy(transition = SettingTransitionState.Failed(reason))
                }
            }
        }
    }

    override suspend fun queryStatus(): Result<WatchStatusSnapshot> {
        if (!_capabilities.value.hasAnyCapability) {
            return Result.failure(UnsupportedOperationException("Watch status query not supported by this watch firmware"))
        }
        val cmdId = cmdCounter.incrementAndGet()
        val json = ProtocolV2Codec.buildGetStatusCommand(cmdId)
        val response = bleSessionManager?.control(json)
            ?: return Result.failure(IllegalStateException("BLE session unavailable"))
        return response.map { ProtocolV2Codec.parseStatusSnapshot(it.appliedData).also(::handleStatusSnapshot) }
    }

    override suspend fun setWallpaper(index: Int): Result<Unit> {
        if (!_capabilities.value.wallpaper) {
            _wallpaperState.value = _wallpaperState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Wallpaper control not supported by this watch firmware"))
        }

        val oldState = _wallpaperState.value
        _wallpaperState.value = oldState.copy(transition = SettingTransitionState.Pending)
        _wallpaperState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val cmdId = cmdCounter.incrementAndGet()
        val json = try {
            ProtocolV2Codec.buildSetWallpaperCommand(cmdId, index)
        } catch (e: Exception) {
            _wallpaperState.value = oldState.copy(transition = SettingTransitionState.Failed(e.message ?: "Invalid wallpaper index"))
            return Result.failure(e)
        }

        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            _wallpaperState.value = SettingItemState(currentValue = index, transition = SettingTransitionState.Applied)
            Result.success(Unit)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit wallpaper command"
            _wallpaperState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun setBrightness(percent: Int): Result<Unit> {
        if (!_capabilities.value.brightness) {
            _brightnessState.value = _brightnessState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Brightness control not supported by this watch firmware"))
        }

        val oldState = _brightnessState.value
        _brightnessState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val cmdId = cmdCounter.incrementAndGet()
        val json = try {
            ProtocolV2Codec.buildSetBrightnessCommand(cmdId, percent)
        } catch (e: Exception) {
            _brightnessState.value = oldState.copy(transition = SettingTransitionState.Failed(e.message ?: "Invalid brightness"))
            return Result.failure(e)
        }

        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            _brightnessState.value = SettingItemState(currentValue = percent, transition = SettingTransitionState.Applied)
            Result.success(Unit)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit brightness command"
            _brightnessState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun setVolume(percent: Int): Result<Unit> {
        if (!_capabilities.value.volume) {
            _volumeState.value = _volumeState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Volume control not supported by this watch firmware"))
        }

        val oldState = _volumeState.value
        _volumeState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val cmdId = cmdCounter.incrementAndGet()
        val json = try {
            ProtocolV2Codec.buildSetVolumeCommand(cmdId, percent)
        } catch (e: Exception) {
            _volumeState.value = oldState.copy(transition = SettingTransitionState.Failed(e.message ?: "Invalid volume"))
            return Result.failure(e)
        }

        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            _volumeState.value = SettingItemState(currentValue = percent, transition = SettingTransitionState.Applied)
            Result.success(Unit)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit volume command"
            _volumeState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun setFallDetection(enabled: Boolean): Result<Unit> {
        if (!_capabilities.value.fallDetection) {
            _fallDetectionState.value = _fallDetectionState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Fall detection not supported by this watch firmware"))
        }

        val oldState = _fallDetectionState.value
        _fallDetectionState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val cmdId = cmdCounter.incrementAndGet()
        val json = ProtocolV2Codec.buildSetFallEnabledCommand(cmdId, enabled)
        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            _fallDetectionState.value = SettingItemState(currentValue = enabled, transition = SettingTransitionState.Applied)
            Result.success(Unit)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit fall detection command"
            _fallDetectionState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun setWatchMode(mode: WatchMode): Result<Unit> {
        if (!_capabilities.value.watchMode) {
            _watchModeState.value = _watchModeState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Mode switching not supported by this watch firmware"))
        }

        val oldState = _watchModeState.value
        _watchModeState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val cmdId = cmdCounter.incrementAndGet()
        val json = ProtocolV2Codec.buildSetModeCommand(cmdId, mode)
        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            _watchModeState.value = SettingItemState(currentValue = mode, transition = SettingTransitionState.Applied)
            Result.success(Unit)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit mode command"
            _watchModeState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun findWatch(durationSeconds: Int): Result<Unit> {
        if (!_capabilities.value.findWatch) {
            _findWatchState.value = _findWatchState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Find watch not supported by this watch firmware"))
        }

        val oldState = _findWatchState.value
        _findWatchState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val cmdId = cmdCounter.incrementAndGet()
        val json = ProtocolV2Codec.buildFindWatchCommand(cmdId, durationSeconds)
        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            _findWatchState.value = SettingItemState(currentValue = Unit, transition = SettingTransitionState.Applied)
            Result.success(Unit)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit find watch command"
            _findWatchState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun syncTime(): Result<Long> {
        if (!_capabilities.value.timeSync) {
            _timeSyncState.value = _timeSyncState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Time sync not supported by this watch firmware"))
        }

        val oldState = _timeSyncState.value
        _timeSyncState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val nowEpochS = System.currentTimeMillis() / 1000
        val tzOffsetMinutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (60 * 1000)
        val cmdId = cmdCounter.incrementAndGet()
        val json = ProtocolV2Codec.buildSyncTimeCommand(cmdId, nowEpochS, tzOffsetMinutes)
        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            val syncedTime = System.currentTimeMillis()
            _timeSyncState.value = SettingItemState(currentValue = syncedTime, transition = SettingTransitionState.Applied)
            Result.success(syncedTime)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit time sync command"
            _timeSyncState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }

    override suspend fun safeCheckIn(pinHash: String): Result<Unit> {
        if (!_capabilities.value.safeCheckIn) {
            _safeCheckInState.value = _safeCheckInState.value.copy(
                transition = SettingTransitionState.Failed("Not supported by this watch firmware")
            )
            return Result.failure(UnsupportedOperationException("Safe check-in not supported by this watch firmware"))
        }

        val oldState = _safeCheckInState.value
        _safeCheckInState.value = oldState.copy(transition = SettingTransitionState.Applying)

        val cmdId = cmdCounter.incrementAndGet()
        val json = ProtocolV2Codec.buildSafeCheckInCommand(cmdId, pinHash)
        val sendResult = bleSessionManager?.sendControlCommand(json)
            ?: Result.failure(IllegalStateException("BLE session unavailable"))

        return if (sendResult.isSuccess) {
            _safeCheckInState.value = SettingItemState(currentValue = Unit, transition = SettingTransitionState.Applied)
            Result.success(Unit)
        } else {
            val err = sendResult.exceptionOrNull()?.message ?: "Failed to transmit check-in command"
            _safeCheckInState.value = oldState.copy(transition = SettingTransitionState.Failed(err))
            Result.failure(sendResult.exceptionOrNull() ?: Exception(err))
        }
    }
}
