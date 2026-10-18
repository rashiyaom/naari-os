package com.naari.companion.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naari.companion.data.AppDatabase
import com.naari.companion.data.IncidentLog
import com.naari.companion.designsystem.AttentionAmber
import com.naari.companion.designsystem.AttentionAmberContainerLight
import com.naari.companion.designsystem.DangerButton
import com.naari.companion.designsystem.EmergencyRed
import com.naari.companion.designsystem.EmergencyRedContainerLight
import com.naari.companion.designsystem.InlineError
import com.naari.companion.designsystem.InlineErrorSeverity
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.ReadinessBadge
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.designsystem.TealAccent
import com.naari.companion.permissions.AndroidPermissionsRepository
import com.naari.companion.service.CompanionServiceDependencies
import com.naari.companion.watch.DefaultWatchSettingsRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Incidents screen conforming to PRD Section 3 & Master Prompt Task 16:
 * - Real readiness badge and monitoring enabled state.
 * - Live incident tracking with watch authority countdown.
 * - Visibly marked safe demo mode (strictly 0 real calls/SMS).
 * - Deliberate remote SOS cancellation with disarm PIN and persistent logging.
 * - Honest fall-detection disclaimer.
 */
@Composable
fun IncidentsScreen(
    viewModel: IncidentsViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveViewModel = viewModel ?: remember(context) {
        val db = AppDatabase.getInstance(context)
        val ble = CompanionServiceDependencies.getBleSessionManager(context)
        val incidentsRepo = DefaultIncidentsRepository(db.incidentLogDao(), ble)
        val permsRepo = AndroidPermissionsRepository(context)
        val settingsRepo = DefaultWatchSettingsRepository(ble)
        IncidentsViewModel(
            incidentsRepository = incidentsRepo,
            permissionsRepository = permsRepo,
            bleSessionManager = ble,
            watchSettingsRepository = settingsRepo
        )
    }

    val uiState by effectiveViewModel.uiState.collectAsState()
    var showDisarmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        // Section Header with Readiness Badge
        SectionHeader(
            title = "Safety Incidents",
            subtitle = "Live countdown & emergency event monitoring",
            trailing = {
                ReadinessBadge(readiness = uiState.readinessState)
            }
        )

        // Cancellation Error Banner if any
        uiState.cancelError?.let { err ->
            InlineError(
                message = err,
                severity = InlineErrorSeverity.Critical
            )
        }

        // Active Incident Alert Card
        uiState.activeIncident?.let { active ->
            ActiveIncidentCard(
                incident = active,
                remainingSeconds = uiState.remainingCountdownSeconds,
                onCancelClick = { showDisarmDialog = true },
                onResolveClick = { effectiveViewModel.resolveActiveIncident("RESOLVED_BY_USER") }
            )
        }

        // Monitoring Status Card (when no incident is active)
        if (uiState.activeIncident == null) {
            MonitoringStatusCard(
                isFallDetectionEnabled = uiState.isFallDetectionEnabled,
                lastSyncTimeMs = uiState.lastSyncTimeMs,
                onStartDemo = { effectiveViewModel.startSafeDemo() }
            )
        }

        // Fall Detection Disclaimer Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Fall Detection Notice",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Wrist sensor heuristics can miss falls or trigger accidentally; this watch is not a medically certified emergency device. Real impacts trigger an immediate local countdown before guardian alert dispatch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Escalation History & Audit Trail Card
        IncidentHistoryCard(logs = uiState.incidentLogs)

        Spacer(modifier = Modifier.height(NaariSpacing.lg))
    }

    // Deliberate Disarm PIN Dialog
    if (showDisarmDialog) {
        DisarmConfirmationDialog(
            onDismiss = { showDisarmDialog = false },
            onConfirmDisarm = { pin ->
                showDisarmDialog = false
                effectiveViewModel.cancelActiveSos(pin, "Manual PIN disarm")
            }
        )
    }
}

@Composable
private fun ActiveIncidentCard(
    incident: ActiveIncident,
    remainingSeconds: Int,
    onCancelClick: () -> Unit,
    onResolveClick: () -> Unit
) {
    val isDemo = incident.isDemo
    val isCountdown = incident.state == IncidentLifecycleState.Countdown
    val containerBg = if (isDemo) AttentionAmberContainerLight else EmergencyRedContainerLight
    val borderColor = if (isDemo) AttentionAmber else EmergencyRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .background(containerBg)
            .padding(NaariSpacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            // Prominent Demo Mode or Emergency Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDemo) {
                    StatusChip(
                        label = "DEMO MODE — ZERO DISPATCH",
                        tone = StatusTone.Warning
                    )
                } else {
                    StatusChip(
                        label = "EMERGENCY ACTIVE",
                        tone = StatusTone.Critical
                    )
                }

                Text(
                    text = "ID #${incident.incidentId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = when (incident.trigger) {
                    IncidentTrigger.FallDetected -> "Fall Detected by Watch IMU"
                    IncidentTrigger.ManualSos -> "Manual Watch SOS Triggered"
                    IncidentTrigger.SafeDemo -> "Safety Countdown Simulation"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            // Large Authority Countdown Timer
            if (isCountdown) {
                Text(
                    text = "00:${remainingSeconds.toString().padStart(2, '0')}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDemo) AttentionAmber else EmergencyRed,
                    modifier = Modifier.semantics {
                        contentDescription = "$remainingSeconds seconds remaining in countdown"
                    }
                )
                Text(
                    text = "Authority countdown on watch. If you are safe, cancel below before dispatch begins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                DangerButton(
                    text = "Cancel SOS (Disarm)",
                    onClick = onCancelClick,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "State: ${incident.state.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                SecondaryButton(
                    text = "Dismiss Incident",
                    onClick = onResolveClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Actions list
            if (incident.actions.isNotEmpty()) {
                Text(
                    text = "Dispatched Actions:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                incident.actions.forEach { act ->
                    Text(
                        text = "• ${act.type}: ${act.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitoringStatusCard(
    isFallDetectionEnabled: Boolean,
    lastSyncTimeMs: Long?,
    onStartDemo: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            Text(
                text = "Incident Monitoring",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fall Detection:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusChip(
                    label = if (isFallDetectionEnabled) "Active" else "Disabled",
                    tone = if (isFallDetectionEnabled) StatusTone.Positive else StatusTone.Neutral
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last Telemetry Sync:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = lastSyncTimeMs?.let { dateFormat.format(Date(it)) } ?: "Awaiting sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SecondaryButton(
                text = "Simulate Fall Demo (Safe)",
                onClick = onStartDemo,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun IncidentHistoryCard(logs: List<IncidentLog>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            Text(
                text = "Escalation Audit Trail",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (logs.isEmpty()) {
                Text(
                    text = "No recorded incidents or simulated demos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                logs.take(5).forEach { log ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(NaariSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Incident #${log.watchIncidentId}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dateFormat.format(Date(log.startedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Outcome: ${log.outcome}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        log.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisarmConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmDisarm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Confirm SOS Disarm")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                Text(
                    text = "Please enter your 4-digit security PIN to confirm you are safe and cancel emergency dispatch.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6) {
                            pin = it
                            pinError = null
                        }
                    },
                    label = { Text("Disarm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = pinError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                pinError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            DangerButton(
                text = "Cancel SOS",
                onClick = {
                    if (pin.length < 4) {
                        pinError = "PIN must be at least 4 digits"
                    } else {
                        onConfirmDisarm(pin)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
