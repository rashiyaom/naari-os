package com.naari.companion.ble

/**
 * High-level connection states for Naari Watch BLE session.
 */
enum class BleConnectionState {
    BluetoothDisabled,
    Disconnected,
    Scanning,
    Connecting,
    ConnectedServiceUnavailable,
    Ready
}
