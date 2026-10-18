package com.naari.companion.history

import com.naari.companion.actions.ActionDispatcher
import com.naari.companion.ledger.LedgerSubmissionResult
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeLedgerRepository : RequestLedgerRepository {
        val entriesFlow = MutableStateFlow<List<RequestLedgerEntry>>(emptyList())
        override val recentEntries: Flow<List<RequestLedgerEntry>> = entriesFlow.asStateFlow()

        override suspend fun submitRequest(
            request: WatchRequest,
            deviceAddress: String,
            timestamp: Long
        ): LedgerSubmissionResult {
            val entry = RequestLedgerEntry(
                id = 1L,
                deviceAddress = deviceAddress,
                watchRequestId = request.id,
                type = request.type,
                status = RequestLedgerStatus.Received,
                receivedAt = timestamp,
                updatedAt = timestamp
            )
            entriesFlow.value = listOf(entry)
            return LedgerSubmissionResult.Accepted(entry)
        }

        override suspend fun transitionStatus(
            entryId: Long,
            newStatus: RequestLedgerStatus,
            detail: String?,
            failureReason: String?,
            timestamp: Long
        ): Result<RequestLedgerEntry> {
            val list = entriesFlow.value.toMutableList()
            val index = list.indexOfFirst { it.id == entryId }
            if (index >= 0) {
                val updated = list[index].copy(
                    status = newStatus,
                    detail = detail,
                    failureReason = failureReason,
                    updatedAt = timestamp
                )
                list[index] = updated
                entriesFlow.value = list
                return Result.success(updated)
            }
            return Result.failure(NoSuchElementException())
        }

        override suspend fun getById(entryId: Long): RequestLedgerEntry? =
            entriesFlow.value.find { it.id == entryId }

        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> =
            entriesFlow.value.filter { it.watchRequestId == watchRequestId }

        override suspend fun recoverInflightOnStartup(timestamp: Long): Int = 0
    }

    private class FakeActionDispatcher : ActionDispatcher {
        var retriedEntryId: Long? = null
        var shouldRetrySucceed: Boolean = true

        override fun startListening(scope: CoroutineScope): Job = Job()
        override suspend fun dispatchRequest(request: WatchRequest, deviceAddress: String): Result<Unit> = Result.success(Unit)

        override suspend fun retryRequest(entryId: Long): Result<Unit> {
            retriedEntryId = entryId
            return if (shouldRetrySucceed) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Simulated carrier reject"))
            }
        }
    }

    @Test
    fun initialState_hasEmptyEntries_andNoPendingRetry() = runTest {
        val ledger = FakeLedgerRepository()
        val dispatcher = FakeActionDispatcher()
        val viewModel = HistoryViewModel(ledger, dispatcher)
        testScheduler.runCurrent()

        assertTrue(viewModel.entries.value.isEmpty())
        assertNull(viewModel.pendingRetryEntry.value)
        assertFalse(viewModel.isRetrying.value)
        assertNull(viewModel.retryFeedback.value)
    }

    @Test
    fun onRetryRequested_onlyAllowsFailedOrUnknownOutcome() = runTest {
        val ledger = FakeLedgerRepository()
        val dispatcher = FakeActionDispatcher()
        val viewModel = HistoryViewModel(ledger, dispatcher)
        testScheduler.runCurrent()

        val deliveredEntry = RequestLedgerEntry(
            id = 1L,
            deviceAddress = "AA:BB:CC",
            watchRequestId = 101L,
            type = WatchRequestType.SMS,
            status = RequestLedgerStatus.Delivered
        )

        // Attempt retry on delivered action: ignored
        viewModel.onRetryRequested(deliveredEntry)
        testScheduler.runCurrent()
        assertNull(viewModel.pendingRetryEntry.value)

        // Attempt retry on failed action: accepted for confirmation
        val failedEntry = RequestLedgerEntry(
            id = 2L,
            deviceAddress = "AA:BB:CC",
            watchRequestId = 102L,
            type = WatchRequestType.CALL,
            status = RequestLedgerStatus.Failed
        )
        viewModel.onRetryRequested(failedEntry)
        testScheduler.runCurrent()
        assertEquals(2L, viewModel.pendingRetryEntry.value?.id)

        // Dismiss dialog
        viewModel.dismissRetryDialog()
        testScheduler.runCurrent()
        assertNull(viewModel.pendingRetryEntry.value)

        // Attempt retry on UnknownOutcome action: accepted for confirmation
        val unknownEntry = RequestLedgerEntry(
            id = 3L,
            deviceAddress = "AA:BB:CC",
            watchRequestId = 103L,
            type = WatchRequestType.LOCATION_SMS,
            status = RequestLedgerStatus.UnknownOutcome
        )
        viewModel.onRetryRequested(unknownEntry)
        testScheduler.runCurrent()
        assertEquals(3L, viewModel.pendingRetryEntry.value?.id)
    }

    @Test
    fun confirmRetry_dispatchesRetry_andSetsFeedback() = runTest {
        val ledger = FakeLedgerRepository()
        val dispatcher = FakeActionDispatcher()
        val viewModel = HistoryViewModel(ledger, dispatcher)
        testScheduler.runCurrent()

        val failedEntry = RequestLedgerEntry(
            id = 5L,
            deviceAddress = "AA:BB:CC",
            watchRequestId = 105L,
            type = WatchRequestType.SMS,
            status = RequestLedgerStatus.Failed
        )

        viewModel.onRetryRequested(failedEntry)
        testScheduler.runCurrent()

        viewModel.confirmRetry()
        testScheduler.runCurrent()

        assertEquals(5L, dispatcher.retriedEntryId)
        assertNull(viewModel.pendingRetryEntry.value)
        assertEquals("Action retry dispatched successfully", viewModel.retryFeedback.value)

        viewModel.clearFeedback()
        testScheduler.runCurrent()
        assertNull(viewModel.retryFeedback.value)
    }

    @Test
    fun confirmRetry_whenFailed_setsErrorFeedback() = runTest {
        val ledger = FakeLedgerRepository()
        val dispatcher = FakeActionDispatcher()
        dispatcher.shouldRetrySucceed = false
        val viewModel = HistoryViewModel(ledger, dispatcher)
        testScheduler.runCurrent()

        val failedEntry = RequestLedgerEntry(
            id = 6L,
            deviceAddress = "AA:BB:CC",
            watchRequestId = 106L,
            type = WatchRequestType.CALL,
            status = RequestLedgerStatus.Failed
        )

        viewModel.onRetryRequested(failedEntry)
        testScheduler.runCurrent()

        viewModel.confirmRetry()
        testScheduler.runCurrent()

        assertEquals(6L, dispatcher.retriedEntryId)
        assertTrue(viewModel.retryFeedback.value!!.contains("Retry failed"))
    }
}
