package com.naari.companion.ble

import com.naari.companion.permissions.LocationPermissionState
import com.naari.companion.permissions.PermissionsSnapshot
import com.naari.companion.protocol.ProtocolConstants

/**
 * High-level system readiness states reflecting mutual device readiness and capability checks.
 * Ref: PRD Section 3 & Master Prompt Task 4.
 */
enum class ReadinessState {
    BluetoothOff,
    Disconnected,
    Connecting,
    Paired,
    ConnectedServiceUnavailable,
    Ready,
    PermissionLimited
}

/**
 * Step in the low-level Bluetooth LE connection lifecycle.
 */
enum class BleConnectionStep {
    Disconnected,
    Connecting,
    ConnectedBonded,
    ServiceDiscovered
}

/**
 * Truthful readiness derivation engine.
 * Never claims "Ready" purely because BLE is connected.
 * Requires:
 * 1. Bluetooth adapter enabled
 * 2. Active BLE bond with service and characteristics confirmed
 * 3. Mutual hello capability handshake received within the last 45 seconds
 * 4. All required safety permissions (SMS, Calls, Location) granted
 */
object ReadinessDeriver {

    fun derive(
        bluetoothAdapterEnabled: Boolean,
        connectionStep: BleConnectionStep,
        hasRequiredGattService: Boolean,
        lastHelloTimestampMs: Long?,
        currentTimeMs: Long,
        permissions: PermissionsSnapshot
    ): ReadinessState {
        // 1. Hardware Bluetooth check
        if (!bluetoothAdapterEnabled) {
            return ReadinessState.BluetoothOff
        }

        // 2. Link connection step checks
        when (connectionStep) {
            BleConnectionStep.Disconnected -> return ReadinessState.Disconnected
            BleConnectionStep.Connecting -> return ReadinessState.Connecting
            BleConnectionStep.ConnectedBonded -> {
                // Connected and bonded, but service discovery is either pending or not completed
                return if (hasRequiredGattService) {
                    ReadinessState.Paired
                } else {
                    ReadinessState.ConnectedServiceUnavailable
                }
            }
            BleConnectionStep.ServiceDiscovered -> {
                if (!hasRequiredGattService) {
                    return ReadinessState.ConnectedServiceUnavailable
                }
            }
        }

        // 3. Hello freshness check (expires after 45 seconds per PRD Section 5)
        if (lastHelloTimestampMs == null) {
            // Awaiting initial hello handshake
            return ReadinessState.ConnectedServiceUnavailable
        }

        val ageMs = currentTimeMs - lastHelloTimestampMs
        if (ageMs < 0 || ageMs > ProtocolConstants.READINESS_EXPIRATION_MS) {
            // Hello expired (> 45s), mutual readiness invalid
            return ReadinessState.ConnectedServiceUnavailable
        }

        // 4. Permissions check
        val missingSms = !permissions.smsGranted
        val missingCall = !permissions.callGranted
        val missingLocation = permissions.locationState == LocationPermissionState.Denied

        if (missingSms || missingCall || missingLocation) {
            return ReadinessState.PermissionLimited
        }

        // Mutual readiness confirmed truthfully
        return ReadinessState.Ready
    }
}
