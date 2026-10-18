package com.naari.companion.actions

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.history.RedactionUtils
import com.naari.companion.ledger.LedgerSubmissionResult
import com.naari.companion.ledger.RequestLedgerDao
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.location.LocationExecutor
import com.naari.companion.location.LocationFix
import com.naari.companion.location.LocationResult
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.PhoneReplyStatus
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActionDispatcherTest {

    // region Fake Implementations

    private class FakeBleSessionManager : BleSessionManager {
        val requestFlow = MutableSharedFlow<WatchRequest>()
        val sentReplies = mutableListOf<PhoneReply>()

        override val connectionState: StateFlow<BleConnectionState> =
            MutableStateFlow(BleConnectionState.Ready)
        override val incomingRequests: SharedFlow<WatchRequest> = requestFlow
        override val negotiatedMtu: StateFlow<Int> = MutableStateFlow(512)

        override fun connect(deviceAddress: String) {}

        override suspend fun sendReply(reply: PhoneReply): Result<Unit> {
            sentReplies.add(reply)
            return Result.success(Unit)
        }

        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)

        override fun disconnect() {}
    }

    private class FakeRequestLedgerRepository(
        private val deduplicationWindowMs: Long = 60_000L
    ) : RequestLedgerRepository {
        val entries = mutableListOf<RequestLedgerEntry>()

        override val recentEntries: Flow<List<RequestLedgerEntry>> = flowOf(entries)

        override suspend fun submitRequest(
            request: WatchRequest,
            deviceAddress: String,
            timestamp: Long
        ): LedgerSubmissionResult {
            // Check for duplicate within 60s window
            val cutoff = timestamp - deduplicationWindowMs
            val duplicate = entries.firstOrNull {
                it.deviceAddress == deviceAddress &&
                        it.watchRequestId == request.id &&
                        it.receivedAt >= cutoff
            }

            if (duplicate != null) {
                return LedgerSubmissionResult.Duplicate(duplicate)
            }

            val newEntry = RequestLedgerEntry(
                id = (entries.size + 1).toLong(),
                deviceAddress = deviceAddress,
                watchRequestId = request.id,
                type = request.type,
                status = RequestLedgerStatus.Received,
                targetPhone = request.phone,
                messagePayload = request.message,
                receivedAt = timestamp,
                updatedAt = timestamp
            )
            entries.add(newEntry)
            return LedgerSubmissionResult.Accepted(newEntry)
        }

        override suspend fun transitionStatus(
            entryId: Long,
            newStatus: RequestLedgerStatus,
            detail: String?,
            failureReason: String?,
            timestamp: Long
        ): Result<RequestLedgerEntry> {
            val index = entries.indexOfFirst { it.id == entryId }
            if (index == -1) return Result.failure(IllegalArgumentException("Entry not found"))
            val updated = entries[index].copy(
                status = newStatus,
                detail = detail ?: entries[index].detail,
                failureReason = failureReason ?: entries[index].failureReason,
                updatedAt = timestamp
            )
            entries[index] = updated
            return Result.success(updated)
        }

        override suspend fun getById(entryId: Long): RequestLedgerEntry? = entries.find { it.id == entryId }

        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> =
            entries.filter { it.watchRequestId == watchRequestId }

        override suspend fun recoverInflightOnStartup(timestamp: Long): Int = 0
    }

    private class FakeSmsExecutor : SmsExecutor {
        val dispatchedRequests = mutableListOf<Triple<Long, String, String>>()

        override suspend fun sendSms(
            requestId: Long,
            phone: String,
            message: String,
            lat: Double?,
            lon: Double?
        ): SmsExecutionResult {
            dispatchedRequests.add(Triple(requestId, phone, message))
            return SmsExecutionResult.Success(
                status = RequestLedgerStatus.Delivered,
                detail = "SMS delivered"
            )
        }
    }

    private class FakeCallExecutor : CallExecutor {
        val dispatchedCalls = mutableListOf<Pair<Long, String>>()

        override suspend fun executeCall(
            requestId: Long,
            phoneNumber: String
        ): CallExecutionResult {
            dispatchedCalls.add(Pair(requestId, phoneNumber))
            return CallExecutionResult.Calling("Direct call placed")
        }
    }

    private class FakeLocationExecutor : LocationExecutor {
        val locationRequests = mutableListOf<Long>()
        val locationSmsRequests = mutableListOf<Triple<Long, String, String?>>()

        override suspend fun executeLocation(requestId: Long): LocationResult {
            locationRequests.add(requestId)
            return LocationResult.Fresh(LocationFix(12.9716, 77.5946, 10f))
        }

        override suspend fun executeLocationSms(
            requestId: Long,
            phoneNumber: String,
            baseMessage: String?
        ): SmsExecutionResult {
            locationSmsRequests.add(Triple(requestId, phoneNumber, baseMessage))
            return SmsExecutionResult.Success(
                status = RequestLedgerStatus.Delivered,
                detail = "Location SMS delivered"
            )
        }
    }

    // endregion

    // region Routing & Dispatching Tests

    @Test
    fun dispatchRequest_smsType_routesToSmsExecutor() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        val request = WatchRequest(
            id = 401L,
            type = WatchRequestType.SMS,
            phone = "+919876543210",
            message = "Emergency message"
        )

        val result = dispatcher.dispatchRequest(request, "AA:BB:CC:DD:EE:FF")

        assertTrue(result.isSuccess)
        assertEquals(1, sms.dispatchedRequests.size)
        assertEquals(401L, sms.dispatchedRequests.first().first)
        assertEquals("+919876543210", sms.dispatchedRequests.first().second)
        assertEquals("Emergency message", sms.dispatchedRequests.first().third)
        assertTrue(call.dispatchedCalls.isEmpty())
        assertTrue(location.locationRequests.isEmpty())
    }

    @Test
    fun dispatchRequest_callType_routesToCallExecutor() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        val request = WatchRequest(
            id = 402L,
            type = WatchRequestType.CALL,
            phone = "+919876543210"
        )

        val result = dispatcher.dispatchRequest(request, "AA:BB:CC:DD:EE:FF")

        assertTrue(result.isSuccess)
        assertEquals(1, call.dispatchedCalls.size)
        assertEquals(402L, call.dispatchedCalls.first().first)
        assertEquals("+919876543210", call.dispatchedCalls.first().second)
        assertTrue(sms.dispatchedRequests.isEmpty())
    }

    @Test
    fun dispatchRequest_locationType_routesToLocationExecutor() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        val request = WatchRequest(
            id = 403L,
            type = WatchRequestType.LOCATION
        )

        val result = dispatcher.dispatchRequest(request, "AA:BB:CC:DD:EE:FF")

        assertTrue(result.isSuccess)
        assertEquals(listOf(403L), location.locationRequests)
    }

    @Test
    fun dispatchRequest_locationSmsType_routesToLocationExecutor() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        val request = WatchRequest(
            id = 404L,
            type = WatchRequestType.LOCATION_SMS,
            phone = "+919876543210",
            message = "Help needed"
        )

        val result = dispatcher.dispatchRequest(request, "AA:BB:CC:DD:EE:FF")

        assertTrue(result.isSuccess)
        assertEquals(1, location.locationSmsRequests.size)
        assertEquals(404L, location.locationSmsRequests.first().first)
        assertEquals("+919876543210", location.locationSmsRequests.first().second)
        assertEquals("Help needed", location.locationSmsRequests.first().third)
    }

    // endregion

    // region Deduplication & Reconnect Safety Tests (Gate Requirement)

    @Test
    fun dispatchRequest_duplicateNotificationWithin60s_producesNoSecondSend() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        val request = WatchRequest(
            id = 405L,
            type = WatchRequestType.SMS,
            phone = "+919876543210",
            message = "Emergency"
        )

        // 1. First dispatch: Accepted and executed
        val firstResult = dispatcher.dispatchRequest(request, "AA:BB:CC:DD:EE:FF")
        assertTrue(firstResult.isSuccess)
        assertEquals(1, sms.dispatchedRequests.size)

        // Simulate ledger update to Delivered
        ledger.transitionStatus(ledger.entries.first().id, RequestLedgerStatus.Delivered)

        // 2. Second dispatch: Duplicate re-notification within 60s window
        val secondResult = dispatcher.dispatchRequest(request, "AA:BB:CC:DD:EE:FF")
        assertTrue(secondResult.isSuccess)

        // CRITICAL GATE VERIFICATION: Zero second send!
        assertEquals("SMS must not be dispatched a second time", 1, sms.dispatchedRequests.size)

        // Re-transmits the last known reply status on BLE
        val lastReply = ble.sentReplies.lastOrNull()
        assertEquals(405L, lastReply?.id)
        assertEquals(PhoneReplyStatus.DELIVERED, lastReply?.status)
    }

    @Test
    fun startListening_incomingRequestFlow_deduplicatesOnReconnect() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        val job = dispatcher.startListening(this)
        runCurrent() // Allow collector to start subscribing

        val request = WatchRequest(
            id = 406L,
            type = WatchRequestType.CALL,
            phone = "+919876543210"
        )

        // Watch sends notification
        ble.requestFlow.emit(request)
        runCurrent()

        assertEquals(1, call.dispatchedCalls.size)

        // Connection toggled / reconnected, watch re-emits notification with same ID
        ble.requestFlow.emit(request)
        runCurrent()

        // ZERO second call placed!
        assertEquals(1, call.dispatchedCalls.size)

        job.cancel()
    }

    // endregion

    // region Manual Retry Safety Tests

    @Test
    fun retryRequest_failedAction_reExecutesWithUserConfirmation() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        // Seed a failed entry
        val submitResult = ledger.submitRequest(
            WatchRequest(id = 407L, type = WatchRequestType.SMS, phone = "+919876543210", message = "Help"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )
        val entry = (submitResult as LedgerSubmissionResult.Accepted).entry
        ledger.transitionStatus(entry.id, RequestLedgerStatus.Failed, failureReason = "Cellular radio off")

        // User manually confirms retry
        val retryResult = dispatcher.retryRequest(entry.id)

        assertTrue(retryResult.isSuccess)
        assertEquals(1, sms.dispatchedRequests.size)
        assertEquals(407L, sms.dispatchedRequests.first().first)
        assertEquals(RequestLedgerStatus.Executing, ledger.entries.first().status)
    }

    @Test
    fun retryRequest_alreadyDeliveredAction_rejectedWithException() = runTest {
        val ble = FakeBleSessionManager()
        val ledger = FakeRequestLedgerRepository()
        val sms = FakeSmsExecutor()
        val call = FakeCallExecutor()
        val location = FakeLocationExecutor()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val dispatcher = DefaultActionDispatcher(
            bleSessionManager = ble,
            ledgerRepository = ledger,
            smsExecutor = sms,
            callExecutor = call,
            locationExecutor = location,
            ioDispatcher = testDispatcher
        )

        // Seed an already delivered entry
        val submitResult = ledger.submitRequest(
            WatchRequest(id = 408L, type = WatchRequestType.SMS, phone = "+919876543210", message = "Help"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )
        val entry = (submitResult as LedgerSubmissionResult.Accepted).entry
        ledger.transitionStatus(entry.id, RequestLedgerStatus.Delivered)

        // User attempts retry on delivered action
        val retryResult = dispatcher.retryRequest(entry.id)

        // SAFETY: Must reject retrying already delivered actions!
        assertTrue(retryResult.isFailure)
        assertTrue(retryResult.exceptionOrNull() is IllegalStateException)
        assertTrue(sms.dispatchedRequests.isEmpty())
    }

    // endregion

    // region Privacy Redaction Tests

    @Test
    fun redactPhoneNumber_masksSensitiveDigitsTruthfully() {
        // International with country code
        assertEquals("+91 98*** **210", RedactionUtils.redactPhoneNumber("+919876543210"))
        assertEquals("+14 15*** **671", RedactionUtils.redactPhoneNumber("+14155552671"))

        // Standard 10 digit number
        assertEquals("98*** **210", RedactionUtils.redactPhoneNumber("9876543210"))

        // Emergency short codes: not masked so user knows which emergency service was dialed
        assertEquals("112", RedactionUtils.redactPhoneNumber("112"))
        assertEquals("911", RedactionUtils.redactPhoneNumber("911"))
        assertEquals("1091", RedactionUtils.redactPhoneNumber("1091"))

        // Null / Blank
        assertEquals("Unknown", RedactionUtils.redactPhoneNumber(null))
        assertEquals("Unknown", RedactionUtils.redactPhoneNumber(""))
    }

    @Test
    fun redactMessageSnippet_truncatesOverflow() {
        val short = "Safe alert"
        assertEquals("Safe alert", RedactionUtils.redactMessageSnippet(short))

        val long = "This is a very long safety alert message that contains excess text"
        val snippet = RedactionUtils.redactMessageSnippet(long, maxLength = 30)
        assertEquals("This is a very long safety ale…", snippet)
    }

    // endregion
}
