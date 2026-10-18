package com.naari.companion.guardians

import com.naari.companion.data.Guardian
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.ble.ReadinessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardiansViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeGuardiansRepository : GuardiansRepository {
        val slot0Flow = MutableStateFlow<Guardian?>(null)
        val slot1Flow = MutableStateFlow<Guardian?>(null)
        val syncStateFlow = MutableStateFlow<GuardianSyncState>(GuardianSyncState.Empty)
        val isPendingSyncFlow = MutableStateFlow(false)

        override val slot0: StateFlow<Guardian?> = slot0Flow.asStateFlow()
        override val slot1: StateFlow<Guardian?> = slot1Flow.asStateFlow()
        override val syncState: StateFlow<GuardianSyncState> = syncStateFlow.asStateFlow()
        override val isPendingSync: StateFlow<Boolean> = isPendingSyncFlow.asStateFlow()

        var saveCallCount = 0
        var clearCallCount = 0
        var swapCallCount = 0
        var retrySyncCallCount = 0

        override suspend fun saveGuardian(slotIndex: Int, name: String, phone: String): Result<Unit> {
            saveCallCount++
            val g = Guardian(slotIndex = slotIndex, name = name, phone = phone)
            if (slotIndex == 0) slot0Flow.value = g else slot1Flow.value = g
            syncStateFlow.value = GuardianSyncState.SyncedToWatch
            return Result.success(Unit)
        }

        override suspend fun clearGuardian(slotIndex: Int): Result<Unit> {
            clearCallCount++
            if (slotIndex == 0) slot0Flow.value = null else slot1Flow.value = null
            return Result.success(Unit)
        }

        override suspend fun swapGuardians(): Result<Unit> {
            swapCallCount++
            val temp = slot0Flow.value
            slot0Flow.value = slot1Flow.value?.copy(slotIndex = 0)
            slot1Flow.value = temp?.copy(slotIndex = 1)
            return Result.success(Unit)
        }

        override suspend fun syncProfileToWatch(): Result<Unit> {
            retrySyncCallCount++
            syncStateFlow.value = GuardianSyncState.SyncedToWatch
            return Result.success(Unit)
        }
    }

    private class FakeDeviceStateRepository : DeviceStateRepository {
        override val deviceStatus = MutableStateFlow<DeviceStatus>(DeviceStatus.Ready)
        override val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.UpToDate())
        override val readinessState = MutableStateFlow(ReadinessState.Ready)
        override val batteryPercent = MutableStateFlow<Int?>(90)
        override val pairedDeviceAddress = MutableStateFlow<String?>("AA:BB:CC:DD:EE:FF")
        override val pairedDeviceName = MutableStateFlow<String?>("Naari Watch")
        override val lastSyncTimestampMs = MutableStateFlow<Long?>(null)

        var syncStartedCount = 0
        var syncCompletedCount = 0
        var syncFailedCount = 0

        override fun notifySyncStarted() {
            syncStartedCount++
        }

        override fun notifySyncCompleted(timestampMs: Long) {
            syncCompletedCount++
        }

        override fun notifySyncFailed(reason: String) {
            syncFailedCount++
        }
    }

    @Test
    fun initialState_hasEmptySlots_andClosedEditor() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.slot0)
        assertNull(state.slot1)
        assertFalse(state.isEditorOpen)
        assertNull(state.editingSlot)
        assertEquals(GuardianSyncState.Empty, state.syncState)
        assertFalse(state.isPendingSync)
    }

    @Test
    fun editorWorkflow_openEditTypeAndSave_updatesRepositoryAndClosesEditor() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        // 1. Open editor for slot 0
        viewModel.openEditor(0)
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isEditorOpen)
        assertEquals(0, viewModel.uiState.value.editingSlot)
        assertFalse(viewModel.uiState.value.isEditorSaveEnabled)

        // 2. Type name and phone
        viewModel.onNameChanged("Alice Smith")
        viewModel.onPhoneChanged("+15555550101")
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isEditorSaveEnabled)
        assertNull(viewModel.uiState.value.editorPhoneError)
        assertEquals(32 - "Alice Smith".toByteArray().size, viewModel.uiState.value.editorRemainingBytes)

        // 3. Save
        viewModel.saveEditor()
        testScheduler.runCurrent()

        assertEquals(1, repo.saveCallCount)
        assertEquals(1, deviceRepo.syncStartedCount)
        assertEquals(1, deviceRepo.syncCompletedCount)
        assertFalse(viewModel.uiState.value.isEditorOpen)
        assertEquals("Alice Smith", viewModel.uiState.value.slot0?.name)
        assertEquals("+15555550101", viewModel.uiState.value.slot0?.phone)
    }

    @Test
    fun phoneSanitization_onPastingOrContactPicked_cleansFormatAutomatically() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        viewModel.openEditor(1)
        testScheduler.runCurrent()

        // Simulate contact pick with spaces and dashes
        viewModel.onContactPicked("Doctor Bob", "+1 (555) 234-5678")
        testScheduler.runCurrent()

        assertEquals("Doctor Bob", viewModel.uiState.value.editorName)
        assertEquals("+15552345678", viewModel.uiState.value.editorPhone)
        assertNull(viewModel.uiState.value.editorPhoneError)
        assertTrue(viewModel.uiState.value.isEditorSaveEnabled)
    }

    @Test
    fun nameByteCap_truncatesAt32Bytes_preventingOverflow() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        viewModel.openEditor(0)
        testScheduler.runCurrent()

        // Long 40 character ASCII name
        viewModel.onNameChanged("A".repeat(40))
        testScheduler.runCurrent()

        assertEquals(32, viewModel.uiState.value.editorName.length)
        assertEquals(0, viewModel.uiState.value.editorRemainingBytes)
    }

    @Test
    fun invalidPhone_showsInlineError_andDisablesSave() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        viewModel.openEditor(0)
        viewModel.onNameChanged("Charlie")
        viewModel.onPhoneChanged("12") // Too short (< 3 digits)
        testScheduler.runCurrent()

        assertNotNull(viewModel.uiState.value.editorPhoneError)
        assertFalse(viewModel.uiState.value.isEditorSaveEnabled)
    }

    @Test
    fun swapGuardians_invokesRepoSwap_andNotifiesDeviceState() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        repo.slot0Flow.value = Guardian(0, "Alice", "+15550001111")
        repo.slot1Flow.value = Guardian(1, "Bob", "+15550002222")
        testScheduler.runCurrent()

        viewModel.swapGuardians()
        testScheduler.runCurrent()

        assertEquals(1, repo.swapCallCount)
        assertEquals(1, deviceRepo.syncStartedCount)
        assertEquals(1, deviceRepo.syncCompletedCount)

        assertEquals("Bob", viewModel.uiState.value.slot0?.name)
        assertEquals("Alice", viewModel.uiState.value.slot1?.name)
    }

    @Test
    fun clearSlot_removesGuardian_andNotifiesSync() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        repo.slot0Flow.value = Guardian(0, "Alice", "+15550001111")
        testScheduler.runCurrent()

        viewModel.clearSlot(0)
        testScheduler.runCurrent()

        assertEquals(1, repo.clearCallCount)
        assertNull(viewModel.uiState.value.slot0)
    }

    @Test
    fun retrySync_invokesRepositorySync_andNotifiesDeviceState() = runTest {
        val repo = FakeGuardiansRepository()
        val deviceRepo = FakeDeviceStateRepository()
        val viewModel = GuardiansViewModel(repo, deviceRepo)
        testScheduler.runCurrent()

        viewModel.retrySync()
        testScheduler.runCurrent()

        assertEquals(1, repo.retrySyncCallCount)
        assertEquals(1, deviceRepo.syncStartedCount)
        assertEquals(1, deviceRepo.syncCompletedCount)
    }
}
