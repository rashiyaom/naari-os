package com.naari.companion.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.ReadinessDeriver
import com.naari.companion.ble.ReadinessState
import com.naari.companion.data.IncidentLog
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.watch.WatchSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * UI State for the Incidents view.
 */
data class IncidentsUiState(
    val readinessState: ReadinessState = ReadinessState.Disconnected,
    val isConnected: Boolean = false,
    val isFallDetectionEnabled: Boolean = false,
    val lastSyncTimeMs: Long? = null,
    val activeIncident: ActiveIncident? = null,
    val remainingCountdownSeconds: Int = 0,
    val incidentLogs: List<IncidentLog> = emptyList(),
    val isCancelling: Boolean = false,
    val cancelError: String? = null
)

/**
 * ViewModel driving the Incidents view.
 * Ref: PRD Section 3 & Master Prompt Task 16.
 */
@HiltViewModel
class IncidentsViewModel(
    private val incidentsRepository: IncidentsRepository,
    private val permissionsRepository: PermissionsRepository,
    private val bleSessionManager: BleSessionManager,
    private val watchSettingsRepository: WatchSettingsRepository,
    externalScope: CoroutineScope?
) : ViewModel() {

    @Inject
    constructor(
        incidentsRepository: IncidentsRepository,
        permissionsRepository: PermissionsRepository,
        bleSessionManager: BleSessionManager,
        watchSettingsRepository: WatchSettingsRepository
    ) : this(
        incidentsRepository = incidentsRepository,
        permissionsRepository = permissionsRepository,
        bleSessionManager = bleSessionManager,
        watchSettingsRepository = watchSettingsRepository,
        externalScope = null
    )

    // Overload constructor for testing
    constructor(
        incidentsRepository: IncidentsRepository,
        permissionsRepository: PermissionsRepository,
        bleSessionManager: BleSessionManager,
        externalScope: CoroutineScope?
    ) : this(
        incidentsRepository = incidentsRepository,
        permissionsRepository = permissionsRepository,
        bleSessionManager = bleSessionManager,
        watchSettingsRepository = com.naari.companion.watch.DefaultWatchSettingsRepository(bleSessionManager),
        externalScope = externalScope
    )

    private val scope = externalScope ?: viewModelScope

    private val _cancelError = MutableStateFlow<String?>(null)
    private val _isCancelling = MutableStateFlow(false)
    private val _currentTimeMs = MutableStateFlow(System.currentTimeMillis())

    init {
        // Ticker to re-evaluate remaining seconds strictly against watch authority deadline
        scope.launch {
            while (isActive) {
                _currentTimeMs.value = System.currentTimeMillis()
                delay(500L)
            }
        }
    }

    private val incidentLogsFlow = incidentsRepository.incidentLogs

    val uiState: StateFlow<IncidentsUiState> = combine(
        permissionsRepository.permissions,
        incidentsRepository.activeIncident,
        incidentLogsFlow,
        _currentTimeMs,
        _cancelError
    ) { perms, active, logs, nowMs, err ->
        val connStep = bleSessionManager?.connectionStep?.value ?: BleConnectionStep.Disconnected
        val hasGatt = bleSessionManager?.hasRequiredGattService?.value ?: false
        val lastHello = bleSessionManager?.lastHelloTimestampMs?.value

        val readiness = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = perms.bluetoothAdapterEnabled,
            connectionStep = connStep,
            hasRequiredGattService = hasGatt,
            lastHelloTimestampMs = lastHello,
            currentTimeMs = nowMs,
            permissions = perms
        )

        val remainingSeconds = active?.remainingSeconds(nowMs) ?: 0
        val fallEnabled = watchSettingsRepository?.fallDetectionState?.value?.currentValue ?: false
        val lastSync = watchSettingsRepository?.statusSnapshot?.value?.lastSyncTimestampMs ?: lastHello

        IncidentsUiState(
            readinessState = readiness,
            isConnected = readiness == ReadinessState.Ready || readiness == ReadinessState.ConnectedServiceUnavailable,
            isFallDetectionEnabled = fallEnabled,
            lastSyncTimeMs = lastSync,
            activeIncident = active,
            remainingCountdownSeconds = remainingSeconds,
            incidentLogs = logs,
            isCancelling = _isCancelling.value,
            cancelError = err
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = IncidentsUiState()
    )

    fun startSafeDemo() {
        scope.launch {
            _cancelError.value = null
            incidentsRepository.startSafeDemo(countdownSeconds = 30)
        }
    }

    fun cancelActiveSos(disarmPin: String, reason: String = "User disarm") {
        scope.launch {
            _isCancelling.value = true
            _cancelError.value = null
            val result = incidentsRepository.cancelActiveSos(disarmPin, reason)
            if (result.isFailure) {
                _cancelError.value = result.exceptionOrNull()?.message ?: "Failed to disarm SOS"
            }
            _isCancelling.value = false
        }
    }

    fun resolveActiveIncident(outcome: String = "RESOLVED") {
        scope.launch {
            incidentsRepository.resolveActiveIncident(outcome)
        }
    }

    fun clearCancelError() {
        _cancelError.value = null
    }

    companion object {
        private fun createFallbackBleSession(): BleSessionManager = object : BleSessionManager {
            override val connectionState = MutableStateFlow(com.naari.companion.ble.BleConnectionState.Disconnected)
            override val connectionStep = MutableStateFlow(BleConnectionStep.Disconnected)
            override val hasRequiredGattService = MutableStateFlow(false)
            override val hasV2ControlService = MutableStateFlow(false)
            override val negotiatedMtu = MutableStateFlow(23)
            override val lastHelloTimestampMs = MutableStateFlow<Long?>(null)
            override val batteryPercent = MutableStateFlow<Int?>(null)
            override val incomingRequests = kotlinx.coroutines.flow.MutableSharedFlow<com.naari.companion.protocol.WatchRequest>()
            override fun connect(deviceAddress: String) {}
            override suspend fun sendReply(reply: com.naari.companion.protocol.PhoneReply) = Result.success(Unit)
            override suspend fun syncProfile(profile: com.naari.companion.protocol.PhoneProfileMessage) = Result.success(Unit)
            override suspend fun sendControlCommand(commandJson: String) = Result.success(Unit)
            override fun disconnect() {}
        }

        private fun createFallbackWatchSettings(): WatchSettingsRepository =
            com.naari.companion.watch.DefaultWatchSettingsRepository()
    }
}
