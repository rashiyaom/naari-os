package com.naari.companion

import com.naari.companion.ble.BleConnectionStep
import com.naari.companion.ble.ReadinessDeriver
import com.naari.companion.ble.ReadinessState
import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsSnapshot
import com.naari.companion.protocol.ProtocolConstants
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessDerivationTest {

    private val baseNow = 1_000_000L

    @Test
    fun derive_whenBluetoothDisabled_returnsBluetoothOff() {
        val state = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = false,
            connectionStep = BleConnectionStep.ConnectedBonded,
            hasRequiredGattService = true,
            lastHelloTimestampMs = baseNow,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.BluetoothOff, state)
    }

    @Test
    fun derive_whenDisconnected_returnsDisconnected() {
        val state = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.Disconnected,
            hasRequiredGattService = false,
            lastHelloTimestampMs = null,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.Disconnected, state)
    }

    @Test
    fun derive_whenConnecting_returnsConnecting() {
        val state = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.Connecting,
            hasRequiredGattService = false,
            lastHelloTimestampMs = null,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.Connecting, state)
    }

    @Test
    fun derive_whenConnectedBondedWithService_returnsPaired() {
        val state = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ConnectedBonded,
            hasRequiredGattService = true,
            lastHelloTimestampMs = null,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.Paired, state)
    }

    @Test
    fun derive_whenConnectedServiceUnavailable_returnsServiceUnavailable() {
        val state = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = false,
            lastHelloTimestampMs = baseNow,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.ConnectedServiceUnavailable, state)
    }

    @Test
    fun derive_whenHelloExpiredBeyond45Seconds_returnsConnectedServiceUnavailable() {
        val expiredHello = baseNow - (ProtocolConstants.READINESS_EXPIRATION_MS + 1000)
        val state = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = true,
            lastHelloTimestampMs = expiredHello,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.ConnectedServiceUnavailable, state)
    }

    @Test
    fun derive_whenFreshHelloAndAllPermissionsGranted_returnsReady() {
        val freshHello = baseNow - 5_000L // 5 seconds ago
        val state = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = true,
            lastHelloTimestampMs = freshHello,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.Ready, state)
    }

    @Test
    fun derive_whenPermissionsRevoked_transitionsToPermissionLimitedWithoutCrash() {
        val freshHello = baseNow - 2_000L

        // 1. Initially Ready
        val readyState = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = true,
            lastHelloTimestampMs = freshHello,
            currentTimeMs = baseNow,
            permissions = PermissionsSnapshot.ALL_GRANTED
        )
        assertEquals(ReadinessState.Ready, readyState)

        // 2. Revoke SMS
        val noSmsSnapshot = PermissionsSnapshot.ALL_GRANTED.copy(smsGranted = false)
        val afterRevokeSms = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = true,
            lastHelloTimestampMs = freshHello,
            currentTimeMs = baseNow,
            permissions = noSmsSnapshot
        )
        assertEquals(ReadinessState.PermissionLimited, afterRevokeSms)

        // 3. Revoke Call
        val noCallSnapshot = PermissionsSnapshot.ALL_GRANTED.copy(callGranted = false)
        val afterRevokeCall = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = true,
            lastHelloTimestampMs = freshHello,
            currentTimeMs = baseNow,
            permissions = noCallSnapshot
        )
        assertEquals(ReadinessState.PermissionLimited, afterRevokeCall)

        // 4. Revoke Location
        val noLocSnapshot = PermissionsSnapshot.ALL_GRANTED.copy(locationState = LocationPermissionState.Denied)
        val afterRevokeLoc = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = true,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = true,
            lastHelloTimestampMs = freshHello,
            currentTimeMs = baseNow,
            permissions = noLocSnapshot
        )
        assertEquals(ReadinessState.PermissionLimited, afterRevokeLoc)
    }

    @Test
    fun flowSimulation_reactiveRevocationUpdatesCleanly() {
        val flow = MutableStateFlow(PermissionsSnapshot.ALL_GRANTED)
        val freshHello = baseNow - 1_000L

        fun checkState(): ReadinessState = ReadinessDeriver.derive(
            bluetoothAdapterEnabled = flow.value.bluetoothAdapterEnabled,
            connectionStep = BleConnectionStep.ServiceDiscovered,
            hasRequiredGattService = true,
            lastHelloTimestampMs = freshHello,
            currentTimeMs = baseNow,
            permissions = flow.value
        )

        assertEquals(ReadinessState.Ready, checkState())

        // Simulate runtime permission revocation by Android OS
        flow.value = flow.value.copy(smsGranted = false)
        assertEquals(ReadinessState.PermissionLimited, checkState())

        // Simulate user toggling Bluetooth off in quick settings
        flow.value = flow.value.copy(bluetoothAdapterEnabled = false)
        assertEquals(ReadinessState.BluetoothOff, checkState())
    }
}
