package com.naari.companion.actions

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ledger.LedgerSubmissionResult
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SmsExecutorTest {

    // region Fake Implementations

    private class FakeSmsTransport : SmsTransport {
        val sentCallbacks = mutableListOf<(partIndex: Int, resultCode: Int) -> Unit>()
        val deliveredCallbacks = mutableListOf<(partIndex: Int, resultCode: Int) -> Unit>()
        var recordedRequestId: Long? = null
        var recordedDestination: String? = null
        var recordedParts: List<String> = emptyList()
        var closedCount = 0
        var throwOnSend: Exception? = null

        override fun divideMessage(message: String): List<String> {
            return AndroidSmsTransport.fallbackDivideMessage(message)
        }

        override fun sendSmsWithCallbacks(
            requestId: Long,
            destinationAddress: String,
            parts: List<String>,
            onSentPart: (partIndex: Int, resultCode: Int) -> Unit,
            onDeliveredPart: (partIndex: Int, resultCode: Int) -> Unit
        ): AutoCloseable {
            throwOnSend?.let { throw it }
            recordedRequestId = requestId
            recordedDestination = destinationAddress
            recordedParts = parts
            sentCallbacks.add(onSentPart)
            deliveredCallbacks.add(onDeliveredPart)
            return AutoCloseable {
                closedCount++
            }
        }
    }

    private class FakeLedgerRepository : RequestLedgerRepository {
        val entries = mutableListOf<RequestLedgerEntry>()
        val statusHistory = mutableListOf<RequestLedgerStatus>()

        override val recentEntries: Flow<List<RequestLedgerEntry>> = flowOf(entries)

        override suspend fun submitRequest(
            request: WatchRequest,
            deviceAddress: String,
            timestamp: Long
        ): LedgerSubmissionResult {
            val entry = RequestLedgerEntry(
                id = (entries.size + 1).toLong(),
                deviceAddress = deviceAddress,
                watchRequestId = request.id,
                type = request.type,
                status = RequestLedgerStatus.Received,
                targetPhone = request.phone,
                messagePayload = request.message
            )
            entries.add(entry)
            statusHistory.add(entry.status)
            return LedgerSubmissionResult.Accepted(entry)
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
                detail = detail,
                failureReason = failureReason
            )
            entries[index] = updated
            statusHistory.add(newStatus)
            return Result.success(updated)
        }

        override suspend fun getById(entryId: Long): RequestLedgerEntry? {
            return entries.find { it.id == entryId }
        }

        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> {
            return entries.filter { it.watchRequestId == watchRequestId }
        }

        override suspend fun recoverInflightOnStartup(timestamp: Long): Int = 0
    }

    private class FakeBleSessionManager : BleSessionManager {
        val sentReplies = mutableListOf<PhoneReply>()

        override val connectionState: StateFlow<BleConnectionState> =
            MutableStateFlow(BleConnectionState.Ready)
        override val incomingRequests: SharedFlow<WatchRequest> = MutableSharedFlow()
        override val negotiatedMtu: StateFlow<Int> = MutableStateFlow(512)

        override fun connect(deviceAddress: String) {}

        override suspend fun sendReply(reply: PhoneReply): Result<Unit> {
            sentReplies.add(reply)
            return Result.success(Unit)
        }

        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)

        override fun disconnect() {}
    }

    // endregion

    // region Message Division Tests

    @Test
    fun fallbackDivideMessage_singlePartAscii_within160Chars() {
        val message = "Short alert message within single part limit."
        val parts = AndroidSmsTransport.fallbackDivideMessage(message)
        assertEquals(1, parts.size)
        assertEquals(message, parts[0])
    }

    @Test
    fun fallbackDivideMessage_exact160CharsAscii_singlePart() {
        val message = "A".repeat(160)
        val parts = AndroidSmsTransport.fallbackDivideMessage(message)
        assertEquals(1, parts.size)
        assertEquals(160, parts[0].length)
    }

    @Test
    fun fallbackDivideMessage_multiPartAscii_exceeding160Chars_splitsInto153ByteChunks() {
        val message = "A".repeat(161)
        val parts = AndroidSmsTransport.fallbackDivideMessage(message)
        assertEquals(2, parts.size)
        assertEquals(153, parts[0].length)
        assertEquals(8, parts[1].length)
    }

    @Test
    fun fallbackDivideMessage_singlePartUnicode_within70Chars() {
        val unicodeMessage = "आपातकालीन चेतावनी! तत्काल सहायता चाहिए।" // Hindi + Devanagari Unicode
        assertTrue(unicodeMessage.length <= 70)
        val parts = AndroidSmsTransport.fallbackDivideMessage(unicodeMessage)
        assertEquals(1, parts.size)
        assertEquals(unicodeMessage, parts[0])
    }

    @Test
    fun fallbackDivideMessage_multiPartUnicode_exceeding70Chars_splitsInto67CharChunks() {
        val unicodeChar = "\u0905" // Devanagari Letter A
        val message = unicodeChar.repeat(71)
        val parts = AndroidSmsTransport.fallbackDivideMessage(message)
        assertEquals(2, parts.size)
        assertEquals(67, parts[0].length)
        assertEquals(4, parts[1].length)
    }

    @Test
    fun fallbackDivideMessage_emptyMessage_returnsSingleEmptyString() {
        val parts = AndroidSmsTransport.fallbackDivideMessage("")
        assertEquals(listOf(""), parts)
    }

    // endregion

    // region Result Code Mapping Tests

    @Test
    fun mapSentResultCode_success_returnsNull() {
        assertNull(SmsResultMapper.mapSentResultCode(SmsResultMapper.RESULT_OK))
    }

    @Test
    fun mapSentResultCode_genericFailure_mappedTruthfully() {
        val reason = SmsResultMapper.mapSentResultCode(SmsResultMapper.ERROR_GENERIC_FAILURE_CODE)
        assertTrue(reason!!.contains("Generic carrier error", ignoreCase = true))
    }

    @Test
    fun mapSentResultCode_radioOff_mappedTruthfully() {
        val reason = SmsResultMapper.mapSentResultCode(SmsResultMapper.ERROR_RADIO_OFF_CODE)
        assertTrue(reason!!.contains("Airplane mode", ignoreCase = true))
    }

    @Test
    fun mapSentResultCode_nullPdu_mappedTruthfully() {
        val reason = SmsResultMapper.mapSentResultCode(SmsResultMapper.ERROR_NULL_PDU_CODE)
        assertTrue(reason!!.contains("Null PDU", ignoreCase = true))
    }

    @Test
    fun mapSentResultCode_noService_mappedTruthfully() {
        val reason = SmsResultMapper.mapSentResultCode(SmsResultMapper.ERROR_NO_SERVICE_CODE)
        assertTrue(reason!!.contains("No cellular service", ignoreCase = true))
    }

    @Test
    fun mapSentResultCode_limitExceeded_mappedTruthfully() {
        val reason = SmsResultMapper.mapSentResultCode(SmsResultMapper.ERROR_LIMIT_EXCEEDED_CODE)
        assertTrue(reason!!.contains("rate limit exceeded", ignoreCase = true))
    }

    @Test
    fun mapSentResultCode_unknownCode_mappedWithCodeNumber() {
        val reason = SmsResultMapper.mapSentResultCode(999)
        assertTrue(reason!!.contains("999"))
    }

    // endregion

    // region Executor Aggregation & Safety Policy Tests

    @Test
    fun sendSms_singlePart_fullSuccess_progressesReceivedToSentToDelivered() = runTest {
        val transport = FakeSmsTransport()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            deliveryTimeoutMs = 45_000L,
            sendTimeoutMs = 15_000L,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 101L, type = WatchRequestType.SMS, phone = "+919876543210", message = "Help"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        var result: SmsExecutionResult? = null
        val job = launch {
            result = executor.sendSms(101L, "+919876543210", "Help")
        }

        runCurrent()

        // Verify Step 1: Phone replied RECEIVED first, ledger updated to Executing
        assertEquals(PhoneReplyStatus.RECEIVED, ble.sentReplies[0].status)
        assertEquals(RequestLedgerStatus.Executing, ledger.entries.first().status)

        // Trigger carrier sent callback (success)
        transport.sentCallbacks.first().invoke(0, SmsResultMapper.RESULT_OK)
        runCurrent()

        // Verify Step 2: Phone replied SENT, ledger updated to Sent
        assertEquals(PhoneReplyStatus.SENT, ble.sentReplies[1].status)
        assertEquals(RequestLedgerStatus.Sent, ledger.entries.first().status)

        // Trigger carrier delivered callback (success)
        transport.deliveredCallbacks.first().invoke(0, SmsResultMapper.RESULT_OK)
        runCurrent()

        job.join()

        // Verify Step 3: Phone replied DELIVERED, ledger updated to Delivered
        assertEquals(PhoneReplyStatus.DELIVERED, ble.sentReplies[2].status)
        assertEquals(RequestLedgerStatus.Delivered, ledger.entries.first().status)
        assertTrue(result is SmsExecutionResult.Success)
        assertEquals(RequestLedgerStatus.Delivered, (result as SmsExecutionResult.Success).status)
        assertEquals(1, transport.closedCount) // Resources cleaned up
    }

    @Test
    fun sendSms_multiPart_partialSentFailure_failsImmediately() = runTest {
        val transport = FakeSmsTransport()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            deliveryTimeoutMs = 45_000L,
            sendTimeoutMs = 15_000L,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 102L, type = WatchRequestType.SMS, phone = "+919876543210", message = "A".repeat(200)),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        var result: SmsExecutionResult? = null
        val job = launch {
            result = executor.sendSms(102L, "+919876543210", "A".repeat(200))
        }

        runCurrent()

        // 2 parts expected
        assertEquals(2, transport.recordedParts.size)

        // Part 0 succeeds sent
        transport.sentCallbacks.first().invoke(0, SmsResultMapper.RESULT_OK)
        runCurrent()

        // Part 1 fails sent with NO_SERVICE
        transport.sentCallbacks.first().invoke(1, SmsResultMapper.ERROR_NO_SERVICE_CODE)
        runCurrent()

        job.join()

        // Fails: ledger status is Failed, BLE reply is FAILED
        assertEquals(RequestLedgerStatus.Failed, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.FAILED, ble.sentReplies.last().status)
        assertTrue(result is SmsExecutionResult.Failed)
        assertEquals(SmsResultMapper.ERROR_NO_SERVICE_CODE, (result as SmsExecutionResult.Failed).errorCode)
        assertEquals(1, transport.closedCount)
    }

    @Test
    fun sendSms_deliveryTimeout_retainsSentStatus_neverMarksFailed_neverRetries() = runTest {
        val transport = FakeSmsTransport()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            deliveryTimeoutMs = 45_000L,
            sendTimeoutMs = 15_000L,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 103L, type = WatchRequestType.SMS, phone = "+919876543210", message = "Test message"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        var result: SmsExecutionResult? = null
        val job = launch {
            result = executor.sendSms(103L, "+919876543210", "Test message")
        }

        runCurrent()

        // Part 0 succeeds sent
        transport.sentCallbacks.first().invoke(0, SmsResultMapper.RESULT_OK)
        runCurrent()

        // Delivered callback NEVER arrives; advance time by 45s
        advanceTimeBy(45_001L)
        runCurrent()

        job.join()

        // SAFETY VERIFICATION:
        // Must retain Sent status. Must NEVER transition to Failed.
        assertEquals(RequestLedgerStatus.Sent, ledger.entries.first().status)
        assertTrue(result is SmsExecutionResult.Success)
        assertEquals(RequestLedgerStatus.Sent, (result as SmsExecutionResult.Success).status)
        assertTrue((result as SmsExecutionResult.Success).detail.contains("timed out after 45000ms"))
        assertEquals(1, transport.closedCount)
    }

    @Test
    fun sendSms_invalidPhoneNumber_failsFastWithoutTransportCall() = runTest {
        val transport = FakeSmsTransport()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 104L, type = WatchRequestType.SMS, phone = "not-a-number", message = "Alert"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.sendSms(104L, "not-a-number", "Alert")

        assertTrue(result is SmsExecutionResult.Failed)
        assertEquals(RequestLedgerStatus.Failed, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.FAILED, ble.sentReplies.first().status)
        assertNull(transport.recordedRequestId) // Transport was never invoked
        assertEquals(0, transport.closedCount)
    }

    @Test
    fun sendSms_telephonyDispatchException_reportsFailedGracefully() = runTest {
        val transport = FakeSmsTransport().apply {
            throwOnSend = SecurityException("Permission SEND_SMS denied")
        }
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 105L, type = WatchRequestType.SMS, phone = "+919876543210", message = "Alert"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.sendSms(105L, "+919876543210", "Alert")

        assertTrue(result is SmsExecutionResult.Failed)
        assertTrue((result as SmsExecutionResult.Failed).reason.contains("Permission SEND_SMS denied"))
        assertEquals(RequestLedgerStatus.Failed, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.FAILED, ble.sentReplies.last().status)
    }

    @Test
    fun sendSms_sendTimeout_transitionsToFailed() = runTest {
        val transport = FakeSmsTransport()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            deliveryTimeoutMs = 45_000L,
            sendTimeoutMs = 15_000L,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 106L, type = WatchRequestType.SMS, phone = "+919876543210", message = "Alert"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        var result: SmsExecutionResult? = null
        val job = launch {
            result = executor.sendSms(106L, "+919876543210", "Alert")
        }

        runCurrent()

        // Sent callback never called; advance time past 15s
        advanceTimeBy(15_001L)
        runCurrent()

        job.join()

        assertTrue(result is SmsExecutionResult.Failed)
        assertTrue((result as SmsExecutionResult.Failed).reason.contains("timed out after 15000ms"))
        assertEquals(RequestLedgerStatus.Failed, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.FAILED, ble.sentReplies.last().status)
        assertEquals(1, transport.closedCount)
    }

    @Test
    fun sendSms_withCoordinates_includesLatAndLonInSentReply() = runTest {
        val transport = FakeSmsTransport()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            deliveryTimeoutMs = 45_000L,
            sendTimeoutMs = 15_000L,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 110L, type = WatchRequestType.LOCATION_SMS, phone = "+919876543210", message = "Help"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val job = launch {
            executor.sendSms(
                requestId = 110L,
                phone = "+919876543210",
                message = "Help",
                lat = 13.0827,
                lon = 80.2707
            )
        }

        runCurrent()

        // Trigger carrier sent callback (success)
        transport.sentCallbacks.first().invoke(0, SmsResultMapper.RESULT_OK)
        runCurrent()

        // Verify sent reply contains lat and lon
        val sentReply = ble.sentReplies.find { it.status == PhoneReplyStatus.SENT }
        assertNotNull(sentReply)
        assertEquals(13.0827, sentReply!!.lat)
        assertEquals(80.2707, sentReply.lon)

        transport.deliveredCallbacks.first().invoke(0, SmsResultMapper.RESULT_OK)
        runCurrent()
        job.join()
    }

    // endregion
}
