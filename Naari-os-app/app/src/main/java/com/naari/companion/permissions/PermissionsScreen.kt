package com.naari.companion.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone

@Composable
fun PermissionsScreen(
    repository: PermissionsRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveRepo = remember(context, repository) {
        repository ?: AndroidPermissionsRepository(context)
    }

    val snapshot by effectiveRepo.permissions.collectAsState()

    // Granular permission request launchers
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        effectiveRepo.refreshPermissions()
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        effectiveRepo.refreshPermissions()
    }

    val callLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        effectiveRepo.refreshPermissions()
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        effectiveRepo.refreshPermissions()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        effectiveRepo.refreshPermissions()
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        effectiveRepo.refreshPermissions()
    }

    val allPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        effectiveRepo.refreshPermissions()
    }

    val pendingPermissions = mutableListOf<String>()
    if (!snapshot.bluetoothGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            pendingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            pendingPermissions.add(Manifest.permission.BLUETOOTH)
            pendingPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }
    if (!snapshot.smsGranted) pendingPermissions.add(Manifest.permission.SEND_SMS)
    if (!snapshot.callGranted) pendingPermissions.add(Manifest.permission.CALL_PHONE)
    if (snapshot.locationState == LocationPermissionState.Denied) {
        pendingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        pendingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (!snapshot.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pendingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (!snapshot.contactsGranted) {
        pendingPermissions.add(Manifest.permission.READ_CONTACTS)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        SectionHeader(
            title = "Permissions",
            subtitle = "Granular capabilities explained with feature-level clarity"
        )

        // Summary Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Safety Capability Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusChip(
                        label = if (snapshot.hasAllSafetyPermissions) "Full Protection" else "Degraded Mode",
                        tone = if (snapshot.hasAllSafetyPermissions) StatusTone.Positive else StatusTone.Warning
                    )
                }
                Text(
                    text = if (snapshot.hasAllSafetyPermissions) {
                        "All safety execution capabilities are active. Your watch can trigger emergency calls, dispatch SMS alerts, and attach GPS map links."
                    } else {
                        "One or more emergency capabilities are restricted. Naari will degrade gracefully where possible (e.g. opening the phone dialer or sending text without GPS)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // One-Tap Grant All Permissions Banner
        if (pendingPermissions.isNotEmpty()) {
            NaariCard {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "One-Tap Permission Setup",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusChip(
                            label = "${pendingPermissions.distinct().size} Pending",
                            tone = StatusTone.Warning
                        )
                    }
                    Text(
                        text = "Grant Contacts, GPS, Notifications, SMS, and Calls in one tap for complete emergency protection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PrimaryButton(
                        text = "Grant All Permissions",
                        onClick = {
                            allPermissionsLauncher.launch(pendingPermissions.distinct().toTypedArray())
                        },
                        contentDescription = "Grant all pending permissions in one tap"
                    )
                }
            }
        }

        // Bluetooth
        PermissionItemCard(
            title = "Nearby Devices (Bluetooth)",
            description = "Required to find, connect, and communicate with your Naari Watch.",
            isGranted = snapshot.bluetoothGranted,
            degradationWarning = "Without Bluetooth, the phone cannot connect to your watch or receive any SOS requests.",
            onRequestClick = {
                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
                }
                bluetoothLauncher.launch(perms)
            }
        )

        // Send SMS
        PermissionItemCard(
            title = "Send SMS",
            description = "Dispatches emergency text messages to your guardians when the watch initiates an SOS trigger.",
            isGranted = snapshot.smsGranted,
            degradationWarning = "Without SMS permission, emergency alert messages cannot be dispatched to your guardians.",
            onRequestClick = {
                smsLauncher.launch(Manifest.permission.SEND_SMS)
            }
        )

        // Make Calls
        PermissionItemCard(
            title = "Phone Calls",
            description = "Places an immediate call to your primary guardian upon confirmed watch trigger.",
            isGranted = snapshot.callGranted,
            degradationWarning = "Without call permission, Naari will open your phone dialer for manual calling instead of direct dialing.",
            onRequestClick = {
                callLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        )

        // Location
        val locationGranted = snapshot.locationState == LocationPermissionState.Precise
        val locationStatusText = when (snapshot.locationState) {
            LocationPermissionState.Precise -> "Precise GPS"
            LocationPermissionState.Approximate -> "Approximate Only"
            LocationPermissionState.Denied -> "Denied"
        }
        val locationDegradation = when (snapshot.locationState) {
            LocationPermissionState.Precise -> null
            LocationPermissionState.Approximate -> "Approximate GPS: Location is accurate to ~2 km. Precise location recommended for safety."
            LocationPermissionState.Denied -> "Without location permission, emergency SMS will send with an explicit 'Location unavailable' notice."
        }
        PermissionItemCard(
            title = "Precise Location",
            description = "Retrieves one-shot GPS coordinates to attach a map link to guardian emergency SMS alerts.",
            isGranted = locationGranted,
            customStatus = locationStatusText,
            degradationWarning = locationDegradation,
            onRequestClick = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        )

        // Notifications
        PermissionItemCard(
            title = "Notifications",
            description = "Displays the companion foreground service status and safety connection warnings.",
            isGranted = snapshot.notificationsGranted,
            degradationWarning = "Without notification permission, Android may terminate the background service when the phone is locked.",
            onRequestClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        // Optional Contacts
        PermissionItemCard(
            title = "Contacts (Optional)",
            description = "Allows selecting emergency contacts from your phone address book. Manual phone entry is always supported without this permission.",
            isGranted = snapshot.contactsGranted,
            degradationWarning = null,
            onRequestClick = {
                contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        )
    }
}

@Composable
private fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    customStatus: String? = null,
    degradationWarning: String? = null,
    onRequestClick: () -> Unit
) {
    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusChip(
                    label = customStatus ?: if (isGranted) "Granted" else "Required",
                    tone = if (isGranted) StatusTone.Positive else StatusTone.Warning
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted && degradationWarning != null) {
                Text(
                    text = degradationWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(4.dp))
                PrimaryButton(
                    text = "Grant Permission",
                    onClick = onRequestClick,
                    contentDescription = "Request $title permission"
                )
            }
        }
    }
}
