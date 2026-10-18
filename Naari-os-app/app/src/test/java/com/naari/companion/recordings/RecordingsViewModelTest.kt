package com.naari.companion.recordings

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.data.RecordingEntity
import com.naari.companion.data.RecordingSyncState
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.WatchCapabilities
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.watch.DefaultWatchSettingsRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModelTest {

    private class FakeRecordingsRepository : RecordingsRepository {
        val listFlow = MutableStateFlow<List<RecordingEntity>>(emptyList())
        override val recordings: Flow<List<RecordingEntity>> = listFlow

        var storageBytes: Long = 100 * 1024 * 1024L // 100 MB
        var lastRenamedId: Long? = null
        var lastRenamedTitle: String? = null
        var lastDeletedId: Long? = null

        override fun getAvailableStorageBytes(): Long = storageBytes

        override suspend fun addCapturedRecording(
            recordingId: String,
            deviceAddress: String,
            timestampMs: Long,
            durationMs: Long,
            totalBytes: Long,
            sha256Hex: String,
            title: String
        ): Long = 1L

        override suspend fun renameRecording(id: Long, newTitle: String): Result<Unit> {
            lastRenamedId = id
            lastRenamedTitle = newTitle
            return Result.success(Unit)
        }

        override suspend fun deleteRecording(id: Long): Result<Unit> {
            lastDeletedId = id
            return Result.success(Unit)
        }

        override fun getExportUri(id: Long, context: android.content.Context): Result<android.net.Uri> {
            return Result.failure(UnsupportedOperationException("Stub"))
        }

        override suspend fun executeTransfer(
            recordingId: String,
            chunkSource: suspend (startOffset: Long, onChunkReceived: suspend (AudioChunk) -> Unit) -> Unit
        ): Result<File> = Result.failure(UnsupportedOperationException("Stub"))
    }

    private class FakeBleSessionManager : BleSessionManager {
        val connState = MutableStateFlow(BleConnectionState.Ready)
        override val connectionState: StateFlow<BleConnectionState> = connState.asStateFlow()

        override val incomingRequests: SharedFlow<WatchRequest> = MutableSharedFlow()
        override val negotiatedMtu: StateFlow<Int> = MutableStateFlow(512)

        override fun connect(deviceAddress: String) {}
        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)
        override suspend fun sendControlCommand(commandJson: String): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
    }

    @Test
    fun uiState_reflectsCapabilitiesAndStorage() = runTest {
        val repo = FakeRecordingsRepository()
        val ble = FakeBleSessionManager()
        val settings = DefaultWatchSettingsRepository(ble)

        settings.setCapabilities(WatchCapabilities(audioTransfer = true))
        repo.storageBytes = 250 * 1024 * 1024L // 250 MB

        val viewModel = RecordingsViewModel(
            recordingsRepository = repo,
            watchSettingsRepository = settings,
            bleSessionManager = ble,
            externalScope = backgroundScope
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isFirmwareSupported)
        assertTrue(state.isBleConnected)
        assertEquals(250L, state.availableStorageMb)
        assertNull(state.inlineError)
    }

    @Test
    fun uiState_whenAudioTransferNotSupported_showsFalseForFirmwareSupported() = runTest {
        val repo = FakeRecordingsRepository()
        val ble = FakeBleSessionManager()
        val settings = DefaultWatchSettingsRepository(ble)

        // audioTransfer is false
        settings.setCapabilities(WatchCapabilities(audioTransfer = false))

        val viewModel = RecordingsViewModel(
            recordingsRepository = repo,
            watchSettingsRepository = settings,
            bleSessionManager = ble,
            externalScope = backgroundScope
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isFirmwareSupported)
    }

    @Test
    fun playRecording_whenLocalFilePathIsNull_setsInlineError() = runTest {
        val repo = FakeRecordingsRepository()
        val viewModel = RecordingsViewModel(
            recordingsRepository = repo,
            externalScope = backgroundScope
        )
        runCurrent()

        val unsyncedEntity = RecordingEntity(
            id = 1L,
            recordingId = "rec_unsynced",
            watchDeviceAddress = "AA:BB:CC:DD:EE:FF",
            timestampMs = 1000L,
            durationMs = 5000L,
            totalBytes = 2000L,
            sha256Hex = "abc",
            localFilePath = null,
            title = "Unsynced Memo",
            syncState = RecordingSyncState.WaitingForTransfer
        )

        viewModel.playRecording(unsyncedEntity)
        runCurrent()

        assertEquals("Audio memo not yet synchronized to phone", viewModel.uiState.value.inlineError)

        viewModel.clearInlineError()
        runCurrent()
        assertNull(viewModel.uiState.value.inlineError)
    }

    @Test
    fun renameRecording_invokesRepository() = runTest {
        val repo = FakeRecordingsRepository()
        val viewModel = RecordingsViewModel(
            recordingsRepository = repo,
            externalScope = backgroundScope
        )
        runCurrent()

        viewModel.renameRecording(42L, "New Title")
        runCurrent()

        assertEquals(42L, repo.lastRenamedId)
        assertEquals("New Title", repo.lastRenamedTitle)
    }

    @Test
    fun deleteRecording_invokesRepository() = runTest {
        val repo = FakeRecordingsRepository()
        val viewModel = RecordingsViewModel(
            recordingsRepository = repo,
            externalScope = backgroundScope
        )
        runCurrent()

        viewModel.deleteRecording(99L)
        runCurrent()

        assertEquals(99L, repo.lastDeletedId)
    }
}
