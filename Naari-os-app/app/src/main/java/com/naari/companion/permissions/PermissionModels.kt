package com.naari.companion.permissions

/**
 * Granular location capability states.
 * Ref: PRD Section 7 (Precise vs approximate vs denied).
 */
enum class LocationPermissionState {
    Precise,
    Approximate,
    Denied
}

/**
 * Immutable snapshot of all device capability grants and hardware toggles.
 */
data class PermissionsSnapshot(
    val bluetoothGranted: Boolean,
    val bluetoothAdapterEnabled: Boolean,
    val smsGranted: Boolean,
    val callGranted: Boolean,
    val locationState: LocationPermissionState,
    val locationServiceEnabled: Boolean,
    val notificationsGranted: Boolean,
    val contactsGranted: Boolean
) {
    /**
     * Checks if all mandatory safety execution capabilities are present.
     */
    val hasAllSafetyPermissions: Boolean
        get() = smsGranted && callGranted && locationState != LocationPermissionState.Denied

    /**
     * Checks if Bluetooth discovery and link can operate.
     */
    val canConnectBluetooth: Boolean
        get() = bluetoothGranted && bluetoothAdapterEnabled

    /**
     * Specific safety execution capabilities.
     */
    val canSendSms: Boolean
        get() = smsGranted

    val canPlaceCall: Boolean
        get() = callGranted

    val canAccessLocation: Boolean
        get() = locationState != LocationPermissionState.Denied && locationServiceEnabled

    companion object {
        val ALL_DENIED = PermissionsSnapshot(
            bluetoothGranted = false,
            bluetoothAdapterEnabled = false,
            smsGranted = false,
            callGranted = false,
            locationState = LocationPermissionState.Denied,
            locationServiceEnabled = false,
            notificationsGranted = false,
            contactsGranted = false
        )

        val ALL_GRANTED = PermissionsSnapshot(
            bluetoothGranted = true,
            bluetoothAdapterEnabled = true,
            smsGranted = true,
            callGranted = true,
            locationState = LocationPermissionState.Precise,
            locationServiceEnabled = true,
            notificationsGranted = true,
            contactsGranted = true
        )
    }
}

/**
 * Feature-level explanation metadata for granular user prompts.
 */
data class PermissionExplanation(
    val permissionKey: String,
    val title: String,
    val explanation: String,
    val isMandatory: Boolean
) {
    companion object {
        val EXPLANATIONS = listOf(
            PermissionExplanation(
                permissionKey = "BLUETOOTH",
                title = "Nearby Devices (Bluetooth)",
                explanation = "Keeps your watch connected so it can trigger emergency alerts and stay protected.",
                isMandatory = true
            ),
            PermissionExplanation(
                permissionKey = "SEND_SMS",
                title = "Send SMS",
                explanation = "Required to dispatch emergency alert text messages with your location to guardians when watch triggers SOS.",
                isMandatory = true
            ),
            PermissionExplanation(
                permissionKey = "CALL_PHONE",
                title = "Phone Calls",
                explanation = "Initiates an urgent phone call to your primary guardian upon confirmed watch trigger.",
                isMandatory = true
            ),
            PermissionExplanation(
                permissionKey = "LOCATION",
                title = "Precise Location",
                explanation = "Acquires one-shot GPS coordinates during safety actions to send an actionable map link to guardians.",
                isMandatory = true
            ),
            PermissionExplanation(
                permissionKey = "NOTIFICATIONS",
                title = "Notifications",
                explanation = "Displays persistent foreground safety connection status and battery alerts.",
                isMandatory = true
            ),
            PermissionExplanation(
                permissionKey = "CONTACTS",
                title = "Contacts (Optional)",
                explanation = "Optional shortcut to pick guardian phone numbers from your phone address book without manual typing.",
                isMandatory = false
            )
        )
    }
}
