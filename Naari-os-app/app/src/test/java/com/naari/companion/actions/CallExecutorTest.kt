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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallExecutorTest {

    // region Fakes

    private class FakeCallLauncher : CallLauncher {
        var hasPermission: Boolean = true
        var directCallResult: Boolean = true
        var dialerResult: Boolean = true

        val directCallsPlaced = mutableListOf<String>()
        val dialersOpened = mutableListOf<String>()

        override fun hasCallPermission(): Boolean = hasPermission

        override fun placeDirectCall(phoneNumber: String): Boolean {
            directCallsPlaced.add(phoneNumber)
            return directCallResult
        }

        override fun openDialer(phoneNumber: String): Boolean {
            dialersOpened.add(phoneNumber)
            return dialerResult
        }
    }

    private class FakeLedgerRepository : RequestLedgerRepository {
        val entries = mutableListOf<RequestLedgerEntry>()

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
            return Result.success(updated)
        }

        override suspend fun getById(entryId: Long): RequestLedgerEntry? = entries.find { it.id == entryId }

        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> =
            entries.filter { it.watchRequestId == watchRequestId }

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

    // region Emergency Number Detection Tests

    @Test
    fun isEmergencyNumber_recognizesIndiaEmergencyNumbers() {
        val detector = DefaultEmergencyNumberDetector()
        assertTrue("112 must be emergency", detector.isEmergencyNumber("112"))
        assertTrue("100 must be emergency", detector.isEmergencyNumber("100"))
        assertTrue("101 must be emergency", detector.isEmergencyNumber("101"))
        assertTrue("102 must be emergency", detector.isEmergencyNumber("102"))
        assertTrue("108 must be emergency", detector.isEmergencyNumber("108"))
        assertTrue("1091 (Women Helpline) must be emergency", detector.isEmergencyNumber("1091"))
        assertTrue("181 (Women in Distress) must be emergency", detector.isEmergencyNumber("181"))
        assertTrue("1098 (Childline) must be emergency", detector.isEmergencyNumber("1098"))
    }

    @Test
    fun isEmergencyNumber_recognizesInternationalEmergencyNumbers() {
        val detector = DefaultEmergencyNumberDetector()
        assertTrue("911 must be emergency", detector.isEmergencyNumber("911"))
        assertTrue("999 must be emergency", detector.isEmergencyNumber("999"))
        assertTrue("000 must be emergency", detector.isEmergencyNumber("000"))
        assertTrue("111 must be emergency", detector.isEmergencyNumber("111"))
    }

    @Test
    fun isEmergencyNumber_prefixedCountryCode_recognized() {
        val detector = DefaultEmergencyNumberDetector()
        assertTrue("+91112 recognized", detector.isEmergencyNumber("+91112"))
        assertTrue("+911091 recognized", detector.isEmergencyNumber("+911091"))
    }

    @Test
    fun isEmergencyNumber_ordinaryGuardianNumbers_areNotEmergency() {
        val detector = DefaultEmergencyNumberDetector()
        assertFalse("+919876543210 is ordinary", detector.isEmergencyNumber("+919876543210"))
        assertFalse("+14155552671 is ordinary", detector.isEmergencyNumber("+14155552671"))
        assertFalse("9876543210 is ordinary", detector.isEmergencyNumber("9876543210"))
        assertFalse("empty string is not emergency", detector.isEmergencyNumber(""))
    }

    // endregion

    // region Call Execution & Safety Policy Tests

    @Test
    fun executeCall_ordinaryNumber_permissionGranted_initiatesDirectCall() = runTest {
        val launcher = FakeCallLauncher()
        val detector = DefaultEmergencyNumberDetector()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = detector,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 201L, type = WatchRequestType.CALL, phone = "+919876543210"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeCall(201L, "+919876543210")

        // 1. Result is Calling
        assertTrue(result is CallExecutionResult.Calling)
        // 2. Direct call was placed
        assertEquals(listOf("+919876543210"), launcher.directCallsPlaced)
        // 3. Dialer was NOT opened
        assertTrue(launcher.dialersOpened.isEmpty())
        // 4. Ledger progressed to Calling
        assertEquals(RequestLedgerStatus.Calling, ledger.entries.first().status)
        // 5. BLE replies: received then calling
        assertEquals(PhoneReplyStatus.RECEIVED, ble.sentReplies[0].status)
        assertEquals(PhoneReplyStatus.CALLING, ble.sentReplies[1].status)
    }

    @Test
    fun executeCall_emergencyNumber112_opensDialer_returnsNeedsPhone_neverDirectCalls() = runTest {
        val launcher = FakeCallLauncher()
        val detector = DefaultEmergencyNumberDetector()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = detector,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 202L, type = WatchRequestType.CALL, phone = "112"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeCall(202L, "112")

        // SAFETY: Direct call MUST NOT be attempted
        assertTrue("Direct calls must be empty for 112", launcher.directCallsPlaced.isEmpty())
        // System dialer MUST be opened
        assertEquals(listOf("112"), launcher.dialersOpened)
        // Result MUST be NeedsPhone (never Calling or Placed)
        assertTrue(result is CallExecutionResult.NeedsPhone)
        // Ledger status is NeedsPhone
        assertEquals(RequestLedgerStatus.NeedsPhone, ledger.entries.first().status)
        // BLE reply status is NEEDS_PHONE
        assertEquals(PhoneReplyStatus.NEEDS_PHONE, ble.sentReplies.last().status)
    }

    @Test
    fun executeCall_emergencyNumber1091_opensDialer_returnsNeedsPhone() = runTest {
        val launcher = FakeCallLauncher()
        val detector = DefaultEmergencyNumberDetector()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = detector,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 203L, type = WatchRequestType.CALL, phone = "1091"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeCall(203L, "1091")

        assertTrue(launcher.directCallsPlaced.isEmpty())
        assertEquals(listOf("1091"), launcher.dialersOpened)
        assertTrue(result is CallExecutionResult.NeedsPhone)
        assertEquals(RequestLedgerStatus.NeedsPhone, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.NEEDS_PHONE, ble.sentReplies.last().status)
    }

    @Test
    fun executeCall_emergencyNumber911_opensDialer_returnsNeedsPhone() = runTest {
        val launcher = FakeCallLauncher()
        val detector = DefaultEmergencyNumberDetector()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = detector,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 204L, type = WatchRequestType.CALL, phone = "911"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeCall(204L, "911")

        assertTrue(launcher.directCallsPlaced.isEmpty())
        assertEquals(listOf("911"), launcher.dialersOpened)
        assertTrue(result is CallExecutionResult.NeedsPhone)
        assertEquals(RequestLedgerStatus.NeedsPhone, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.NEEDS_PHONE, ble.sentReplies.last().status)
    }

    @Test
    fun executeCall_permissionDenied_opensDialer_returnsNeedsPhone() = runTest {
        val launcher = FakeCallLauncher().apply {
            hasPermission = false
        }
        val detector = DefaultEmergencyNumberDetector()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = detector,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 205L, type = WatchRequestType.CALL, phone = "+919876543210"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeCall(205L, "+919876543210")

        assertTrue(launcher.directCallsPlaced.isEmpty())
        assertEquals(listOf("+919876543210"), launcher.dialersOpened)
        assertTrue(result is CallExecutionResult.NeedsPhone)
        assertEquals(RequestLedgerStatus.NeedsPhone, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.NEEDS_PHONE, ble.sentReplies.last().status)
    }

    @Test
    fun executeCall_directPlacementFails_fallsBackToDialer_returnsNeedsPhone() = runTest {
        val launcher = FakeCallLauncher().apply {
            directCallResult = false
        }
        val detector = DefaultEmergencyNumberDetector()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = detector,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 206L, type = WatchRequestType.CALL, phone = "+919876543210"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeCall(206L, "+919876543210")

        // Direct call was attempted but failed
        assertEquals(listOf("+919876543210"), launcher.directCallsPlaced)
        // System dialer opened as fallback
        assertEquals(listOf("+919876543210"), launcher.dialersOpened)
        assertTrue(result is CallExecutionResult.NeedsPhone)
        assertEquals(RequestLedgerStatus.NeedsPhone, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.NEEDS_PHONE, ble.sentReplies.last().status)
    }

    @Test
    fun executeCall_invalidPhoneNumber_failsFast() = runTest {
        val launcher = FakeCallLauncher()
        val detector = DefaultEmergencyNumberDetector()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        val executor = DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = detector,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 207L, type = WatchRequestType.CALL, phone = "not-a-number"),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeCall(207L, "not-a-number")

        assertTrue(result is CallExecutionResult.Failed)
        assertTrue(launcher.directCallsPlaced.isEmpty())
        assertTrue(launcher.dialersOpened.isEmpty())
        assertEquals(RequestLedgerStatus.Failed, ledger.entries.first().status)
        assertEquals(PhoneReplyStatus.FAILED, ble.sentReplies.first().status)
    }

    // endregion
}
