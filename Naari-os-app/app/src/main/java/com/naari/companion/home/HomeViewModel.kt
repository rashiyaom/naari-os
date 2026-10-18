package com.naari.companion.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.ReadinessDeriver
import com.naari.companion.ble.ReadinessState
import com.naari.companion.data.DefaultDeviceStateRepository
import com.naari.companion.data.Guardian
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.ledger.LedgerSubmissionResult
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.permissions.PermissionsSnapshot
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.naari.companion.antiloss.AntiLossManager
import com.naari.companion.antiloss.DisconnectEvent
import com.naari.companion.checkin.SafeCheckInManager
import com.naari.companion.checkin.SafeCheckInResult
import com.naari.companion.safewalk.SafeWalkManager
import com.naari.companion.safewalk.SafeWalkState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for the Home dashboard conforming to PRD Section 4 & Master Prompt Task 14.
 */
data class HomeUiState(
    val readiness: ReadinessState = ReadinessState.Disconnected,
    val deviceStatus: DeviceStatus = DeviceStatus.NotPaired,
    val syncStatus: SyncStatus = SyncStatus.UpToDate(),
    val connectionState: BleConnectionState = BleConnectionState.Disconnected,
    val batteryPercent: Int? = null,
    val lastSeenTimestampMs: Long? = null,
    val guardiansCount: Int = 0,
    val primaryGuardianName: String? = null,
    val primaryGuardianPhone: String? = null,
    val recentLedgerEntries: List<RequestLedgerEntry> = emptyList(),
    val pendingLedgerCount: Int = 0,
    val lastActionSummary: String? = null,
    val inlineErrorSafety: String? = null,
    val inlineErrorGuardians: String? = null,
    val inlineErrorConnection: String? = null,
    val isOnboardingCompleted: Boolean = true,
    val pairedDeviceAddress: String? = null,
    val pairedDeviceName: String? = null,
    val safeWalkState: SafeWalkState = SafeWalkState.Idle,
    val disconnectEvent: DisconnectEvent? = null,
    val checkInResult: SafeCheckInResult? = null,
    val checkInError: String? = null,
    val isCheckingIn: Boolean = false
)

private data class BleTelemetry(
    val connectionState: BleConnectionState,
    val connectionStep: BleConnectionStep,
    val hasRequiredGattService: Boolean,
    val lastHelloTimestampMs: Long?,
    val batteryPercent: Int?
)

private data class DeviceSetupInfo(
    val onboardingCompleted: Boolean,
    val pairedDeviceAddress: String?,
    val pairedDeviceName: String?
)

/**
 * ViewModel managing Home screen live state, telemetry, and truthful readiness derivation.
 */
@HiltViewModel
class HomeViewModel(
    private val permissionsRepository: PermissionsRepository,
    private val bleSessionManager: BleSessionManager,
    private val guardiansRepository: GuardiansRepository,
    private val ledgerRepository: RequestLedgerRepository,
    private val onboardingRepository: OnboardingRepository,
    private val deviceStateRepository: DeviceStateRepository,
    private val safeWalkManager: SafeWalkManager?,
    private val antiLossManager: AntiLossManager?,
    private val safeCheckInManager: SafeCheckInManager?,
    externalScope: CoroutineScope?
) : ViewModel() {

    @Inject
    constructor(
        permissionsRepository: PermissionsRepository,
        bleSessionManager: BleSessionManager,
        guardiansRepository: GuardiansRepository,
        ledgerRepository: RequestLedgerRepository,
        onboardingRepository: OnboardingRepository,
        deviceStateRepository: DeviceStateRepository,
        safeWalkManager: SafeWalkManager,
        antiLossManager: AntiLossManager,
        safeCheckInManager: SafeCheckInManager
    ) : this(
        permissionsRepository = permissionsRepository,
        bleSessionManager = bleSessionManager,
        guardiansRepository = guardiansRepository,
        ledgerRepository = ledgerRepository,
        onboardingRepository = onboardingRepository,
        deviceStateRepository = deviceStateRepository,
        safeWalkManager = safeWalkManager,
        antiLossManager = antiLossManager,
        safeCheckInManager = safeCheckInManager,
        externalScope = null
    )

    // Overload constructor for isolated testing
    constructor(
        permissionsRepository: PermissionsRepository,
        bleSessionManager: BleSessionManager,
        guardiansRepository: GuardiansRepository? = null,
        ledgerRepository: RequestLedgerRepository? = null,
        onboardingRepository: OnboardingRepository? = null,
        safeWalkManager: SafeWalkManager? = null,
        antiLossManager: AntiLossManager? = null,
        safeCheckInManager: SafeCheckInManager? = null,
        externalScope: CoroutineScope? = null
    ) : this(
        permissionsRepository = permissionsRepository,
        bleSessionManager = bleSessionManager,
        guardiansRepository = guardiansRepository ?: createFallbackGuardiansRepo(),
        ledgerRepository = ledgerRepository ?: createFallbackLedgerRepo(),
        onboardingRepository = onboardingRepository ?: createFallbackOnboardingRepo(),
        deviceStateRepository = DefaultDeviceStateRepository(
            bleSessionManager = bleSessionManager,
            permissionsRepository = permissionsRepository,
            onboardingRepository = onboardingRepository ?: createFallbackOnboardingRepo(),
            guardiansRepository = guardiansRepository ?: createFallbackGuardiansRepo(),
            externalScope = externalScope
        ),
        safeWalkManager = safeWalkManager,
        antiLossManager = antiLossManager,
        safeCheckInManager = safeCheckInManager,
        externalScope = externalScope
    )

    private val scope = externalScope ?: viewModelScope

    private val _checkInResult = MutableStateFlow<SafeCheckInResult?>(null)
    private val _checkInError = MutableStateFlow<String?>(null)
    private val _isCheckingIn = MutableStateFlow(false)

    private val bleTelemetryFlow = combine(
        bleSessionManager.connectionState,
        bleSessionManager.connectionStep,
        bleSessionManager.hasRequiredGattService,
        bleSessionManager.lastHelloTimestampMs,
        bleSessionManager.batteryPercent
    ) { connState, connStep, hasGatt, lastHello, battery ->
        BleTelemetry(connState, connStep, hasGatt, lastHello, battery)
    }

    private val setupInfoFlow = combine(
        onboardingRepository.isOnboardingCompleted,
        onboardingRepository.pairedDeviceAddress,
        onboardingRepository.pairedDeviceName
    ) { done, addr, name ->
        DeviceSetupInfo(done, addr, name)
    }

    private val guardiansFlow: Flow<List<Guardian>> = combine(
        guardiansRepository.slot0,
        guardiansRepository.slot1
    ) { g0, g1 ->
        listOfNotNull(g0, g1)
    }

    private val ledgerFlow: Flow<List<RequestLedgerEntry>> = ledgerRepository.recentEntries

    private val safeWalkFlow: Flow<SafeWalkState> = safeWalkManager?.state ?: MutableStateFlow(SafeWalkState.Idle)
    private val disconnectEventFlow: Flow<DisconnectEvent?> = antiLossManager?.lastDisconnectEvent ?: MutableStateFlow(null)

    private val checkInStateFlow = combine(_checkInResult, _checkInError, _isCheckingIn) { result, error, inProgress ->
        Triple(result, error, inProgress)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        permissionsRepository.permissions,
        bleTelemetryFlow,
        guardiansFlow,
        ledgerFlow,
        setupInfoFlow,
        deviceStateRepository.deviceStatus,
        deviceStateRepository.syncStatus,
        safeWalkFlow,
        disconnectEventFlow,
        checkInStateFlow
    ) { params ->
        val snapshot = params[0] as PermissionsSnapshot
        val ble = params[1] as BleTelemetry
        @Suppress("UNCHECKED_CAST")
        val guardians = params[2] as List<Guardian>
        @Suppress("UNCHECKED_CAST")
        val entries = params[3] as List<RequestLedgerEntry>
        val setup = params[4] as DeviceSetupInfo
        val devStatus = params[5] as DeviceStatus
        val syncStat = params[6] as SyncStatus
        val safeWalk = params[7] as SafeWalkState
        val disconnectEvt = params[8] as DisconnectEvent?
        @Suppress("UNCHECKED_CAST")
        val checkInTuple = params[9] as Triple<SafeCheckInResult?, String?, Boolean>
        val (checkInRes, checkInErr, isChecking) = checkInTuple

        val currentTime = System.currentTimeMillis()

        // Derive truthful readiness per PRD Section 3 & Master Prompt Task 4/14
        val derivedReadiness = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = snapshot.bluetoothAdapterEnabled,
            connectionStep = ble.connectionStep,
            hasRequiredGattService = ble.hasRequiredGattService,
            lastHelloTimestampMs = ble.lastHelloTimestampMs,
            currentTimeMs = currentTime,
            permissions = snapshot
        )

        // Primary guardian extraction
        val primary = guardians.find { it.slotIndex == 0 }

        // Action summary
        val pendingCount = entries.count { it.status == RequestLedgerStatus.Received || it.status == RequestLedgerStatus.Executing }
        val latestEntry = entries.firstOrNull()
        val lastActionSummary = latestEntry?.let {
            val op = it.type.name.lowercase()
            val st = it.status.name.lowercase()
            "Latest: $op ($st)"
        }

        // Inline failure detection - concise and near relevant tiles
        val safetyError = when {
            !snapshot.smsGranted && !snapshot.callGranted ->
                "SMS and Call permissions revoked. Watch emergency actions will fail."
            !snapshot.smsGranted ->
                "SMS permission revoked. Emergency SMS alerts cannot be sent."
            !snapshot.callGranted ->
                "Call permission revoked. Direct emergency calls cannot be placed."
            snapshot.locationState == LocationPermissionState.Denied ->
                "Location denied. Alerts will not contain fresh GPS coordinates."
            else -> null
        }

        val guardiansError = if (guardians.isEmpty()) {
            "No guardians configured. Add at least 1 contact for SOS alerts."
        } else null

        val connectionError = when {
            !snapshot.bluetoothAdapterEnabled ->
                "Bluetooth is turned off on this phone."
            ble.connectionState == BleConnectionState.Disconnected ->
                "Watch is disconnected. Automatic backoff reconnect is active."
            ble.connectionState == BleConnectionState.ConnectedServiceUnavailable ->
                "Awaiting mutual hello handshake (< 45s)."
            else -> null
        }

        HomeUiState(
            readiness = derivedReadiness,
            deviceStatus = devStatus,
            syncStatus = syncStat,
            connectionState = ble.connectionState,
            batteryPercent = ble.batteryPercent,
            lastSeenTimestampMs = ble.lastHelloTimestampMs,
            guardiansCount = guardians.size,
            primaryGuardianName = primary?.name,
            primaryGuardianPhone = primary?.phone,
            recentLedgerEntries = entries,
            pendingLedgerCount = pendingCount,
            lastActionSummary = lastActionSummary,
            inlineErrorSafety = safetyError,
            inlineErrorGuardians = guardiansError,
            inlineErrorConnection = connectionError,
            isOnboardingCompleted = setup.onboardingCompleted,
            pairedDeviceAddress = setup.pairedDeviceAddress,
            pairedDeviceName = setup.pairedDeviceName,
            safeWalkState = safeWalk,
            disconnectEvent = disconnectEvt,
            checkInResult = checkInRes,
            checkInError = checkInErr,
            isCheckingIn = isChecking
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState()
    )

    // Feature 2: Safe Walk Actions
    fun startSafeWalk(durationMinutes: Int) {
        safeWalkManager?.startJourney(durationMinutes)
    }

    fun extendSafeWalk(extraMinutes: Int = 10) {
        safeWalkManager?.extend(extraMinutes)
    }

    fun arriveSafelySafeWalk() {
        safeWalkManager?.arriveSafely()
    }

    fun cancelSafeWalk() {
        safeWalkManager?.cancel()
    }

    fun resetSafeWalk() {
        safeWalkManager?.reset()
    }

    // Feature 3: Anti-Loss Actions
    fun clearSeparationAlert() {
        antiLossManager?.clearAlert()
    }

    // Feature 4: Safe Check-In Actions
    fun sendSafeCheckIn() {
        if (_isCheckingIn.value) return
        scope.launch {
            _isCheckingIn.value = true
            _checkInError.value = null
            _checkInResult.value = null
            val result = safeCheckInManager?.sendSafeCheckIn()
            if (result != null) {
                result.onSuccess {
                    _checkInResult.value = it
                }.onFailure {
                    _checkInError.value = it.message ?: "Failed to send check-in SMS"
                }
            } else {
                _checkInError.value = "Safe Check-In service unavailable"
            }
            _isCheckingIn.value = false
        }
    }

    fun dismissCheckInMessage() {
        _checkInResult.value = null
        _checkInError.value = null
    }

    companion object {
        private fun createFallbackGuardiansRepo(): GuardiansRepository = object : GuardiansRepository {
            override val slot0 = MutableStateFlow<Guardian?>(null)
            override val slot1 = MutableStateFlow<Guardian?>(null)
            override val syncState = MutableStateFlow(com.naari.companion.guardians.GuardianSyncState.Empty)
            override val isPendingSync = MutableStateFlow(false)
            override suspend fun saveGuardian(slotIndex: Int, name: String, phone: String) = Result.success(Unit)
            override suspend fun clearGuardian(slotIndex: Int) = Result.success(Unit)
            override suspend fun swapGuardians() = Result.success(Unit)
            override suspend fun syncProfileToWatch() = Result.success(Unit)
        }

        private fun createFallbackLedgerRepo(): RequestLedgerRepository = object : RequestLedgerRepository {
            override val recentEntries = MutableStateFlow(emptyList<RequestLedgerEntry>())
            override suspend fun submitRequest(request: WatchRequest, deviceAddress: String, timestamp: Long) =
                LedgerSubmissionResult.Accepted(RequestLedgerEntry(1L, request.id, deviceAddress, request.type))
            override suspend fun transitionStatus(entryId: Long, newStatus: RequestLedgerStatus, detail: String?, failureReason: String?, timestamp: Long) =
                Result.success(RequestLedgerEntry(id = entryId, watchRequestId = 1L, deviceAddress = "addr", type = WatchRequestType.SMS, status = newStatus))
            override suspend fun getById(entryId: Long): RequestLedgerEntry? = null
            override suspend fun getByWatchRequestId(watchRequestId: Long): List<RequestLedgerEntry> = emptyList()
            override suspend fun recoverInflightOnStartup(timestamp: Long): Int = 0
        }

        private fun createFallbackOnboardingRepo(): OnboardingRepository = object : OnboardingRepository {
            override val isOnboardingCompleted = MutableStateFlow(true)
            override val pairedDeviceAddress = MutableStateFlow<String?>(null)
            override val pairedDeviceName = MutableStateFlow<String?>(null)
            override suspend fun setOnboardingCompleted(completed: Boolean) {}
            override suspend fun savePairedDevice(address: String, name: String) {}
            override suspend fun clearPairedDevice() {}
            override suspend fun resetOnboarding() {}
        }
    }
}
