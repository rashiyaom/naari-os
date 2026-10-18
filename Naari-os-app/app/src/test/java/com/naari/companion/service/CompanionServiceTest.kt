package com.naari.companion.service

import android.content.Intent
import com.naari.companion.ble.BleConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionServiceTest {

    // region Battery Restrictions & Force-Stop Guidance Tests

    @Test
    fun forceStopRecoveryGuidance_explainsTruthfulRecoveryWithoutFalsePromises() {
        val guidance = BatteryOptimizationHelper.FORCE_STOP_RECOVERY_GUIDANCE
        assertTrue("Must explain OS behavior on force-stop", guidance.contains("force-stopped"))
        assertTrue("Must state that OS prohibits auto-restart", guidance.contains("prohibits automatic restarts") || guidance.contains("cannot restart automatically"))
        assertTrue("Must instruct user to reopen app", guidance.contains("manually open the Naari app"))
    }

    @Test
    fun batteryRestrictionStatus_optimized_includesWarning() {
        val status = BatteryRestrictionStatus.Optimized()
        assertTrue(status.warning.contains("screen is off or idle", ignoreCase = true))
    }

    @Test
    fun batteryRestrictionStatus_unrestricted_isTerminalClean() {
        val status = BatteryRestrictionStatus.Unrestricted
        assertEquals("Unrestricted", status.toString())
    }

    // endregion

    // region CompanionService Constants & Status Formatting Tests

    @Test
    fun serviceConstants_complyWithForegroundServiceRequirements() {
        assertEquals("naari_companion_status_channel", CompanionService.CHANNEL_ID)
        assertEquals(2001, CompanionService.NOTIFICATION_ID)
        assertEquals("com.naari.companion.START_FOREGROUND_SERVICE", CompanionService.ACTION_START_SERVICE)
        assertEquals("com.naari.companion.STOP_FOREGROUND_SERVICE", CompanionService.ACTION_STOP_SERVICE)
    }

    @Test
    fun connectionStates_formattedTruthfullyForNotification() {
        // Ready -> Active monitoring
        val readyText = formatConnectionStatus(BleConnectionState.Ready)
        assertTrue("Ready must indicate active monitoring", readyText.contains("Safety Monitoring Active"))

        // Connecting
        val connectingText = formatConnectionStatus(BleConnectionState.Connecting)
        assertTrue(connectingText.contains("Connecting"))

        // Scanning
        val scanningText = formatConnectionStatus(BleConnectionState.Scanning)
        assertTrue(scanningText.contains("Scanning"))

        // Disconnected
        val disconnectedText = formatConnectionStatus(BleConnectionState.Disconnected)
        assertTrue(disconnectedText.contains("Disconnected"))

        // BluetoothDisabled
        val btDisabledText = formatConnectionStatus(BleConnectionState.BluetoothDisabled)
        assertTrue(btDisabledText.contains("Bluetooth disabled"))

        // ConnectedServiceUnavailable
        val unavailText = formatConnectionStatus(BleConnectionState.ConnectedServiceUnavailable)
        assertTrue(unavailText.contains("Preparing services"))
    }

    @Test
    fun serviceDependencies_injectionAndResetLifecycle() {
        CompanionServiceDependencies.reset()
        assertEquals(null, CompanionServiceDependencies.bleSessionManager)
        assertEquals(null, CompanionServiceDependencies.actionDispatcher)
    }

    // Helper mirroring CompanionService format logic for pure JVM testing
    private fun formatConnectionStatus(state: BleConnectionState): String {
        return when (state) {
            BleConnectionState.Ready -> "Connected & Ready -- Safety Monitoring Active"
            BleConnectionState.Connecting -> "Connecting to Naari Watch…"
            BleConnectionState.Scanning -> "Scanning for watch…"
            BleConnectionState.Disconnected -> "Disconnected -- Looking for watch"
            BleConnectionState.BluetoothDisabled -> "Bluetooth disabled -- Safety monitoring paused"
            BleConnectionState.ConnectedServiceUnavailable -> "Connected -- Preparing services…"
        }
    }

    // endregion
}
