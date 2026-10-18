package com.naari.companion.navigation

import androidx.lifecycle.ViewModel
import com.naari.companion.ble.ReadinessState
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Shared ViewModel for [NaariAppShell] providing persistent top header state across all tabs.
 * Ref: PRD Section 4 & Master Prompt Phase 2.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val deviceStateRepository: DeviceStateRepository
) : ViewModel() {

    val deviceStatus: StateFlow<DeviceStatus> = deviceStateRepository.deviceStatus
    val syncStatus: StateFlow<SyncStatus> = deviceStateRepository.syncStatus
    val readinessState: StateFlow<ReadinessState> = deviceStateRepository.readinessState
    val batteryPercent: StateFlow<Int?> = deviceStateRepository.batteryPercent
    val pairedDeviceAddress: StateFlow<String?> = deviceStateRepository.pairedDeviceAddress
    val pairedDeviceName: StateFlow<String?> = deviceStateRepository.pairedDeviceName
    val lastSyncTimestampMs: StateFlow<Long?> = deviceStateRepository.lastSyncTimestampMs
}
