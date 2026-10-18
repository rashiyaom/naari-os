package com.naari.companion.onboarding

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.ble.AssociationCallback
import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.CompanionDeviceManagerHelper
import com.naari.companion.ble.ReadinessDeriver
import com.naari.companion.ble.ReadinessState
import com.naari.companion.data.Guardian
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.permissions.PermissionsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Visual state of the BLE pairing step.
 */
sealed interface PairingVisualState {
    object Idle : PairingVisualState
    object Searching : PairingVisualState
    data class DeviceFound(val address: String, val name: String) : PairingVisualState
    data class Pairing(val address: String, val name: String, val stepMessage: String) : PairingVisualState
    data class PreviouslyPaired(val address: String, val name: String) : PairingVisualState
    data class Connected(val address: String, val name: String) : PairingVisualState
    data class Error(val message: String) : PairingVisualState
}

/**
 * UI State for the multi-step onboarding wizard.
 */
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.Welcome,
    val permissions: PermissionsSnapshot = PermissionsSnapshot.ALL_DENIED,
    val pairedAddress: String? = null,
    val pairedName: String? = null,
    val guardians: List<Guardian> = emptyList(),
    val readinessState: ReadinessState = ReadinessState.Disconnected,
    val isOnboardingCompleted: Boolean = false,
    val isPairing: Boolean = false,
    val pairingMessage: String? = null,
    val pairingVisualState: PairingVisualState = PairingVisualState.Idle
)

private data class StepProgress(
    val currentStep: OnboardingStep,
    val isPairing: Boolean,
    val pairingMessage: String?,
    val visualState: PairingVisualState
)

private data class PairedDeviceInfo(
    val address: String?,
    val name: String?,
    val isCompleted: Boolean
)

private data class BleProgress(
    val connectionState: com.naari.companion.ble.BleConnectionState,
    val connectionStep: BleConnectionStep,
    val hasRequiredGattService: Boolean,
    val lastHelloTimestampMs: Long?
)

/**
 * ViewModel driving the 6-step Onboarding flow:
 * install → pair → grant capabilities → set guardians → verify readiness → wear watch.
 * Ref: PRD Section 1 & Master Prompt Task 14.
 */
@HiltViewModel
class OnboardingViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val permissionsRepository: PermissionsRepository,
    private val guardiansRepository: GuardiansRepository,
    private val bleSessionManager: BleSessionManager,
    private val cdmHelper: CompanionDeviceManagerHelper? = null,
    externalScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    // Overload for 4-argument test construction
    constructor(
        onboardingRepository: OnboardingRepository,
        permissionsRepository: PermissionsRepository,
        guardiansRepository: GuardiansRepository,
        bleSessionManager: BleSessionManager,
        externalScope: kotlinx.coroutines.CoroutineScope?
    ) : this(
        onboardingRepository = onboardingRepository,
        permissionsRepository = permissionsRepository,
        guardiansRepository = guardiansRepository,
        bleSessionManager = bleSessionManager,
        cdmHelper = null,
        externalScope = externalScope
    )

    @Inject
    constructor(
        onboardingRepository: OnboardingRepository,
        permissionsRepository: PermissionsRepository,
        guardiansRepository: GuardiansRepository,
        bleSessionManager: BleSessionManager,
        cdmHelper: CompanionDeviceManagerHelper
    ) : this(
        onboardingRepository = onboardingRepository,
        permissionsRepository = permissionsRepository,
        guardiansRepository = guardiansRepository,
        bleSessionManager = bleSessionManager,
        cdmHelper = cdmHelper,
        externalScope = null
    )

    private val scope = externalScope ?: viewModelScope

    private val _currentStep = MutableStateFlow<OnboardingStep>(OnboardingStep.Welcome)
    private val _isPairing = MutableStateFlow(false)
    private val _pairingMessage = MutableStateFlow<String?>(null)
    private val _pairingVisualState = MutableStateFlow<PairingVisualState>(PairingVisualState.Idle)

    private val _cdmIntentSender = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val cdmIntentSender: SharedFlow<IntentSender> = _cdmIntentSender.asSharedFlow()

    private val stepProgressFlow = combine(
        _currentStep,
        _isPairing,
        _pairingMessage,
        _pairingVisualState
    ) { currentStep, isPairing, pairingMsg, visualState ->
        StepProgress(currentStep, isPairing, pairingMsg, visualState)
    }

    private val deviceInfoFlow = combine(
        onboardingRepository.pairedDeviceAddress,
        onboardingRepository.pairedDeviceName,
        onboardingRepository.isOnboardingCompleted
    ) { addr, name, completed ->
        PairedDeviceInfo(addr, name, completed)
    }

    private val guardiansFlow: Flow<List<Guardian>> = combine(guardiansRepository.slot0, guardiansRepository.slot1) { g0, g1 ->
        listOfNotNull(g0, g1)
    }

    private val bleProgressFlow = combine(
        bleSessionManager.connectionState,
        bleSessionManager.connectionStep,
        bleSessionManager.hasRequiredGattService,
        bleSessionManager.lastHelloTimestampMs
    ) { connectionState, connectionStep, hasRequiredGattService, lastHelloTimestampMs ->
        BleProgress(connectionState, connectionStep, hasRequiredGattService, lastHelloTimestampMs)
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        stepProgressFlow,
        permissionsRepository.permissions,
        deviceInfoFlow,
        guardiansFlow,
        bleProgressFlow
    ) { stepProgress: StepProgress, permissions: PermissionsSnapshot, device: PairedDeviceInfo,
        guardians: List<Guardian>, ble: BleProgress ->
        val derivedReadiness = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = permissions.bluetoothAdapterEnabled,
            connectionStep = ble.connectionStep,
            hasRequiredGattService = ble.hasRequiredGattService,
            lastHelloTimestampMs = ble.lastHelloTimestampMs,
            currentTimeMs = System.currentTimeMillis(),
            permissions = permissions
        )

        // A stored association is not the same as a live authenticated GATT handshake.
        val resolvedVisualState = when {
            stepProgress.visualState is PairingVisualState.Searching ||
                stepProgress.visualState is PairingVisualState.DeviceFound ||
                stepProgress.visualState is PairingVisualState.Pairing ||
                stepProgress.visualState is PairingVisualState.Error -> stepProgress.visualState
            device.address == null -> PairingVisualState.Idle
            ble.connectionState == com.naari.companion.ble.BleConnectionState.Ready ->
                PairingVisualState.Connected(device.address, device.name ?: "Naari Watch")
            else -> PairingVisualState.PreviouslyPaired(
                device.address,
                device.name ?: "Naari Watch"
            )
        }

        OnboardingUiState(
            currentStep = stepProgress.currentStep,
            permissions = permissions,
            pairedAddress = device.address,
            pairedName = device.name,
            guardians = guardians,
            readinessState = derivedReadiness,
            isOnboardingCompleted = device.isCompleted,
            isPairing = stepProgress.isPairing,
            pairingMessage = stepProgress.pairingMessage,
            pairingVisualState = resolvedVisualState
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = OnboardingUiState()
    )

    fun goToStep(step: OnboardingStep) {
        _currentStep.value = step
    }

    fun nextStep() {
        val nextIndex = _currentStep.value.stepIndex + 1
        if (nextIndex < OnboardingStep.allSteps.size) {
            _currentStep.value = OnboardingStep.fromIndex(nextIndex)
        }
    }

    fun previousStep() {
        val prevIndex = _currentStep.value.stepIndex - 1
        if (prevIndex >= 0) {
            _currentStep.value = OnboardingStep.fromIndex(prevIndex)
        }
    }

    /**
     * Initiates searching for Naari Watch via CompanionDeviceManager.
     */
    fun startSearching() {
        val permissions = permissionsRepository.permissions.value
        if (!permissions.bluetoothAdapterEnabled) {
            onPairingError("Bluetooth is turned off. Turn it on, then search again.")
            return
        }
        if (!permissions.bluetoothGranted) {
            onPairingError("Allow Nearby devices access before pairing your watch.")
            return
        }
        _pairingVisualState.value = PairingVisualState.Searching
        _pairingMessage.value = "Searching for Naari Watch..."

        val helper = cdmHelper
        if (helper != null && helper.isAvailable()) {
            helper.associate(object : AssociationCallback {
                override fun onIntentSenderReady(intentSender: IntentSender) {
                    _cdmIntentSender.tryEmit(intentSender)
                }

                override fun onFailure(error: String) {
                    onPairingError(error)
                }
            })
        }
    }

    fun cancelSearching() {
        _pairingVisualState.value = PairingVisualState.Idle
        _pairingMessage.value = null
    }

    fun onDeviceDiscovered(address: String, name: String) {
        _pairingVisualState.value = PairingVisualState.DeviceFound(address, name)
        _pairingMessage.value = "Found $name ($address)"
    }

    fun onPairingError(message: String) {
        val friendlyMessage = friendlyPairingError(message)
        _pairingVisualState.value = PairingVisualState.Error(friendlyMessage)
        _pairingMessage.value = friendlyMessage
    }

    /**
     * Executes pairing, bonding, and initial connection to the selected watch.
     */
    fun confirmAndPair(address: String, name: String) {
        scope.launch {
            _isPairing.value = true
            _pairingVisualState.value = PairingVisualState.Pairing(address, name, "Confirm passkey on your watch...")
            _pairingMessage.value = "Pairing with $name..."

            try {
                bleSessionManager.connect(address)
                kotlinx.coroutines.withTimeout(110000) {
                    bleSessionManager.connectionState.first { it == com.naari.companion.ble.BleConnectionState.Ready }
                }
                onboardingRepository.savePairedDevice(address, name)
                _pairingVisualState.value = PairingVisualState.Connected(address, name)
                _pairingMessage.value = "Watch handshake complete"
            } catch(e: kotlinx.coroutines.CancellationException) {
                if(e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                bleSessionManager.disconnect()
                _pairingVisualState.value = PairingVisualState.Error("Pairing timed out. Open Phone Link → Start pairing on the watch and retry.")
            } catch(e: Exception) {
                onPairingError(e.message ?: "Pairing failed")
            } finally { _isPairing.value = false }

        }
    }

    fun pairDevice(address: String, name: String) {
        confirmAndPair(address, name)
    }

    fun clearPairedDevice() {
        scope.launch {
            val currentAddress = onboardingRepository.pairedDeviceAddress.firstOrNull()
            if (currentAddress != null) {
                cdmHelper?.disassociate(currentAddress)
            }
            onboardingRepository.clearPairedDevice()
            bleSessionManager?.disconnect()
            _pairingVisualState.value = PairingVisualState.Idle
            _pairingMessage.value = null
        }
    }

    fun refreshPermissions() {
        permissionsRepository.refreshPermissions()
    }

    fun saveGuardian(slotIndex: Int, name: String, phone: String) {
        scope.launch {
            guardiansRepository.saveGuardian(slotIndex, name, phone)
        }
    }

    fun clearGuardian(slotIndex: Int) {
        scope.launch {
            guardiansRepository.clearGuardian(slotIndex)
        }
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        scope.launch {
            onboardingRepository.setOnboardingCompleted(true)
            onFinished()
        }
    }

    companion object {
        internal fun friendlyPairingError(raw: String): String {
            val normalized = raw.lowercase()
            return when {
                "companion_device_setup" in normalized || "uses-feature" in normalized ->
                    "Android pairing support was unavailable in this app build. Install Naari Companion 1.2 or newer."
                "permission" in normalized || "not allowed" in normalized || "securityexception" in normalized ->
                    "Bluetooth access is required. Allow Nearby devices, then search again."
                "cancel" in normalized ->
                    "Pairing was cancelled. Keep the watch pairing screen open and try again."
                "timeout" in normalized || "timed out" in normalized ->
                    "The watch did not complete the secure handshake. Open Phone Link → Start pairing on the watch, then retry."
                "not supported" in normalized ->
                    "This phone does not provide Android's companion-device pairing service."
                else -> "Could not connect to the watch. Keep Phone Link open on the watch and try again."
            }
        }

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

        private fun createFallbackBleSessionManager(): BleSessionManager = object : BleSessionManager {
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
    }
}
