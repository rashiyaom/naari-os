package com.naari.companion.data

import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.ReadinessDeriver
import com.naari.companion.ble.ReadinessState
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.protocol.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative implementation of [DeviceStateRepository].
 * Fuses inputs from BLE session, Permissions, Bonded Device, and Guardians into
 * a single reactive [DeviceStatus], [SyncStatus], and [ReadinessState].
 */
@Singleton
class DefaultDeviceStateRepository @Inject constructor(
    private val bleSessionManager: BleSessionManager,
    private val permissionsRepository: PermissionsRepository,
    private val onboardingRepository: OnboardingRepository,
    private val guardiansRepository: GuardiansRepository,
    externalScope: CoroutineScope? = null
) : DeviceStateRepository {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.UpToDate())
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTimestampMs = MutableStateFlow<Long?>(null)
    override val lastSyncTimestampMs: StateFlow<Long?> = _lastSyncTimestampMs.asStateFlow()

    override val batteryPercent: StateFlow<Int?> = bleSessionManager.batteryPercent
    override val pairedDeviceAddress: StateFlow<String?> = onboardingRepository.pairedDeviceAddress
        .stateIn(scope, SharingStarted.Eagerly, null)
    override val pairedDeviceName: StateFlow<String?> = onboardingRepository.pairedDeviceName
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val derivationFlow = combine(
        onboardingRepository.pairedDeviceAddress,
        permissionsRepository.permissions,
        bleSessionManager.connectionStep,
        bleSessionManager.hasRequiredGattService,
        bleSessionManager.lastHelloTimestampMs
    ) { addr, perms, step, gatt, hello ->
        Triple(addr, perms, Triple(step, gatt, hello))
    }

    private val guardiansConfiguredFlow = combine(
        guardiansRepository.slot0,
        guardiansRepository.slot1
    ) { g0, g1 ->
        g0 != null || g1 != null
    }

    override val deviceStatus: StateFlow<DeviceStatus> = combine(
        derivationFlow,
        guardiansConfiguredFlow
    ) { (addr, perms, stepGattHello), hasGuardians ->
        val (step, gatt, hello) = stepGattHello
        deriveDeviceStatus(
            pairedAddress = addr,
            bluetoothEnabled = perms.bluetoothAdapterEnabled,
            connectionStep = step,
            hasGattService = gatt,
            lastHelloMs = hello,
            smsGranted = perms.smsGranted,
            callGranted = perms.callGranted,
            locationState = perms.locationState,
            hasGuardians = hasGuardians,
            currentTimeMs = System.currentTimeMillis()
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DeviceStatus.NotPaired
    )

    override val readinessState: StateFlow<ReadinessState> = combine(
        permissionsRepository.permissions,
        bleSessionManager.connectionStep,
        bleSessionManager.hasRequiredGattService,
        bleSessionManager.lastHelloTimestampMs,
        guardiansConfiguredFlow
    ) { perms, step, gatt, hello, hasGuardians ->
        val baseReadiness = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = perms.bluetoothAdapterEnabled,
            connectionStep = step,
            hasRequiredGattService = gatt,
            lastHelloTimestampMs = hello,
            currentTimeMs = System.currentTimeMillis(),
            permissions = perms
        )
        if (baseReadiness == ReadinessState.Ready && !hasGuardians) {
            ReadinessState.PermissionLimited
        } else {
            baseReadiness
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ReadinessState.Disconnected
    )

    override fun notifySyncStarted() {
        _syncStatus.value = SyncStatus.Updating
    }

    override fun notifySyncCompleted(timestampMs: Long) {
        _syncStatus.value = SyncStatus.UpToDate(timestampMs)
        _lastSyncTimestampMs.value = timestampMs
    }

    override fun notifySyncFailed(reason: String) {
        _syncStatus.value = SyncStatus.Failed(reason)
    }

    companion object {
        fun deriveDeviceStatus(
            pairedAddress: String?,
            bluetoothEnabled: Boolean,
            connectionStep: BleConnectionStep,
            hasGattService: Boolean,
            lastHelloMs: Long?,
            smsGranted: Boolean,
            callGranted: Boolean,
            locationState: LocationPermissionState,
            hasGuardians: Boolean,
            currentTimeMs: Long = System.currentTimeMillis()
        ): DeviceStatus {
            if (!bluetoothEnabled) {
                return DeviceStatus.BluetoothOff
            }
            if (pairedAddress == null) {
                return DeviceStatus.NotPaired
            }
            when (connectionStep) {
                BleConnectionStep.Connecting -> return DeviceStatus.Connecting
                BleConnectionStep.Disconnected -> return DeviceStatus.NeedsAttention("Watch is disconnected. Move closer to reconnect.")
                BleConnectionStep.ConnectedBonded -> {
                    if (!hasGattService) {
                        return DeviceStatus.NeedsAttention("Watch service unavailable. Check watch.")
                    }
                }
                BleConnectionStep.ServiceDiscovered -> {
                    if (!hasGattService) {
                        return DeviceStatus.NeedsAttention("Watch service unavailable. Check watch.")
                    }
                }
            }

            // Hello handshake freshness check (45s window)
            if (lastHelloMs == null) {
                return DeviceStatus.Connected
            }
            val ageMs = currentTimeMs - lastHelloMs
            if (ageMs < 0 || ageMs > ProtocolConstants.READINESS_EXPIRATION_MS) {
                return DeviceStatus.NeedsAttention("Awaiting response from watch.")
            }

            // Required permissions check
            if (!smsGranted || !callGranted || locationState == LocationPermissionState.Denied) {
                return DeviceStatus.NeedsAttention("Allow required permissions to enable safety alerts.")
            }

            // Minimum 1 guardian check
            if (!hasGuardians) {
                return DeviceStatus.NeedsAttention("Add at least one emergency contact.")
            }

            return DeviceStatus.Ready
        }
    }
}
