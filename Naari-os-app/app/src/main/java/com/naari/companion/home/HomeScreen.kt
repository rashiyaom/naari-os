package com.naari.companion.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naari.companion.antiloss.DisconnectEvent
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.ReadinessState
import com.naari.companion.checkin.SafeCheckInResult
import com.naari.companion.safewalk.SafeWalkState
import com.naari.companion.data.AppDatabase
import com.naari.companion.designsystem.InlineError
import com.naari.companion.designsystem.MinimalistBlack
import com.naari.companion.designsystem.MinimalistBorder
import com.naari.companion.designsystem.MinimalistSurface
import com.naari.companion.designsystem.MinimalistSurfaceSecondary
import com.naari.companion.designsystem.MinimalistTextPrimary
import com.naari.companion.designsystem.MinimalistTextSecondary
import com.naari.companion.designsystem.MinimalistWhite
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariShapes
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.ReadinessBadge
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.guardians.DefaultGuardiansRepository
import com.naari.companion.ledger.DefaultRequestLedgerRepository
import com.naari.companion.onboarding.DefaultOnboardingRepository
import com.naari.companion.permissions.AndroidPermissionsRepository
import com.naari.companion.service.CompanionServiceDependencies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home dashboard destination conforming to PRD Section 4 & Master Prompt Task 14:
 * - Centered original watch illustration with dynamic status ring
 * - Real readiness badge (never shows Ready without mutual hello < 45s and permissions)
 * - Battery percentage and last-seen timestamp
 * - Three concise action tiles with inline failure indications near actions
 * - TalkBack accessibility and responsive 320dp layout
 */
@Composable
fun HomeScreen(
    onNavigateToPermissions: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToGuardians: () -> Unit = {},
    onNavigateToWatch: () -> Unit = {},
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToIncidents: () -> Unit = {},
    onNavigateToLanding: () -> Unit = {},
    viewModel: HomeViewModel? = null,
    modifier: Modifier = Modifier
) {
    val effectiveViewModel: HomeViewModel = viewModel ?: hiltViewModel()
    val state by effectiveViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        // 1. Dashboard Header with Title and Real Readiness Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Is Naari ready?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MinimalistTextSecondary
                )
                Text(
                    text = when (state.readiness) {
                        ReadinessState.Ready -> "Ready to protect you."
                        ReadinessState.BluetoothOff -> "Bluetooth is off."
                        ReadinessState.Disconnected -> "Watch disconnected."
                        ReadinessState.Connecting -> "Connecting to watch..."
                        ReadinessState.PermissionLimited -> "Permissions needed."
                        ReadinessState.ConnectedServiceUnavailable -> "Verifying watch..."
                        ReadinessState.Paired -> "Checking connection..."
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MinimalistTextPrimary
                )
            }
            ReadinessBadge(readiness = state.readiness)
        }

        // Unpaired Guidance Banner (Invites user to pair from Landing page)
        if (state.pairedDeviceAddress == null) {
            NaariCard(
                onClick = onNavigateToLanding,
                contentDescription = "Watch not paired. Tap to open pairing and landing guide."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(NaariShapes.iconSquircle)
                            .background(MinimalistBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = NaariIcons.Watch,
                            contentDescription = null,
                            tint = MinimalistWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "No Watch Paired",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MinimalistTextPrimary
                        )
                        Text(
                            text = "Tap here to pair your watch and unlock all hardware safety features.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MinimalistTextSecondary
                        )
                    }
                }
            }
        }

        // Anti-Loss & Separation Alert ("Watch Left Behind")
        if (state.disconnectEvent != null) {
            val event = state.disconnectEvent!!
            val context = LocalContext.current
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = NaariShapes.cardLarge,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE65100))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(NaariSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(NaariShapes.iconSquircle)
                                    .background(Color(0xFFE65100)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = NaariIcons.LocationPin,
                                    contentDescription = null,
                                    tint = MinimalistWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Watch Left Behind?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFBF360C)
                            )
                        }
                        StatusChip(label = "Separated", tone = StatusTone.Critical)
                    }

                    Text(
                        text = "Connection lost at ${event.formattedTime}. Last recorded location: ${event.locationSummary}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF5D4037)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
                    ) {
                        if (event.mapsUrl != null) {
                            SecondaryButton(
                                text = "Open in Maps",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.mapsUrl))
                                    context.startActivity(intent)
                                },
                                contentDescription = "Open last seen location in Google Maps",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SecondaryButton(
                            text = "Dismiss Alert",
                            onClick = { effectiveViewModel.clearSeparationAlert() },
                            contentDescription = "Dismiss watch separation alert",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Hero Watch Card with Integrated Black Bottom Section (Ref: "Product Designer" Card in Reference)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = NaariShapes.cardLarge,
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MinimalistSurface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalistBorder),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NaariSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
            ) {
                // Centered Watch Illustration
                WatchIllustration(
                    readiness = state.readiness,
                    batteryPercent = state.batteryPercent,
                    modifier = Modifier.padding(top = NaariSpacing.xs)
                )

                // High-Contrast Solid Black Pill/Container at bottom of the card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MinimalistBlack)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Naari Watch Link",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MinimalistWhite
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (state.pairedDeviceAddress != null) "Paired" else "Unpaired",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MinimalistWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = when (state.readiness) {
                                ReadinessState.Ready -> "Protected & Ready"
                                ReadinessState.BluetoothOff -> "Bluetooth is turned off on your phone"
                                ReadinessState.Disconnected -> "Watch disconnected · Reconnecting automatically"
                                ReadinessState.Connecting -> "Connecting to your Naari Watch..."
                                ReadinessState.Paired -> "Connected · Checking watch status..."
                                ReadinessState.ConnectedServiceUnavailable -> "Connected · Verifying safety services..."
                                ReadinessState.PermissionLimited -> "Permissions limited · Tap to resolve"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MinimalistWhite.copy(alpha = 0.8f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Battery: ${state.batteryPercent?.let { "$it%" } ?: "Offline"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MinimalistWhite
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Seen: ${formatLastSeen(state.lastSeenTimestampMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MinimalistWhite
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Telemetry 2-Column Grid (Ref: "Experience / Minimum 1 year" & "Employee Type" Tiles)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
        ) {
            val isLowBattery = state.batteryPercent != null && state.batteryPercent!! <= 20
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (isLowBattery) Color(0xFFFFEBEE) else MinimalistSurfaceSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isLowBattery) Color(0xFFEF5350) else MinimalistBorder
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WATCH BATTERY",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLowBattery) Color(0xFFC62828) else MinimalistTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isLowBattery) {
                            StatusChip(label = "Low", tone = StatusTone.Critical)
                        }
                    }
                    Text(
                        text = state.batteryPercent?.let { "$it%" } ?: "Offline",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isLowBattery) Color(0xFFC62828) else MinimalistTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (isLowBattery) {
                        Text(
                            text = "Recharge soon for SOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MinimalistSurfaceSecondary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalistBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "LAST CONTACT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MinimalistTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatLastSeen(state.lastSeenTimestampMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = MinimalistTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Feature 3: Anti-Loss Separation Alert ("Watch Left Behind")
        if (state.disconnectEvent != null) {
            AntiLossSeparationCard(
                event = state.disconnectEvent!!,
                onDismiss = { effectiveViewModel.clearSeparationAlert() }
            )
        }

        // Feature 2: Safe Walk Journey Timer (Dead-Man's Switch)
        SafeWalkCard(
            state = state.safeWalkState,
            onStart = { durationMinutes -> effectiveViewModel.startSafeWalk(durationMinutes) },
            onExtend = { effectiveViewModel.extendSafeWalk(10) },
            onArriveSafely = { effectiveViewModel.arriveSafelySafeWalk() },
            onCancel = { effectiveViewModel.cancelSafeWalk() },
            onReset = { effectiveViewModel.resetSafeWalk() }
        )

        // 4. Safety & Device Actions Section
        SectionHeader(
            title = "Safety & Device Actions",
            subtitle = "Active dispatchers and quick reassurance"
        )

        // Feature 4: One-Tap "Reached Safely" Quick Check-in
        SafeCheckInCard(
            isCheckingIn = state.isCheckingIn,
            result = state.checkInResult,
            error = state.checkInError,
            onSendCheckIn = { effectiveViewModel.sendSafeCheckIn() },
            onDismissFeedback = { effectiveViewModel.dismissCheckInMessage() }
        )

        // Tile 1: Safety Dispatcher Tile
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Safety Dispatcher",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusChip(
                        label = if (state.pendingLedgerCount > 0) "${state.pendingLedgerCount} Pending" else "Synced",
                        tone = if (state.pendingLedgerCount > 0) StatusTone.Warning else StatusTone.Positive
                    )
                }

                Text(
                    text = state.lastActionSummary ?: "Dispatches emergency SMS alerts and phone calls triggered by your watch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Inline failure near action
                if (state.inlineErrorSafety != null) {
                    InlineError(message = state.inlineErrorSafety!!)
                    SecondaryButton(
                        text = "Resolve Permissions",
                        onClick = onNavigateToPermissions,
                        contentDescription = "Navigate to permissions setup"
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
                ) {
                    SecondaryButton(
                        text = "Safety Incidents",
                        onClick = onNavigateToIncidents,
                        contentDescription = "View safety incidents monitoring",
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = "History",
                        onClick = onNavigateToHistory,
                        contentDescription = "View activity history ledger",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Tile 2: Guardians Tile
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Guardians",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusChip(
                        label = "${state.guardiansCount}/2 Configured",
                        tone = if (state.guardiansCount > 0) StatusTone.Positive else StatusTone.Warning
                    )
                }

                Text(
                    text = if (state.guardiansCount > 0) {
                        "Primary: ${state.primaryGuardianName ?: "Configured"} (${state.primaryGuardianPhone ?: ""})"
                    } else {
                        "No emergency contacts configured yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Inline failure near action
                if (state.inlineErrorGuardians != null) {
                    InlineError(message = state.inlineErrorGuardians!!)
                }

                Spacer(modifier = Modifier.height(2.dp))

                SecondaryButton(
                    text = "Manage Guardians",
                    onClick = onNavigateToGuardians,
                    contentDescription = "Manage emergency guardian contacts"
                )
            }
        }

        // Tile 3: Watch Connection Tile
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Watch Connection",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusChip(
                        label = when (state.connectionState) {
                            BleConnectionState.Ready -> "Connected"
                            BleConnectionState.Connecting -> "Connecting"
                            BleConnectionState.BluetoothDisabled -> "Bluetooth Off"
                            else -> "Disconnected"
                        },
                        tone = when (state.connectionState) {
                            BleConnectionState.Ready -> StatusTone.Positive
                            BleConnectionState.Connecting -> StatusTone.Neutral
                            BleConnectionState.BluetoothDisabled -> StatusTone.Critical
                            else -> StatusTone.Warning
                        }
                    )
                }

                Text(
                    text = if (state.pairedDeviceAddress != null) {
                        "Connected to ${state.pairedDeviceName ?: "Naari Watch"}. Emergency protection is active."
                    } else {
                        "No watch paired. Tap below to connect your watch."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Inline failure near action
                if (state.inlineErrorConnection != null) {
                    InlineError(message = state.inlineErrorConnection!!)
                }

                Spacer(modifier = Modifier.height(2.dp))

                SecondaryButton(
                    text = "Watch Controls & Pairing",
                    onClick = onNavigateToWatch,
                    contentDescription = "Navigate to watch controls and pairing"
                )
            }
        }

        // 5. Onboarding / Setup Review Links
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NaariSpacing.xs),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onNavigateToLanding,
                modifier = Modifier.semantics {
                    contentDescription = "Review Naari landing overview"
                }
            ) {
                Text(
                    text = "Welcome & Overview",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(text = "•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(
                onClick = onNavigateToOnboarding,
                modifier = Modifier.semantics {
                    contentDescription = "Review initial onboarding setup guide"
                }
            ) {
                Text(
                    text = "Setup Guide",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
private fun formatLastSeen(timestampMs: Long?): String {
    if (timestampMs == null) return "Never"
    val diffSeconds = (System.currentTimeMillis() - timestampMs) / 1000
    return when {
        diffSeconds < 5 -> "Just now"
        diffSeconds < 60 -> "${diffSeconds}s ago"
        diffSeconds < 3600 -> "${diffSeconds / 60}m ago"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}

@Composable
fun SafeWalkCard(
    state: SafeWalkState,
    onStart: (Int) -> Unit,
    onExtend: () -> Unit,
    onArriveSafely: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMinutes by remember { mutableStateOf(15) }

    when (state) {
        is SafeWalkState.Idle -> {
            NaariCard(modifier = modifier) {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(NaariShapes.iconSquircle)
                                    .background(MinimalistBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = NaariIcons.Walk,
                                    contentDescription = null,
                                    tint = MinimalistWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Safe Walk Journey Timer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalistTextPrimary
                                )
                                Text(
                                    text = "Dead-Man's Switch Safety",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MinimalistTextSecondary
                                )
                            }
                        }
                        StatusChip(label = "Standby", tone = StatusTone.Neutral)
                    }

                    Text(
                        text = "Walking home alone or taking a cab? If you don't confirm safe arrival before the timer expires, an emergency SOS SMS with live GPS will be dispatched to your guardians automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MinimalistTextSecondary
                    )

                    // Duration Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 30).forEach { mins ->
                            val isSelected = selectedMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MinimalistBlack else MinimalistSurfaceSecondary)
                                    .border(
                                        1.dp,
                                        if (isSelected) MinimalistBlack else MinimalistBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedMinutes = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins mins",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MinimalistWhite else MinimalistTextPrimary
                                )
                            }
                        }
                    }

                    PrimaryButton(
                        text = "Start $selectedMinutes Min Safe Walk",
                        onClick = { onStart(selectedMinutes) },
                        contentDescription = "Start journey timer for $selectedMinutes minutes",
                        leadingIcon = {
                            Icon(
                                imageVector = NaariIcons.Walk,
                                contentDescription = null,
                                tint = MinimalistWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        is SafeWalkState.Active -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = NaariShapes.cardLarge,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MinimalistBlack
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = NaariIcons.Walk,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Safe Walk Active",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MinimalistWhite
                                )
                                Text(
                                    text = "Tap 'Arrived Safely' when you reach",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MinimalistWhite.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF00E676))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "MONITORING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MinimalistBlack
                            )
                        }
                    }

                    // Countdown Display
                    val minutes = state.remainingSeconds / 60
                    val seconds = state.remainingSeconds % 60
                    val formattedCountdown = "%02d:%02d".format(minutes, seconds)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formattedCountdown,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.remainingSeconds <= 60) Color(0xFFFF5252) else MinimalistWhite
                        )
                        Text(
                            text = "Remaining before automatic SOS dispatch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MinimalistWhite.copy(alpha = 0.7f)
                        )
                    }

                    // Linear Progress Bar
                    val totalSec = (state.totalMinutes * 60).toFloat()
                    val progress = (state.remainingSeconds.toFloat() / totalSec.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (state.remainingSeconds <= 60) Color(0xFFFF5252) else Color(0xFF00E676),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )

                    // Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onArriveSafely,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = NaariShapes.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                contentColor = MinimalistBlack
                            )
                        ) {
                            Icon(
                                imageVector = NaariIcons.CheckCircle,
                                contentDescription = null,
                                tint = MinimalistBlack,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I've Arrived Safely",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onExtend,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = NaariShapes.button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = MinimalistWhite
                                )
                            ) {
                                Text(
                                    text = "+10 Mins",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Button(
                                onClick = onCancel,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = NaariShapes.button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = MinimalistWhite
                                )
                            ) {
                                Text(
                                    text = "Cancel Journey",
                                    fontWeight = FontWeight.Normal,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        is SafeWalkState.Triggered -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = NaariShapes.cardLarge,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD32F2F))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(NaariSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Journey Timer Expired",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                        StatusChip(label = "SOS Dispatched", tone = StatusTone.Critical)
                    }
                    Text(
                        text = "The countdown reached zero without safe arrival confirmation. Emergency SOS alert with live GPS coordinates was dispatched to your guardians.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB71C1C)
                    )
                    SecondaryButton(
                        text = "Dismiss & Reset",
                        onClick = onReset,
                        contentDescription = "Dismiss triggered safe walk alert"
                    )
                }
            }
        }

        is SafeWalkState.Completed -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = NaariShapes.cardLarge,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = Color(0xFFE8F5E9)
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2E7D32))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(NaariSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Arrived Safely",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        StatusChip(label = "Safe", tone = StatusTone.Positive)
                    }
                    Text(
                        text = "You marked your journey as safely completed. Emergency alerts were not triggered.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1B5E20)
                    )
                    SecondaryButton(
                        text = "Done",
                        onClick = onReset,
                        contentDescription = "Dismiss completed safe walk notification"
                    )
                }
            }
        }
    }
}

@Composable
fun AntiLossSeparationCard(
    event: DisconnectEvent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = NaariShapes.cardLarge,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFA000))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NaariSpacing.md),
            verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF8F00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = NaariIcons.LocationPin,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Watch Left Behind?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037)
                        )
                        Text(
                            text = event.formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8D6E63)
                        )
                    }
                }
                StatusChip(label = "Separated", tone = StatusTone.Critical)
            }

            Text(
                text = "Bluetooth link dropped unexpectedly. Pinned last known coordinates (${event.locationSummary}) so you can retrieve your watch.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF4E342E)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (event.mapsUrl != null) {
                    Button(
                        onClick = {
                            try {
                                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.mapsUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(mapIntent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp),
                        shape = NaariShapes.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE65100),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = NaariIcons.LocationPin,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Find on Maps",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.8f)
                        .height(44.dp),
                    shape = NaariShapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD7CCC8),
                        contentColor = Color(0xFF3E2723)
                    )
                ) {
                    Text(
                        text = "Dismiss",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun SafeCheckInCard(
    isCheckingIn: Boolean,
    result: SafeCheckInResult?,
    error: String?,
    onSendCheckIn: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    NaariCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(NaariShapes.iconSquircle)
                            .background(MinimalistBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = NaariIcons.CheckCircle,
                            contentDescription = null,
                            tint = MinimalistWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Reached Safely Check-in",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                StatusChip(label = "1-Tap", tone = StatusTone.Positive)
            }

            Text(
                text = "Single tap sends a reassuring text with live Google Maps link to both configured guardians: 'Hi Mom, I have reached safely at [Google Maps Link]'.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (result != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F5E9))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "✓ ${result.summary}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (error != null) {
                InlineError(message = error)
            }

            Spacer(modifier = Modifier.height(2.dp))

            PrimaryButton(
                text = if (isCheckingIn) "Sending Check-in..." else "Send Safe Check-in",
                onClick = onSendCheckIn,
                enabled = !isCheckingIn,
                isLoading = isCheckingIn,
                contentDescription = "Send safe check-in SMS with GPS link to all guardians",
                leadingIcon = {
                    Icon(
                        imageVector = NaariIcons.CheckCircle,
                        contentDescription = null,
                        tint = MinimalistWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}
