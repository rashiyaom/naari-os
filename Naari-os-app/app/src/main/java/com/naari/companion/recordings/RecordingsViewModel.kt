package com.naari.companion.recordings

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.data.RecordingEntity
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.protocol.WatchCapabilities
import com.naari.companion.watch.WatchSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * UI State for the Audio Recordings catalog.
 */
data class RecordingsUiState(
    val isFirmwareSupported: Boolean = false,
    val isBleConnected: Boolean = false,
    val deviceStatus: DeviceStatus = DeviceStatus.NotPaired,
    val recordings: List<RecordingEntity> = emptyList(),
    val availableStorageMb: Long = 0L,
    val currentPlayingId: Long? = null,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Int = 0,
    val playbackDurationMs: Int = 0,
    val inlineError: String? = null
)

/**
 * ViewModel driving the voice memos catalog, playback, and export.
 * Ref: PRD Section 3 (Recordings) & Master Prompt Task 17.
 */
@HiltViewModel
class RecordingsViewModel(
    private val recordingsRepository: RecordingsRepository,
    private val watchSettingsRepository: WatchSettingsRepository,
    private val bleSessionManager: BleSessionManager,
    private val deviceStateRepository: DeviceStateRepository,
    externalScope: CoroutineScope?
) : ViewModel() {

    @Inject
    constructor(
        recordingsRepository: RecordingsRepository,
        watchSettingsRepository: WatchSettingsRepository,
        bleSessionManager: BleSessionManager,
        deviceStateRepository: DeviceStateRepository
    ) : this(
        recordingsRepository = recordingsRepository,
        watchSettingsRepository = watchSettingsRepository,
        bleSessionManager = bleSessionManager,
        deviceStateRepository = deviceStateRepository,
        externalScope = null
    )

    // Overload constructor for testing
    constructor(
        recordingsRepository: RecordingsRepository,
        watchSettingsRepository: WatchSettingsRepository? = null,
        bleSessionManager: BleSessionManager? = null,
        externalScope: CoroutineScope? = null
    ) : this(
        recordingsRepository = recordingsRepository,
        watchSettingsRepository = watchSettingsRepository ?: createFallbackWatchSettings(),
        bleSessionManager = bleSessionManager ?: createFallbackBleSession(),
        deviceStateRepository = createFallbackDeviceStateRepo(),
        externalScope = externalScope
    )

    private val scope = externalScope ?: viewModelScope

    private var mediaPlayer: MediaPlayer? = null

    private val _currentPlayingId = MutableStateFlow<Long?>(null)
    private val _isPlaying = MutableStateFlow(false)
    private val _playbackPositionMs = MutableStateFlow(0)
    private val _playbackDurationMs = MutableStateFlow(0)
    private val _inlineError = MutableStateFlow<String?>(null)

    init {
        // Ticker to update playback progress slider
        scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playbackPositionMs.value = player.currentPosition
                        _playbackDurationMs.value = player.duration
                    }
                }
                delay(200L)
            }
        }
    }

    private val playbackStateFlow = combine(
        _currentPlayingId,
        _isPlaying,
        _playbackPositionMs,
        _playbackDurationMs
    ) { playingId, isPlaying, pos, dur ->
        PlaybackSnapshot(playingId, isPlaying, pos, dur)
    }

    private data class PlaybackSnapshot(
        val id: Long?,
        val isPlaying: Boolean,
        val positionMs: Int,
        val durationMs: Int
    )

    private data class WatchAudioState(
        val caps: WatchCapabilities,
        val connState: BleConnectionState,
        val devStatus: DeviceStatus
    )

    private val watchAudioStateFlow = combine(
        watchSettingsRepository.capabilities,
        bleSessionManager.connectionState,
        deviceStateRepository.deviceStatus
    ) { caps, connState, devStatus ->
        WatchAudioState(caps, connState, devStatus)
    }

    val uiState: StateFlow<RecordingsUiState> = combine(
        recordingsRepository.recordings,
        watchAudioStateFlow,
        playbackStateFlow,
        _inlineError
    ) { recordings, watchAudio, playback, err ->
        val storageMb = recordingsRepository.getAvailableStorageBytes() / (1024 * 1024)
        val isConnected = watchAudio.connState == BleConnectionState.Ready ||
                watchAudio.connState == BleConnectionState.ConnectedServiceUnavailable

        RecordingsUiState(
            isFirmwareSupported = watchAudio.caps.audioTransfer,
            isBleConnected = isConnected,
            deviceStatus = watchAudio.devStatus,
            recordings = recordings,
            availableStorageMb = storageMb,
            currentPlayingId = playback.id,
            isPlaying = playback.isPlaying,
            playbackPositionMs = playback.positionMs,
            playbackDurationMs = playback.durationMs,
            inlineError = err
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = RecordingsUiState()
    )

    fun playRecording(recording: RecordingEntity, context: Context? = null) {
        val path = recording.localFilePath
        if (path == null) {
            _inlineError.value = "Audio memo not yet synchronized to phone"
            return
        }
        val file = File(path)
        if (!file.exists()) {
            _inlineError.value = "Recording file not found on device storage."
            return
        }

        try {
            stopPlayback()

            mediaPlayer = MediaPlayer().apply {
                if (context != null) {
                    setDataSource(context, Uri.fromFile(file))
                } else {
                    setDataSource(file.absolutePath)
                }
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playbackPositionMs.value = 0
                    _currentPlayingId.value = null
                }
                start()
            }

            _currentPlayingId.value = recording.id
            _isPlaying.value = true
            _playbackDurationMs.value = mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            _inlineError.value = "Unable to play audio memo: ${e.message}"
        }
    }

    fun playRecording(context: Context, recording: RecordingEntity) {
        playRecording(recording, context)
    }

    fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                _isPlaying.value = true
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _playbackPositionMs.value = positionMs
    }

    fun seekPlayback(positionMs: Int) {
        seekTo(positionMs)
    }

    fun getExportUri(id: Long, context: Context): Uri? {
        return recordingsRepository.getExportUri(id, context).getOrNull()
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingId.value = null
        _playbackPositionMs.value = 0
    }

    fun renameRecording(id: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        scope.launch {
            recordingsRepository.renameRecording(id, newTitle.trim())
        }
    }

    fun deleteRecording(id: Long) {
        if (_currentPlayingId.value == id) {
            stopPlayback()
        }
        scope.launch {
            recordingsRepository.deleteRecording(id)
        }
    }

    fun clearInlineError() {
        _inlineError.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
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

        private fun createFallbackWatchSettings(): WatchSettingsRepository = object : WatchSettingsRepository {
            override val capabilities = MutableStateFlow(com.naari.companion.protocol.WatchCapabilities.NONE)
            override val statusSnapshot = MutableStateFlow<com.naari.companion.protocol.WatchStatusSnapshot?>(null)
            override val wallpaperState = MutableStateFlow(com.naari.companion.watch.SettingItemState<Int>())
            override val brightnessState = MutableStateFlow(com.naari.companion.watch.SettingItemState<Int>())
            override val volumeState = MutableStateFlow(com.naari.companion.watch.SettingItemState<Int>())
            override val fallDetectionState = MutableStateFlow(com.naari.companion.watch.SettingItemState<Boolean>())
            override val watchModeState = MutableStateFlow(com.naari.companion.watch.SettingItemState<com.naari.companion.protocol.WatchMode>())
            override val findWatchState = MutableStateFlow(com.naari.companion.watch.SettingItemState<Unit>())
            override val timeSyncState = MutableStateFlow(com.naari.companion.watch.SettingItemState<Long>())
            override val safeCheckInState = MutableStateFlow(com.naari.companion.watch.SettingItemState<Unit>())
            override fun setCapabilities(caps: com.naari.companion.protocol.WatchCapabilities) {}
            override fun handleIncomingResponse(response: com.naari.companion.protocol.ControlResponse) {}
            override fun handleStatusSnapshot(snapshot: com.naari.companion.protocol.WatchStatusSnapshot) {}
            override suspend fun queryStatus() = Result.failure<com.naari.companion.protocol.WatchStatusSnapshot>(UnsupportedOperationException())
            override suspend fun setWallpaper(index: Int) = Result.success(Unit)
            override suspend fun setBrightness(percent: Int) = Result.success(Unit)
            override suspend fun setVolume(percent: Int) = Result.success(Unit)
            override suspend fun setFallDetection(enabled: Boolean) = Result.success(Unit)
            override suspend fun setWatchMode(mode: com.naari.companion.protocol.WatchMode) = Result.success(Unit)
            override suspend fun findWatch(durationSeconds: Int) = Result.success(Unit)
            override suspend fun syncTime() = Result.success(0L)
            override suspend fun safeCheckIn(pinHash: String) = Result.success(Unit)
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
