package com.naari.companion.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiagnosticsUiState(
    val deviceStatus: DeviceStatus = DeviceStatus.NotPaired,
    val pairedAddress: String? = null,
    val pairedName: String? = null,
    val batteryPercent: Int? = null,
    val lastSyncTimestampMs: Long? = null,
    val recentLedgerEntries: List<RequestLedgerEntry> = emptyList()
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    deviceStateRepository: DeviceStateRepository,
    ledgerRepository: RequestLedgerRepository
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        deviceStateRepository.deviceStatus,
        deviceStateRepository.pairedDeviceAddress,
        deviceStateRepository.pairedDeviceName,
        deviceStateRepository.batteryPercent,
        deviceStateRepository.lastSyncTimestampMs,
        ledgerRepository.recentEntries
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val entries = args[5] as List<RequestLedgerEntry>
        DiagnosticsUiState(
            deviceStatus = args[0] as DeviceStatus,
            pairedAddress = args[1] as String?,
            pairedName = args[2] as String?,
            batteryPercent = args[3] as Int?,
            lastSyncTimestampMs = args[4] as Long?,
            recentLedgerEntries = entries.take(10)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiagnosticsUiState()
    )
}
