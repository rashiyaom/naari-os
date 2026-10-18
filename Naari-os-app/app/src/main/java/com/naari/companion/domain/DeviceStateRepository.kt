package com.naari.companion.domain

import com.naari.companion.ble.ReadinessState
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth repository managing unified device state, sync status, and mutual readiness.
 * Observed across all UI tabs, persistent header, and background service.
 */
interface DeviceStateRepository {
    /** High-level watch status (Not paired, Searching, Connecting, Connected, Ready, Needs attention, Bluetooth off) */
    val deviceStatus: StateFlow<DeviceStatus>

    /** Current sync state between phone and watch */
    val syncStatus: StateFlow<SyncStatus>

    /** Fine-grained readiness level used by safety monitors and badges */
    val readinessState: StateFlow<ReadinessState>

    /** Battery percentage reported by watch via mutual hello / telemetry (0..100) */
    val batteryPercent: StateFlow<Int?>

    /** Bonded watch Bluetooth MAC address */
    val pairedDeviceAddress: StateFlow<String?>

    /** Bonded watch display name */
    val pairedDeviceName: StateFlow<String?>

    /** Epoch timestamp of last successful sync with watch */
    val lastSyncTimestampMs: StateFlow<Long?>

    /** Notifies state machine that a synchronization operation has started */
    fun notifySyncStarted()

    /** Notifies state machine that synchronization succeeded */
    fun notifySyncCompleted(timestampMs: Long = System.currentTimeMillis())

    /** Notifies state machine that synchronization failed */
    fun notifySyncFailed(reason: String)
}
