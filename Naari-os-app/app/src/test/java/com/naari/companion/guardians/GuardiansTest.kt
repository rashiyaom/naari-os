package com.naari.companion.guardians

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.data.Guardian
import com.naari.companion.data.GuardiansDao
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.ProtocolV1Codec
import com.naari.companion.protocol.WatchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class GuardiansTest {

    private class InMemoryGuardiansDao : GuardiansDao {
        private val storage = mutableMapOf<Int, Guardian>()
        private val slot0Flow = MutableStateFlow<Guardian?>(null)
        private val slot1Flow = MutableStateFlow<Guardian?>(null)
        private val allFlow = MutableStateFlow<List<Guardian>>(emptyList())

        override suspend fun getSlot(slotIndex: Int): Guardian? = storage[slotIndex]

        override fun observeSlot(slotIndex: Int): Flow<Guardian?> =
            if (slotIndex == 0) slot0Flow else slot1Flow

        override suspend fun getAll(): List<Guardian> = storage.values.sortedBy { it.slotIndex }

        override fun observeAll(): Flow<List<Guardian>> = allFlow

        override suspend fun upsert(guardian: Guardian) {
            storage[guardian.slotIndex] = guardian
            notifyFlows()
        }

        override suspend fun clearSlot(slotIndex: Int): Int {
            val removed = storage.remove(slotIndex) != null
            notifyFlows()
            return if (removed) 1 else 0
        }

        override suspend fun clearAll(): Int {
            val count = storage.size
            storage.clear()
            notifyFlows()
            return count
        }

        private fun notifyFlows() {
            slot0Flow.value = storage[0]
            slot1Flow.value = storage[1]
            allFlow.value = storage.values.sortedBy { it.slotIndex }
        }
    }

    private class FakeBleSessionManager : BleSessionManager {
        val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
        override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

        override val incomingRequests: SharedFlow<WatchRequest> = MutableSharedFlow()
        override val negotiatedMtu: StateFlow<Int> = MutableStateFlow(512)

        var lastSyncedProfile: PhoneProfileMessage? = null
        var shouldSyncSucceed = true

        override fun connect(deviceAddress: String) {
            _connectionState.value = BleConnectionState.Ready
        }

        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)

        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> {
            lastSyncedProfile = profile
            return if (shouldSyncSucceed) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Simulated BLE write failure"))
            }
        }

        override fun disconnect() {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }

    @Test
    fun phoneValidation_acceptsValidAndRejectsInvalidNumbers() {
        // Valid phone numbers (3 to 20 digits, optional leading '+')
        assertTrue(ProtocolV1Codec.validatePhoneNumber("+15555550101"))
        assertTrue(ProtocolV1Codec.validatePhoneNumber("9112345678"))
        assertTrue(ProtocolV1Codec.validatePhoneNumber("+919876543210"))
        assertTrue(ProtocolV1Codec.validatePhoneNumber("123"))
        assertTrue(ProtocolV1Codec.validatePhoneNumber("+12345678901234567890")) // 20 digits
        assertTrue(ProtocolV1Codec.validatePhoneNumber("")) // empty is valid to clear slot

        // Invalid numbers
        assertFalse(ProtocolV1Codec.validatePhoneNumber("12")) // < 3 digits
        assertFalse(ProtocolV1Codec.validatePhoneNumber("+")) // only '+'
        assertFalse(ProtocolV1Codec.validatePhoneNumber("+12")) // < 3 digits
        assertFalse(ProtocolV1Codec.validatePhoneNumber("abc")) // non-numeric
        assertFalse(ProtocolV1Codec.validatePhoneNumber("+1555abc0101")) // mixed
        assertFalse(ProtocolV1Codec.validatePhoneNumber("+123456789012345678901")) // 21 digits > 20
    }

    @Test
    fun nameUtf8ByteTruncation_preservesShortAndTruncatesLongMultiByteStrings() {
        // Short name fits
        val shortName = "Maya"
        assertEquals("Maya", ProtocolV1Codec.truncateNameUtf8(shortName))

        // Exact 32 bytes
        val exact32 = "12345678901234567890123456789012"
        assertEquals(exact32, ProtocolV1Codec.truncateNameUtf8(exact32))

        // Long name exceeds 32 bytes
        val longName = "A".repeat(50)
        val truncatedAscii = ProtocolV1Codec.truncateNameUtf8(longName)
        assertEquals(32, truncatedAscii.toByteArray(StandardCharsets.UTF_8).size)

        // Multibyte characters (e.g. Devanagari "माँ" where each char is 3 UTF-8 bytes)
        val devanagari = "माँ ".repeat(15)
        val truncatedDevanagari = ProtocolV1Codec.truncateNameUtf8(devanagari)
        val byteSize = truncatedDevanagari.toByteArray(StandardCharsets.UTF_8).size
        assertTrue("Byte size $byteSize must be <= 32", byteSize <= 32)
        // Confirm it doesn't end in broken bytes
        assertNotNull(String(truncatedDevanagari.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
    }

    @Test
    fun profileJsonConstruction_formatsSlotsCorrectly() {
        val json = ProtocolV1Codec.buildProfileJson(
            name0 = "Primary Contact",
            phone0 = "+15555550101",
            name1 = "Secondary Contact",
            phone1 = "+15555550102"
        )

        assertTrue(json.contains(""""v":1"""))
        assertTrue(json.contains(""""op":"profile""""))
        assertTrue(json.contains(""""name0":"Primary Contact""""))
        assertTrue(json.contains(""""phone0":"+15555550101""""))
        assertTrue(json.contains(""""name1":"Secondary Contact""""))
        assertTrue(json.contains(""""phone1":"+15555550102""""))

        val parsed = ProtocolV1Codec.parseProfileJson(json)
        assertEquals("Primary Contact", parsed.name0)
        assertEquals("+15555550101", parsed.phone0)
        assertEquals("Secondary Contact", parsed.name1)
        assertEquals("+15555550102", parsed.phone1)
    }

    @Test
    fun freshRepository_neverSilentlySeedsFakeGuardians() = runTest {
        val dao = InMemoryGuardiansDao()
        val repo = DefaultGuardiansRepository(dao)

        assertNull(repo.slot0.value)
        assertNull(repo.slot1.value)
        assertEquals(0, dao.getAll().size)
        assertEquals(GuardianSyncState.Empty, repo.syncState.value)
        assertFalse(repo.isPendingSync.value)
    }

    @Test
    fun pendingSyncFlag_truthfulStateWhenWatchDisconnectedAndAutoSyncsOnConnect() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dao = InMemoryGuardiansDao()
        val fakeBle = FakeBleSessionManager()
        val repo = DefaultGuardiansRepository(
            dao = dao,
            bleSessionManager = fakeBle,
            dispatcher = testDispatcher,
            externalScope = repoScope
        )

        try {
            // 1. Watch is disconnected: Saving guardian should save to DB, set isPendingSync = true
            fakeBle._connectionState.value = BleConnectionState.Disconnected
            val saveResult = repo.saveGuardian(0, "Alice", "+15555550101")
            assertTrue(saveResult.isSuccess)

            // Database has Alice in slot 0
            val stored = dao.getSlot(0)
            assertNotNull(stored)
            assertEquals("Alice", stored!!.name)
            assertEquals("+15555550101", stored.phone)

            // Sync state must truthfully state: pending watch sync
            assertEquals(GuardianSyncState.PendingSync, repo.syncState.value)
            assertTrue(repo.isPendingSync.value)
            assertEquals("Saved on phone, will sync when watch connects", repo.syncState.value.displayText)

            // 2. Watch connects and enters Ready: repo automatically syncs pending profile
            fakeBle._connectionState.value = BleConnectionState.Ready
            testScheduler.runCurrent()

            assertEquals(GuardianSyncState.SyncedToWatch, repo.syncState.value)
            assertFalse(repo.isPendingSync.value)
            assertEquals("Saved to watch", repo.syncState.value.displayText)

            assertNotNull(fakeBle.lastSyncedProfile)
            assertEquals("Alice", fakeBle.lastSyncedProfile!!.name0)
            assertEquals("+15555550101", fakeBle.lastSyncedProfile!!.phone0)
        } finally {
            repoScope.cancel()
        }
    }

    @Test
    fun clearSlot_setsSlotToEmptyAndSyncs() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dao = InMemoryGuardiansDao()
        val fakeBle = FakeBleSessionManager()
        fakeBle._connectionState.value = BleConnectionState.Ready

        val repo = DefaultGuardiansRepository(
            dao = dao,
            bleSessionManager = fakeBle,
            dispatcher = testDispatcher,
            externalScope = repoScope
        )

        try {
            repo.saveGuardian(0, "Bob", "+15555550102")
            testScheduler.runCurrent()
            assertNotNull(dao.getSlot(0))

            // Clear slot 0
            repo.clearGuardian(0)
            testScheduler.runCurrent()

            assertNull(dao.getSlot(0))
            assertEquals("", fakeBle.lastSyncedProfile!!.name0)
            assertEquals("", fakeBle.lastSyncedProfile!!.phone0)
        } finally {
            repoScope.cancel()
        }
    }

    @Test
    fun syncFailure_truthfullyUpdatesState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dao = InMemoryGuardiansDao()
        val fakeBle = FakeBleSessionManager()
        fakeBle._connectionState.value = BleConnectionState.Ready
        fakeBle.shouldSyncSucceed = false // Simulate write error

        val repo = DefaultGuardiansRepository(
            dao = dao,
            bleSessionManager = fakeBle,
            dispatcher = testDispatcher,
            externalScope = repoScope
        )

        try {
            val result = repo.saveGuardian(1, "Charlie", "+15555550103")
            testScheduler.runCurrent()

            assertTrue(result.isFailure)
            assertTrue(repo.syncState.value is GuardianSyncState.SyncFailed)
            assertTrue(repo.isPendingSync.value)
            assertEquals("Saved on phone, watch update failed", repo.syncState.value.displayText)
        } finally {
            repoScope.cancel()
        }
    }

    @Test
    fun swapGuardians_swapsSlot0AndSlot1_andSyncsToWatch() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val dao = InMemoryGuardiansDao()
        val fakeBle = FakeBleSessionManager()
        fakeBle._connectionState.value = BleConnectionState.Ready

        val repo = DefaultGuardiansRepository(
            dao = dao,
            bleSessionManager = fakeBle,
            dispatcher = testDispatcher,
            externalScope = repoScope
        )

        try {
            // Setup: Alice in slot 0 (Primary), Bob in slot 1 (Secondary)
            repo.saveGuardian(0, "Alice", "+15555550101")
            repo.saveGuardian(1, "Bob", "+15555550102")
            testScheduler.runCurrent()

            assertEquals("Alice", dao.getSlot(0)?.name)
            assertEquals("Bob", dao.getSlot(1)?.name)

            // Execute Swap
            val swapResult = repo.swapGuardians()
            testScheduler.runCurrent()

            assertTrue(swapResult.isSuccess)

            // Slot 0 is now Bob, Slot 1 is now Alice
            val newSlot0 = dao.getSlot(0)
            val newSlot1 = dao.getSlot(1)
            assertNotNull(newSlot0)
            assertNotNull(newSlot1)
            assertEquals("Bob", newSlot0!!.name)
            assertEquals("+15555550102", newSlot0.phone)
            assertEquals("Alice", newSlot1!!.name)
            assertEquals("+15555550101", newSlot1.phone)

            // BLE profile reflects new priority
            assertNotNull(fakeBle.lastSyncedProfile)
            assertEquals("Bob", fakeBle.lastSyncedProfile!!.name0)
            assertEquals("+15555550102", fakeBle.lastSyncedProfile!!.phone0)
            assertEquals("Alice", fakeBle.lastSyncedProfile!!.name1)
            assertEquals("+15555550101", fakeBle.lastSyncedProfile!!.phone1)
        } finally {
            repoScope.cancel()
        }
    }

    @Test
    fun sanitizePhoneNumber_cleansFormattedAddressBookStrings() {
        assertEquals("+15555550101", ProtocolV1Codec.sanitizePhoneNumber("+1 (555) 555-0101"))
        assertEquals("+919876543210", ProtocolV1Codec.sanitizePhoneNumber("+91 98765-43210"))
        assertEquals("5551234567", ProtocolV1Codec.sanitizePhoneNumber("555.123.4567"))
        assertEquals("8005550199", ProtocolV1Codec.sanitizePhoneNumber("(800) 555-0199"))
        assertEquals("", ProtocolV1Codec.sanitizePhoneNumber("   "))
    }
}
