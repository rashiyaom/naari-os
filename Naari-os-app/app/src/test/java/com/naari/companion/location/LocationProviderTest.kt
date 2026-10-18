package com.naari.companion.location

import com.naari.companion.actions.SmsExecutionResult
import com.naari.companion.actions.SmsExecutor
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationProviderTest {

    // region Fakes

    private class FakeLocationClient : LocationClient {
        var hasPermission: Boolean = true
        var isEnabled: Boolean = true
        var currentLocation: LocationFix? = null
        var mockLastKnownLocation: LocationFix? = null

        override fun hasLocationPermission(): Boolean = hasPermission
        override fun isLocationEnabled(): Boolean = isEnabled
        override suspend fun getCurrentLocation(timeoutMs: Long): LocationFix? = currentLocation
        override fun getLastKnownLocation(): LocationFix? = mockLastKnownLocation
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

    private class FakeSmsExecutor : SmsExecutor {
        var lastSentRequestId: Long? = null
        var lastSentPhone: String? = null
        var lastSentMessage: String? = null
        var lastSentLat: Double? = null
        var lastSentLon: Double? = null
        var resultToReturn: SmsExecutionResult = SmsExecutionResult.Success(
            status = RequestLedgerStatus.Delivered,
            detail = "SMS delivered"
        )

        override suspend fun sendSms(
            requestId: Long,
            phone: String,
            message: String,
            lat: Double?,
            lon: Double?
        ): SmsExecutionResult {
            lastSentRequestId = requestId
            lastSentPhone = phone
            lastSentMessage = message
            lastSentLat = lat
            lastSentLon = lon
            return resultToReturn
        }
    }

    // endregion

    // region Coordinate Bounds and Integrity Tests

    @Test
    fun locationFix_validCoordinates_accepted() {
        val fix = LocationFix(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 10f
        )
        assertEquals(12.9716, fix.latitude, 0.0001)
        assertEquals(77.5946, fix.longitude, 0.0001)
        assertEquals("https://maps.google.com/?q=12.9716,77.5946", fix.toMapUrl())
    }

    @Test
    fun locationFix_boundaryCoordinates_accepted() {
        val northPole = LocationFix(latitude = 90.0, longitude = 0.0)
        assertEquals(90.0, northPole.latitude, 0.0)

        val southPole = LocationFix(latitude = -90.0, longitude = 0.0)
        assertEquals(-90.0, southPole.latitude, 0.0)

        val dateLineEast = LocationFix(latitude = 0.0, longitude = 180.0)
        assertEquals(180.0, dateLineEast.longitude, 0.0)

        val dateLineWest = LocationFix(latitude = 0.0, longitude = -180.0)
        assertEquals(-180.0, dateLineWest.longitude, 0.0)
    }

    @Test
    fun locationFix_invalidLatitude_rejectedWithoutFabrication() {
        try {
            LocationFix(latitude = 90.1, longitude = 0.0)
            fail("Expected IllegalArgumentException for latitude > 90")
        } catch (_: IllegalArgumentException) {}

        try {
            LocationFix(latitude = -90.1, longitude = 0.0)
            fail("Expected IllegalArgumentException for latitude < -90")
        } catch (_: IllegalArgumentException) {}

        try {
            LocationFix(latitude = Double.NaN, longitude = 0.0)
            fail("Expected IllegalArgumentException for NaN latitude")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun locationFix_invalidLongitude_rejectedWithoutFabrication() {
        try {
            LocationFix(latitude = 0.0, longitude = 180.1)
            fail("Expected IllegalArgumentException for longitude > 180")
        } catch (_: IllegalArgumentException) {}

        try {
            LocationFix(latitude = 0.0, longitude = -180.1)
            fail("Expected IllegalArgumentException for longitude < -180")
        } catch (_: IllegalArgumentException) {}

        try {
            LocationFix(latitude = 0.0, longitude = Double.POSITIVE_INFINITY)
            fail("Expected IllegalArgumentException for infinite longitude")
        } catch (_: IllegalArgumentException) {}
    }

    // endregion

    // region LocationProvider State Paths: Fresh, Approximate, Stale, Timeout, Denied, Disabled

    @Test
    fun acquireLocation_freshFix_withinTargetBounds_returnsFresh() = runTest {
        val client = FakeLocationClient()
        val now = System.currentTimeMillis()
        client.currentLocation = LocationFix(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 15f,
            timestamp = now - 5_000L, // 5 seconds old (<= 30s)
            provider = "gps",
            isCached = false
        )

        val provider = DefaultLocationProvider(
            client = client,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = provider.acquireLocation()

        assertTrue("Expected Fresh result", result is LocationResult.Fresh)
        val freshFix = (result as LocationResult.Fresh).fix
        assertEquals(28.6139, freshFix.latitude, 0.0001)
        assertEquals(77.2090, freshFix.longitude, 0.0001)

        val alert = provider.composeLocationAlert("Help me", result)
        assertTrue(alert.contains("https://maps.google.com/?q=28.6139,77.209"))
        assertTrue(alert.contains("±15m"))
    }

    @Test
    fun acquireLocation_approximateFix_accuracyExceedsTarget_returnsCachedLabeled() = runTest {
        val client = FakeLocationClient()
        val now = System.currentTimeMillis()
        // Accuracy 150m exceeds 100m target; within 120s cache window
        client.currentLocation = LocationFix(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 150f,
            timestamp = now - 10_000L,
            provider = "network",
            isCached = false
        )

        val provider = DefaultLocationProvider(
            client = client,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = provider.acquireLocation()

        // Must be labeled Cached (truthful quality representation, never presented as fresh GPS)
        assertTrue("Expected Cached result for approximate fix", result is LocationResult.Cached)
        val alert = provider.composeLocationAlert("Emergency", result)
        assertTrue(alert.contains("cached 10s ago"))
    }

    @Test
    fun acquireLocation_freshTimedOut_recentCacheAvailable_returnsCachedWithAge() = runTest {
        val client = FakeLocationClient()
        val now = System.currentTimeMillis()
        client.currentLocation = null // Live GPS timed out
        client.mockLastKnownLocation = LocationFix(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracyMeters = 20f,
            timestamp = now - 45_000L, // 45 seconds old (<= 120s cache expiry)
            provider = "gps",
            isCached = true
        )

        val provider = DefaultLocationProvider(
            client = client,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = provider.acquireLocation()

        assertTrue(result is LocationResult.Cached)
        val cached = result as LocationResult.Cached
        assertEquals(45L, cached.ageSeconds)
        val alert = provider.composeLocationAlert("SOS", result)
        assertTrue(alert.contains("cached 45s ago"))
    }

    @Test
    fun acquireLocation_cacheExpiredBeyond120Seconds_discardsCacheAndReturnsTimeout() = runTest {
        val client = FakeLocationClient()
        val now = System.currentTimeMillis()
        client.currentLocation = null
        client.mockLastKnownLocation = LocationFix(
            latitude = 19.0760,
            longitude = 72.8777,
            timestamp = now - 125_000L, // 125 seconds old (> 120s limit)
            isCached = true
        )

        val provider = DefaultLocationProvider(
            client = client,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = provider.acquireLocation()

        // Expired cache must NEVER be used
        assertTrue("Expected Timeout when cache is older than 120s", result is LocationResult.Timeout)
        val alert = provider.composeLocationAlert("SOS", result)
        assertTrue(alert.contains("Location unavailable (GPS timeout)"))
    }

    @Test
    fun acquireLocation_permissionDenied_noCache_returnsPermissionDenied() = runTest {
        val client = FakeLocationClient().apply {
            hasPermission = false
        }

        val provider = DefaultLocationProvider(
            client = client,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = provider.acquireLocation()

        assertTrue(result is LocationResult.PermissionDenied)
        val alert = provider.composeLocationAlert("Alert", result)
        assertTrue(alert.contains("Location unavailable (permission not granted)"))
    }

    @Test
    fun acquireLocation_locationServicesDisabled_returnsDisabled() = runTest {
        val client = FakeLocationClient().apply {
            isEnabled = false
        }

        val provider = DefaultLocationProvider(
            client = client,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = provider.acquireLocation()

        assertTrue(result is LocationResult.Disabled)
        val alert = provider.composeLocationAlert("Alert", result)
        assertTrue(alert.contains("Location unavailable (location services disabled)"))
    }

    // endregion

    // region LocationExecutor Integration Tests: 'location' and 'location_sms'

    @Test
    fun executeLocation_success_repliesLocatedAndUpdatesLedger() = runTest {
        val client = FakeLocationClient()
        val now = System.currentTimeMillis()
        client.currentLocation = LocationFix(
            latitude = 13.0827,
            longitude = 80.2707,
            accuracyMeters = 10f,
            timestamp = now,
            isCached = false
        )

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val locationProvider = DefaultLocationProvider(client = client, ioDispatcher = testDispatcher)
        val smsExecutor = FakeSmsExecutor()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()

        val executor = DefaultLocationExecutor(
            locationProvider = locationProvider,
            smsExecutor = smsExecutor,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 301L, type = WatchRequestType.LOCATION),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeLocation(301L)

        assertTrue(result is LocationResult.Fresh)
        // Verify BLE replies: RECEIVED first, then LOCATED with coordinates
        assertEquals(PhoneReplyStatus.RECEIVED, ble.sentReplies[0].status)
        assertEquals(PhoneReplyStatus.LOCATED, ble.sentReplies[1].status)
        assertEquals(13.0827, ble.sentReplies[1].lat!!, 0.0001)
        assertEquals(80.2707, ble.sentReplies[1].lon!!, 0.0001)
        // Verify Ledger updated to Located
        assertEquals(RequestLedgerStatus.Located, ledger.entries.first().status)
    }

    @Test
    fun executeLocation_timeout_repliesFailedAndUpdatesLedger() = runTest {
        val client = FakeLocationClient() // No location
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val locationProvider = DefaultLocationProvider(client = client, ioDispatcher = testDispatcher)
        val smsExecutor = FakeSmsExecutor()
        val ledger = FakeLedgerRepository()
        val ble = FakeBleSessionManager()

        val executor = DefaultLocationExecutor(
            locationProvider = locationProvider,
            smsExecutor = smsExecutor,
            ledgerRepository = ledger,
            bleSessionManager = ble,
            ioDispatcher = testDispatcher
        )

        ledger.submitRequest(
            WatchRequest(id = 302L, type = WatchRequestType.LOCATION),
            deviceAddress = "AA:BB:CC:DD:EE:FF"
        )

        val result = executor.executeLocation(302L)

        assertTrue(result is LocationResult.Timeout)
        assertEquals(PhoneReplyStatus.RECEIVED, ble.sentReplies[0].status)
        assertEquals(PhoneReplyStatus.FAILED, ble.sentReplies[1].status)
        assertEquals(RequestLedgerStatus.Failed, ledger.entries.first().status)
    }

    @Test
    fun executeLocationSms_success_composesMapLinkAndDispatchesSms() = runTest {
        val client = FakeLocationClient()
        val now = System.currentTimeMillis()
        client.currentLocation = LocationFix(
            latitude = 13.0827,
            longitude = 80.2707,
            accuracyMeters = 8f,
            timestamp = now,
            isCached = false
        )

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val locationProvider = DefaultLocationProvider(client = client, ioDispatcher = testDispatcher)
        val smsExecutor = FakeSmsExecutor()

        val executor = DefaultLocationExecutor(
            locationProvider = locationProvider,
            smsExecutor = smsExecutor,
            ioDispatcher = testDispatcher
        )

        val result = executor.executeLocationSms(
            requestId = 303L,
            phoneNumber = "+919876543210",
            baseMessage = "Emergency alert"
        )

        assertTrue(result is SmsExecutionResult.Success)
        assertEquals("+919876543210", smsExecutor.lastSentPhone)
        assertTrue(smsExecutor.lastSentMessage!!.contains("https://maps.google.com/?q=13.0827,80.2707"))
        assertTrue(smsExecutor.lastSentMessage!!.contains("Emergency alert"))
        assertEquals(13.0827, smsExecutor.lastSentLat)
        assertEquals(80.2707, smsExecutor.lastSentLon)
    }

    @Test
    fun executeLocationSms_locationUnavailable_stillSendsSafetySmsWithLocationUnavailableText() = runTest {
        // SAFETY REQUIREMENT:
        // Location failures must NOT silently prevent a safety message:
        // send explicit location-unavailable text when SMS is permitted!
        val client = FakeLocationClient() // No location available
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val locationProvider = DefaultLocationProvider(client = client, ioDispatcher = testDispatcher)
        val smsExecutor = FakeSmsExecutor()

        val executor = DefaultLocationExecutor(
            locationProvider = locationProvider,
            smsExecutor = smsExecutor,
            ioDispatcher = testDispatcher
        )

        val result = executor.executeLocationSms(
            requestId = 304L,
            phoneNumber = "+919876543210",
            baseMessage = "Need help"
        )

        assertTrue(result is SmsExecutionResult.Success)
        assertEquals("+919876543210", smsExecutor.lastSentPhone)
        assertTrue(smsExecutor.lastSentMessage!!.contains("Need help"))
        assertNull(smsExecutor.lastSentLat)
        assertNull(smsExecutor.lastSentLon)
        assertTrue(smsExecutor.lastSentMessage!!.contains("Location unavailable (GPS timeout)"))
    }

    // endregion
}
