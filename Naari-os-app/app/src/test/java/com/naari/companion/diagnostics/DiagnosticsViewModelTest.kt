package com.naari.companion.diagnostics

import com.naari.companion.ble.ReadinessState
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.ledger.LedgerSubmissionResult
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeDeviceStateRepository : DeviceStateRepository {
        override val deviceStatus = MutableStateFlow<DeviceStatus>(DeviceStatus.Ready)
        override val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.UpToDate())
        override val readinessState = MutableStateFlow(ReadinessState.Ready)
        override val batteryPercent = MutableStateFlow<Int?>(85)
        override val pairedDeviceAddress = MutableStateFlow<String?>("11:22:33:44:55:66")
        override val pairedDeviceName = MutableStateFlow<String?>("Naari Watch")
        override val lastSyncTimestampMs = MutableStateFlow<Long?>(1000L)

        override fun notifySyncStarted() {}
        override fun notifySyncCompleted(timestampMs: Long) {}
        override fun notifySyncFailed(reason: String) {}
    }

    private class FakeRequestLedgerRepository : RequestLedgerRepository {
        val entriesFlow = MutableStateFlow<List<RequestLedgerEntry>>(emptyList())
        override val recentEntries: Flow<List<RequestLedgerEntry>> = entriesFlow

        override suspend fun submitRequest(
            request: WatchRequest,
            deviceAddress: String,
            timestamp: Long
        ): LedgerSubmissionResult = error("Not needed")

        override suspend fun transitionStatus(
            entryId: Long,
            newStatus: RequestLedgerStatus,
            detail: String?,
            failureReason: String?,
            timestamp: Long
        ): Result<RequestLedgerEntry> = error("Not needed")

        override suspend fun getById(entryId: Long): RequestLedgerEntry? = null

        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> = emptyList()

        override suspend fun recoverInflightOnStartup(timestamp: Long): Int = 0
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_reflectsDeviceState() = runTest {
        val deviceRepo = FakeDeviceStateRepository()
        val ledgerRepo = FakeRequestLedgerRepository()

        val viewModel = DiagnosticsViewModel(deviceRepo, ledgerRepo)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("11:22:33:44:55:66", state.pairedAddress)
        assertEquals("Naari Watch", state.pairedName)
        assertEquals(85, state.batteryPercent)
        assertEquals(DeviceStatus.Ready, state.deviceStatus)
        assertEquals(1000L, state.lastSyncTimestampMs)
        assertEquals(0, state.recentLedgerEntries.size)

        job.cancel()
    }

    @Test
    fun ledgerEntries_emittedToUiState() = runTest {
        val deviceRepo = FakeDeviceStateRepository()
        val ledgerRepo = FakeRequestLedgerRepository()

        val sampleEntry = RequestLedgerEntry(
            id = 1L,
            watchRequestId = 42L,
            deviceAddress = "11:22:33:44:55:66",
            type = WatchRequestType.SMS,
            targetPhone = "+919876543210",
            status = RequestLedgerStatus.Delivered,
            receivedAt = 123456L,
            updatedAt = 123456L,
            detail = "Sent via SMS"
        )
        ledgerRepo.entriesFlow.value = listOf(sampleEntry)

        val viewModel = DiagnosticsViewModel(deviceRepo, ledgerRepo)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.recentLedgerEntries.size)
        assertEquals("+919876543210", state.recentLedgerEntries[0].targetPhone)
        assertEquals(RequestLedgerStatus.Delivered, state.recentLedgerEntries[0].status)

        job.cancel()
    }
}
