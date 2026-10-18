package com.naari.companion.domain.model

/**
 * Authoritative single source of truth for watch device connectivity and protection state.
 * Ref: PRD Section 3 & Master Refactor Plan Phase 2.
 */
sealed interface DeviceStatus {
    /** Bluetooth radio on the phone is disabled */
    object BluetoothOff : DeviceStatus

    /** No watch has been bonded/paired yet */
    object NotPaired : DeviceStatus

    /** Actively scanning for advertising watch */
    object Searching : DeviceStatus

    /** Establishing BLE connection or discovering GATT services */
    object Connecting : DeviceStatus

    /** BLE link connected but mutual readiness handshake or requirements pending */
    object Connected : DeviceStatus

    /** Watch is bonded, connected, mutual hello < 45s, permissions granted, and at least 1 guardian configured */
    object Ready : DeviceStatus

    /** Paired watch requires user or system attention to restore protection */
    data class NeedsAttention(val reason: String) : DeviceStatus

    val isReady: Boolean
        get() = this is Ready

    val isConnected: Boolean
        get() = this is Connected || this is Ready

    val userMessage: String
        get() = when (this) {
            BluetoothOff -> "Bluetooth is turned off"
            NotPaired -> "Watch not paired"
            Searching -> "Searching for your watch..."
            Connecting -> "Connecting to watch..."
            Connected -> "Watch connected"
            Ready -> "Your watch is ready to protect you"
            is NeedsAttention -> reason
        }
}
