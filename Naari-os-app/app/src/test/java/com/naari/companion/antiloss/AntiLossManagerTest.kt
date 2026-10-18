package com.naari.companion.antiloss

import android.content.Context
import android.content.ContextWrapper
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.location.LocationFix
import com.naari.companion.location.LocationProvider
import com.naari.companion.location.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AntiLossManagerTest {

    private class DummyContext : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): Context = this
    }

    private class FakeLocationProvider : LocationProvider {
        var fixToReturn: LocationFix? = LocationFix(
            latitude = 19.0760,
            longitude = 72.8777,
            accuracyMeters = 25f
        )

        override suspend fun acquireLocation(timeoutMs: Long): LocationResult {
            val fix = fixToReturn
            return if (fix != null) LocationResult.Fresh(fix) else LocationResult.Disabled("GPS off")
        }

        override fun composeLocationAlert(baseMessage: String?, locationResult: LocationResult): String = ""
    }

    @Test
    fun unexpectedDisconnectFromReady_triggersSeparationAlertWithLocation() = runTest {
        val context = DummyContext()
        val locationProvider = FakeLocationProvider()
        val manager = AntiLossManager(context, locationProvider, externalScope = this)

        assertNull(manager.lastDisconnectEvent.value)

        // Drop from Ready to Disconnected
        manager.onConnectionStateChanged(
            previous = BleConnectionState.Ready,
            current = BleConnectionState.Disconnected
        )

        advanceUntilIdle()

        val event = manager.lastDisconnectEvent.value
        assertNotNull(event)
        assertEquals(19.0760, event!!.latitude!!, 0.0001)
        assertEquals(72.8777, event.longitude!!, 0.0001)
        assertTrue(event.mapsUrl!!.contains("19.076,72.8777"))
    }

    @Test
    fun disconnectFromConnecting_doesNotTriggerSeparationAlert() = runTest {
        val context = DummyContext()
        val locationProvider = FakeLocationProvider()
        val manager = AntiLossManager(context, locationProvider, externalScope = this)

        // Drop from Connecting to Disconnected (never established Ready link)
        manager.onConnectionStateChanged(
            previous = BleConnectionState.Connecting,
            current = BleConnectionState.Disconnected
        )

        advanceUntilIdle()

        assertNull(manager.lastDisconnectEvent.value)
    }

    @Test
    fun clearAlert_removesLastDisconnectEvent() = runTest {
        val context = DummyContext()
        val locationProvider = FakeLocationProvider()
        val manager = AntiLossManager(context, locationProvider, externalScope = this)

        manager.onConnectionStateChanged(
            previous = BleConnectionState.Ready,
            current = BleConnectionState.Disconnected
        )
        advanceUntilIdle()
        assertNotNull(manager.lastDisconnectEvent.value)

        manager.clearAlert()

        assertNull(manager.lastDisconnectEvent.value)
    }

    @Test
    fun disabledManager_ignoresConnectionDrops() = runTest {
        val context = DummyContext()
        val locationProvider = FakeLocationProvider()
        val manager = AntiLossManager(context, locationProvider, externalScope = this)

        manager.setEnabled(false)
        assertFalse(manager.isEnabled.value)

        manager.onConnectionStateChanged(
            previous = BleConnectionState.Ready,
            current = BleConnectionState.Disconnected
        )
        advanceUntilIdle()

        assertNull(manager.lastDisconnectEvent.value)
    }
}
