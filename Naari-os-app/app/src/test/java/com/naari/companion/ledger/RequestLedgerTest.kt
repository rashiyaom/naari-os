package com.naari.companion.ledger

import com.naari.companion.data.Converters
import com.naari.companion.data.Guardian
import com.naari.companion.data.IncidentLog
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestLedgerTest {

    private class InMemoryRequestLedgerDao : RequestLedgerDao {
        private val entries = mutableListOf<RequestLedgerEntry>()
        private var autoId = 1L
        private val flowTrigger = MutableStateFlow(0)

        override suspend fun insert(entry: RequestLedgerEntry): Long {
            val assignedId = if (entry.id == 0L) autoId++ else entry.id
            val persisted = entry.copy(id = assignedId)
            entries.removeAll { it.id == assignedId }
            entries.add(persisted)
            flowTrigger.value++
            return assignedId
        }

        override suspend fun update(entry: RequestLedgerEntry) {
            val index = entries.indexOfFirst { it.id == entry.id }
            if (index >= 0) {
                entries[index] = entry
                flowTrigger.value++
            }
        }

        override suspend fun updateStatus(
            id: Long,
            status: RequestLedgerStatus,
            detail: String?,
            failureReason: String?,
            updatedAt: Long
        ): Int {
            val index = entries.indexOfFirst { it.id == id }
            if (index >= 0) {
                val current = entries[index]
                entries[index] = current.copy(
                    status = status,
                    detail = detail ?: current.detail,
                    failureReason = failureReason ?: current.failureReason,
                    updatedAt = updatedAt
                )
                flowTrigger.value++
                return 1
            }
            return 0
        }

        override suspend fun getById(id: Long): RequestLedgerEntry? {
            return entries.find { it.id == id }
        }

        override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> {
            return entries.filter { it.watchRequestId == watchRequestId }
                .sortedByDescending { it.receivedAt }
        }

        override suspend fun findRecentByWatchRequestId(
            deviceAddress: String,
            watchRequestId: Long,
            sinceTimestamp: Long
        ): List<RequestLedgerEntry> {
            return entries.filter {
                it.deviceAddress.equals(deviceAddress, ignoreCase = true) &&
                    it.watchRequestId == watchRequestId &&
                    it.receivedAt >= sinceTimestamp
            }.sortedByDescending { it.receivedAt }
        }

        override suspend fun getRecentInflight(): List<RequestLedgerEntry> {
            return entries.filter { it.status == RequestLedgerStatus.Executing }
                .sortedBy { it.receivedAt }
        }

        override fun observeAllRecent(limit: Int): Flow<List<RequestLedgerEntry>> {
            return flowTrigger.map {
                entries.sortedByDescending { it.receivedAt }.take(limit)
            }
        }

        override suspend fun recoverInflightEntries(updatedAt: Long): Int {
            var count = 0
            for (i in entries.indices) {
                if (entries[i].status == RequestLedgerStatus.Executing) {
                    entries[i] = entries[i].copy(
                        status = RequestLedgerStatus.UnknownOutcome,
                        detail = "In-flight during app restart; outcome unknown",
                        updatedAt = updatedAt
                    )
                    count++
                }
            }
            if (count > 0) flowTrigger.value++
            return count
        }
    }

    private lateinit var dao: InMemoryRequestLedgerDao
    private lateinit var repository: DefaultRequestLedgerRepository

    @Before
    fun setup() {
        dao = InMemoryRequestLedgerDao()
        repository = DefaultRequestLedgerRepository(dao)
    }

    @Test
    fun submitRequest_persistsEntryInReceivedState() = runTest {
        val request = WatchRequest(
            id = 101L,
            type = WatchRequestType.SMS,
            phone = "+15555550101",
            message = "SOS help me"
        )
        val now = 1_000_000L

        val result = repository.submitRequest(request, "AA:BB:CC:DD:EE:FF", now)
        assertTrue(result is LedgerSubmissionResult.Accepted)

        val accepted = (result as LedgerSubmissionResult.Accepted).entry
        assertEquals(101L, accepted.watchRequestId)
        assertEquals("AA:BB:CC:DD:EE:FF", accepted.deviceAddress)
        assertEquals(RequestLedgerStatus.Received, accepted.status)
        assertEquals("+15555550101", accepted.targetPhone)
        assertEquals("SOS help me", accepted.messagePayload)
        assertEquals(now, accepted.receivedAt)

        val persisted = repository.getById(accepted.id)
        assertNotNull(persisted)
        assertEquals(RequestLedgerStatus.Received, persisted!!.status)
    }

    @Test
    fun deduplication_within60Seconds_rejectsDuplicate() = runTest {
        val request = WatchRequest(
            id = 202L,
            type = WatchRequestType.LOCATION_SMS,
            phone = "+15555550102",
            message = "Emergency"
        )
        val t0 = 100_000L

        val firstSubmission = repository.submitRequest(request, "AA:BB:CC:DD:EE:FF", t0)
        assertTrue(firstSubmission is LedgerSubmissionResult.Accepted)
        val firstEntry = (firstSubmission as LedgerSubmissionResult.Accepted).entry

        // 30 seconds later, duplicate arrives from same device
        val duplicateSubmission = repository.submitRequest(request, "AA:BB:CC:DD:EE:FF", t0 + 30_000L)
        assertTrue(duplicateSubmission is LedgerSubmissionResult.Duplicate)
        val duplicateEntry = (duplicateSubmission as LedgerSubmissionResult.Duplicate).existingEntry

        assertEquals(firstEntry.id, duplicateEntry.id)
        assertEquals(202L, duplicateEntry.watchRequestId)
    }

    @Test
    fun deduplication_after60Seconds_acceptsNewRequest() = runTest {
        val request = WatchRequest(
            id = 303L,
            type = WatchRequestType.SMS,
            phone = "+15555550103",
            message = "Help"
        )
        val t0 = 100_000L

        val firstSubmission = repository.submitRequest(request, "AA:BB:CC:DD:EE:FF", t0)
        assertTrue(firstSubmission is LedgerSubmissionResult.Accepted)

        // 61 seconds later (outside 60s window)
        val secondSubmission = repository.submitRequest(request, "AA:BB:CC:DD:EE:FF", t0 + 61_000L)
        assertTrue(secondSubmission is LedgerSubmissionResult.Accepted)

        val secondEntry = (secondSubmission as LedgerSubmissionResult.Accepted).entry
        val allEntries = repository.getByWatchRequestId(303L)
        assertEquals(2, allEntries.size)
    }

    @Test
    fun deduplication_fromDifferentDevice_acceptsBoth() = runTest {
        val request = WatchRequest(
            id = 404L,
            type = WatchRequestType.CALL,
            phone = "+15555550104"
        )
        val now = 100_000L

        val sub1 = repository.submitRequest(request, "11:22:33:44:55:66", now)
        val sub2 = repository.submitRequest(request, "99:88:77:66:55:44", now + 5_000L)

        assertTrue(sub1 is LedgerSubmissionResult.Accepted)
        assertTrue(sub2 is LedgerSubmissionResult.Accepted)
    }

    @Test
    fun stateMachineTransitions_validPaths() = runTest {
        val request = WatchRequest(id = 505L, type = WatchRequestType.SMS, phone = "+15555550105", message = "Hi")
        val accepted = (repository.submitRequest(request, "AA:BB:CC:DD:EE:FF") as LedgerSubmissionResult.Accepted).entry

        // Received -> Executing
        val executingRes = repository.transitionStatus(accepted.id, RequestLedgerStatus.Executing, detail = "Dispatching SMS")
        assertTrue(executingRes.isSuccess)
        assertEquals(RequestLedgerStatus.Executing, executingRes.getOrThrow().status)

        // Executing -> Sent
        val sentRes = repository.transitionStatus(accepted.id, RequestLedgerStatus.Sent, detail = "PDU sent to carrier")
        assertTrue(sentRes.isSuccess)
        assertEquals(RequestLedgerStatus.Sent, sentRes.getOrThrow().status)

        // Sent -> Delivered
        val deliveredRes = repository.transitionStatus(accepted.id, RequestLedgerStatus.Delivered, detail = "Delivered confirmed")
        assertTrue(deliveredRes.isSuccess)
        assertEquals(RequestLedgerStatus.Delivered, deliveredRes.getOrThrow().status)
    }

    @Test
    fun stateMachineTransitions_illegalTransitionsRejected() = runTest {
        val request = WatchRequest(id = 606L, type = WatchRequestType.SMS, phone = "+15555550106", message = "Test")
        val accepted = (repository.submitRequest(request, "AA:BB:CC:DD:EE:FF") as LedgerSubmissionResult.Accepted).entry

        // Cannot skip from Received directly to Delivered
        val illegalDirect = repository.transitionStatus(accepted.id, RequestLedgerStatus.Delivered)
        assertTrue(illegalDirect.isFailure)

        // Move to Executing, then Delivered
        repository.transitionStatus(accepted.id, RequestLedgerStatus.Executing)
        repository.transitionStatus(accepted.id, RequestLedgerStatus.Delivered)

        // Delivered is terminal: cannot transition back to Executing or Received
        val afterTerminal = repository.transitionStatus(accepted.id, RequestLedgerStatus.Executing)
        assertTrue(afterTerminal.isFailure)
    }

    @Test
    fun crashRecovery_transitionsInflightExecutingToUnknownOutcome() = runTest {
        val req1 = WatchRequest(id = 701L, type = WatchRequestType.SMS, phone = "+15555550107")
        val req2 = WatchRequest(id = 702L, type = WatchRequestType.LOCATION_SMS, phone = "+15555550108")
        val req3 = WatchRequest(id = 703L, type = WatchRequestType.CALL, phone = "+15555550109")

        val e1 = (repository.submitRequest(req1, "AA:BB:CC:DD:EE:FF") as LedgerSubmissionResult.Accepted).entry
        val e2 = (repository.submitRequest(req2, "AA:BB:CC:DD:EE:FF") as LedgerSubmissionResult.Accepted).entry
        val e3 = (repository.submitRequest(req3, "AA:BB:CC:DD:EE:FF") as LedgerSubmissionResult.Accepted).entry

        // e1 was Executing when app was killed
        repository.transitionStatus(e1.id, RequestLedgerStatus.Executing)

        // e2 was already Delivered
        repository.transitionStatus(e2.id, RequestLedgerStatus.Executing)
        repository.transitionStatus(e2.id, RequestLedgerStatus.Delivered)

        // e3 was Received (not yet initiated)

        // On app startup: recover in-flight entries
        val recoveredCount = repository.recoverInflightOnStartup(System.currentTimeMillis())
        assertEquals(1, recoveredCount)

        val updatedE1 = repository.getById(e1.id)
        assertNotNull(updatedE1)
        assertEquals(RequestLedgerStatus.UnknownOutcome, updatedE1!!.status)
        assertTrue(updatedE1.detail!!.contains("In-flight during app restart"))

        // e2 remained Delivered
        val updatedE2 = repository.getById(e2.id)
        assertEquals(RequestLedgerStatus.Delivered, updatedE2!!.status)

        // e3 remained Received
        val updatedE3 = repository.getById(e3.id)
        assertEquals(RequestLedgerStatus.Received, updatedE3!!.status)
    }

    @Test
    fun converters_convertAllEnumsCorrectly() {
        val converters = Converters()

        for (status in RequestLedgerStatus.entries) {
            val converted = converters.fromLedgerStatus(status)
            val restored = converters.toLedgerStatus(converted)
            assertEquals(status, restored)
        }

        // Unknown string falls back to UnknownOutcome
        val fallback = converters.toLedgerStatus("NON_EXISTENT_STATUS")
        assertEquals(RequestLedgerStatus.UnknownOutcome, fallback)

        for (type in WatchRequestType.entries) {
            val converted = converters.fromWatchRequestType(type)
            val restored = converters.toWatchRequestType(converted)
            assertEquals(type, restored)
        }
    }

    @Test
    fun guardianEntity_validatesSlotIndex0And1() {
        val g0 = Guardian(slotIndex = 0, name = "Primary Guardian", phone = "+15555550100")
        assertEquals(0, g0.slotIndex)

        val g1 = Guardian(slotIndex = 1, name = "Secondary Guardian", phone = "+15555550200")
        assertEquals(1, g1.slotIndex)

        try {
            Guardian(slotIndex = 2, name = "Invalid", phone = "+15555550300")
            org.junit.Assert.fail("Expected IllegalArgumentException for slot 2")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }
}
