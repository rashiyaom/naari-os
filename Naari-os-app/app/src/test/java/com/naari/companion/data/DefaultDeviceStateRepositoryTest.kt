package com.naari.companion.data

import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.ReadinessState
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.permissions.PermissionsSnapshot
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.ProtocolConstants
import com.naari.companion.protocol.WatchRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying authoritative single source of truth state derivation
 * in [DefaultDeviceStateRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDeviceStateRepositoryTest {

    private class FakePermissionsRepository : PermissionsRepository {
        val permissionsFlow = MutableStateFlow(
            PermissionsSnapshot(
                bluetoothGranted = true,
                bluetoothAdapterEnabled = true,
                smsGranted = true,
                callGranted = true,
                locationState = LocationPermissionState.Precise,
                locationServiceEnabled = true,
                notificationsGranted = true,
                contactsGranted = true
            )
        )
        override val permissions: StateFlow<PermissionsSnapshot> = permissionsFlow.asStateFlow()
        override fun refreshPermissions() {}
        override fun getRequiredBluetoothPermissions(): List<String> = emptyList()
        override fun getRequiredSafetyPermissions(): List<String> = emptyList()
    }

    private class FakeBleSessionManager : BleSessionManager {
        val connectionStateFlow = MutableStateFlow<BleConnectionState>(BleConnectionState.Ready)
        override val connectionState: StateFlow<BleConnectionState> = connectionStateFlow.asStateFlow()

        val incomingRequestsFlow = MutableSharedFlow<WatchRequest>(replay = 1)
        override val incomingRequests: SharedFlow<WatchRequest> = incomingRequestsFlow

        val negotiatedMtuFlow = MutableStateFlow(512)
        override val negotiatedMtu: StateFlow<Int> = negotiatedMtuFlow.asStateFlow()

        val lastHelloFlow = MutableStateFlow<Long?>(System.currentTimeMillis())
        override val lastHelloTimestampMs: StateFlow<Long?> = lastHelloFlow.asStateFlow()

        val connectionStepFlow = MutableStateFlow(BleConnectionStep.ServiceDiscovered)
        override val connectionStep: StateFlow<BleConnectionStep> = connectionStepFlow.asStateFlow()

        val hasRequiredGattServiceFlow = MutableStateFlow(true)
        override val hasRequiredGattService: StateFlow<Boolean> = hasRequiredGattServiceFlow.asStateFlow()

        val batteryPercentFlow = MutableStateFlow<Int?>(85)
        override val batteryPercent: StateFlow<Int?> = batteryPercentFlow.asStateFlow()

        override fun connect(deviceAddress: String) {}
        override suspend fun sendReply(reply: PhoneReply): Result<Unit> = Result.success(Unit)
        override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> = Result.success(Unit)
        override fun disconnect() {}
    }

    private class FakeGuardiansRepository : GuardiansRepository {
        val slot0Flow = MutableStateFlow<Guardian?>(
            Guardian(slotIndex = 0, name = "Mother", phone = "9876543210")
        )
        val slot1Flow = MutableStateFlow<Guardian?>(null)
        override val slot0: StateFlow<Guardian?> = slot0Flow.asStateFlow()
        override val slot1: StateFlow<Guardian?> = slot1Flow.asStateFlow()
        override val syncState: StateFlow<com.naari.companion.guardians.GuardianSyncState> =
            MutableStateFlow(com.naari.companion.guardians.GuardianSyncState.Empty)
        override val isPendingSync: StateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun saveGuardian(slotIndex: Int, name: String, phone: String): Result<Unit> {
            val g = Guardian(slotIndex = slotIndex, name = name, phone = phone)
            if (slotIndex == 0) slot0Flow.value = g else slot1Flow.value = g
            return Result.success(Unit)
        }

        override suspend fun clearGuardian(slotIndex: Int): Result<Unit> {
            if (slotIndex == 0) slot0Flow.value = null else slot1Flow.value = null
            return Result.success(Unit)
        }

        override suspend fun syncProfileToWatch(): Result<Unit> = Result.success(Unit)

        override suspend fun swapGuardians(): Result<Unit> {
            val temp = slot0Flow.value
            slot0Flow.value = slot1Flow.value?.copy(slotIndex = 0)
            slot1Flow.value = temp?.copy(slotIndex = 1)
            return Result.success(Unit)
        }
    }

    private class FakeOnboardingRepository : OnboardingRepository {
        val completedFlow = MutableStateFlow(true)
        override val isOnboardingCompleted: Flow<Boolean> = completedFlow

        val addressFlow = MutableStateFlow<String?>("AA:BB:CC:DD:EE:FF")
        override val pairedDeviceAddress: Flow<String?> = addressFlow

        val nameFlow = MutableStateFlow<String?>("Naari Watch")
        override val pairedDeviceName: Flow<String?> = nameFlow

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            completedFlow.value = completed
        }

        override suspend fun savePairedDevice(address: String, name: String) {
            addressFlow.value = address
            nameFlow.value = name
        }

        override suspend fun clearPairedDevice() {
            addressFlow.value = null
            nameFlow.value = null
        }

        override suspend fun resetOnboarding() {
            completedFlow.value = false
            addressFlow.value = null
            nameFlow.value = null
        }
    }

    @Test
    fun deviceStatus_allRequirementsMet_producesReady() = runTest {
        val perms = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val onboarding = FakeOnboardingRepository()
        val guardians = FakeGuardiansRepository()

        val repo = DefaultDeviceStateRepository(
            bleSessionManager = ble,
            permissionsRepository = perms,
            onboardingRepository = onboarding,
            guardiansRepository = guardians,
            externalScope = backgroundScope
        )
        runCurrent()

        assertEquals(DeviceStatus.Ready, repo.deviceStatus.value)
        assertTrue(repo.deviceStatus.value.isReady)
        assertTrue(repo.deviceStatus.value.isConnected)
    }

    @Test
    fun deviceStatus_bluetoothDisabled_derivesBluetoothOffAndRestoresOnReenable() = runTest {
        val perms = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val onboarding = FakeOnboardingRepository()
        val guardians = FakeGuardiansRepository()

        val repo = DefaultDeviceStateRepository(
            bleSessionManager = ble,
            permissionsRepository = perms,
            onboardingRepository = onboarding,
            guardiansRepository = guardians,
            externalScope = backgroundScope
        )
        runCurrent()
        assertEquals(DeviceStatus.Ready, repo.deviceStatus.value)

        // Disable Bluetooth adapter
        perms.permissionsFlow.value = perms.permissionsFlow.value.copy(bluetoothAdapterEnabled = false)
        runCurrent()
        assertEquals(DeviceStatus.BluetoothOff, repo.deviceStatus.value)
        assertFalse(repo.deviceStatus.value.isReady)

        // Re-enable Bluetooth adapter
        perms.permissionsFlow.value = perms.permissionsFlow.value.copy(bluetoothAdapterEnabled = true)
        runCurrent()
        assertEquals(DeviceStatus.Ready, repo.deviceStatus.value)
    }

    @Test
    fun deviceStatus_notPaired_derivesNotPaired() = runTest {
        val perms = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val onboarding = FakeOnboardingRepository()
        val guardians = FakeGuardiansRepository()

        onboarding.addressFlow.value = null

        val repo = DefaultDeviceStateRepository(
            bleSessionManager = ble,
            permissionsRepository = perms,
            onboardingRepository = onboarding,
            guardiansRepository = guardians,
            externalScope = backgroundScope
        )
        runCurrent()

        assertEquals(DeviceStatus.NotPaired, repo.deviceStatus.value)
        assertFalse(repo.deviceStatus.value.isConnected)
    }

    @Test
    fun deviceStatus_disconnected_derivesNeedsAttention() = runTest {
        val perms = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val onboarding = FakeOnboardingRepository()
        val guardians = FakeGuardiansRepository()

        ble.connectionStepFlow.value = BleConnectionStep.Disconnected

        val repo = DefaultDeviceStateRepository(
            bleSessionManager = ble,
            permissionsRepository = perms,
            onboardingRepository = onboarding,
            guardiansRepository = guardians,
            externalScope = backgroundScope
        )
        runCurrent()

        assertTrue(repo.deviceStatus.value is DeviceStatus.NeedsAttention)
        val attention = repo.deviceStatus.value as DeviceStatus.NeedsAttention
        assertTrue(attention.reason.contains("disconnected", ignoreCase = true))
    }

    @Test
    fun deviceStatus_connecting_derivesConnecting() = runTest {
        val perms = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val onboarding = FakeOnboardingRepository()
        val guardians = FakeGuardiansRepository()

        ble.connectionStepFlow.value = BleConnectionStep.Connecting

        val repo = DefaultDeviceStateRepository(
            bleSessionManager = ble,
            permissionsRepository = perms,
            onboardingRepository = onboarding,
            guardiansRepository = guardians,
            externalScope = backgroundScope
        )
        runCurrent()

        assertEquals(DeviceStatus.Connecting, repo.deviceStatus.value)
    }

    @Test
    fun deviceStatus_staleHelloHandshake_derivesNeedsAttention() = runTest {
        val now = 1_000_000L
        val staleHello = now - (ProtocolConstants.READINESS_EXPIRATION_MS + 5_000L) // 50s old (>45s)

        val status = DefaultDeviceStateRepository.deriveDeviceStatus(
            pairedAddress = "AA:BB:CC:DD:EE:FF",
            bluetoothEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasGattService = true,
            lastHelloMs = staleHello,
            smsGranted = true,
            callGranted = true,
            locationState = LocationPermissionState.Precise,
            hasGuardians = true,
            currentTimeMs = now
        )

        assertTrue(status is DeviceStatus.NeedsAttention)
        val attention = status as DeviceStatus.NeedsAttention
        assertEquals("Awaiting response from watch.", attention.reason)
    }

    @Test
    fun deviceStatus_missingPermissionsOrGuardians_preventsReady() = runTest {
        val now = 1_000_000L

        // Missing SMS permission
        val missingSmsStatus = DefaultDeviceStateRepository.deriveDeviceStatus(
            pairedAddress = "AA:BB:CC:DD:EE:FF",
            bluetoothEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasGattService = true,
            lastHelloMs = now,
            smsGranted = false,
            callGranted = true,
            locationState = LocationPermissionState.Precise,
            hasGuardians = true,
            currentTimeMs = now
        )
        assertTrue(missingSmsStatus is DeviceStatus.NeedsAttention)
        assertFalse(missingSmsStatus.isReady)

        // Missing Call permission
        val missingCallStatus = DefaultDeviceStateRepository.deriveDeviceStatus(
            pairedAddress = "AA:BB:CC:DD:EE:FF",
            bluetoothEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasGattService = true,
            lastHelloMs = now,
            smsGranted = true,
            callGranted = false,
            locationState = LocationPermissionState.Precise,
            hasGuardians = true,
            currentTimeMs = now
        )
        assertTrue(missingCallStatus is DeviceStatus.NeedsAttention)

        // Missing Guardians
        val missingGuardiansStatus = DefaultDeviceStateRepository.deriveDeviceStatus(
            pairedAddress = "AA:BB:CC:DD:EE:FF",
            bluetoothEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasGattService = true,
            lastHelloMs = now,
            smsGranted = true,
            callGranted = true,
            locationState = LocationPermissionState.Precise,
            hasGuardians = false,
            currentTimeMs = now
        )
        assertTrue(missingGuardiansStatus is DeviceStatus.NeedsAttention)
        assertEquals("Add at least one emergency contact.", (missingGuardiansStatus as DeviceStatus.NeedsAttention).reason)
    }

    @Test
    fun syncStatus_transitionsFromUpdatingToUpToDateToFailed() = runTest {
        val perms = FakePermissionsRepository()
        val ble = FakeBleSessionManager()
        val onboarding = FakeOnboardingRepository()
        val guardians = FakeGuardiansRepository()

        val repo = DefaultDeviceStateRepository(
            bleSessionManager = ble,
            permissionsRepository = perms,
            onboardingRepository = onboarding,
            guardiansRepository = guardians,
            externalScope = backgroundScope
        )

        // Initial state
        assertTrue(repo.syncStatus.value is SyncStatus.UpToDate)
        assertNull(repo.lastSyncTimestampMs.value)

        // Sync started
        repo.notifySyncStarted()
        assertEquals(SyncStatus.Updating, repo.syncStatus.value)

        // Sync completed
        val syncTime = 123456789L
        repo.notifySyncCompleted(syncTime)
        assertEquals(SyncStatus.UpToDate(syncTime), repo.syncStatus.value)
        assertEquals(syncTime, repo.lastSyncTimestampMs.value)

        // Sync failed
        repo.notifySyncFailed("BLE GATT write timeout")
        assertEquals(SyncStatus.Failed("BLE GATT write timeout"), repo.syncStatus.value)
    }
}
