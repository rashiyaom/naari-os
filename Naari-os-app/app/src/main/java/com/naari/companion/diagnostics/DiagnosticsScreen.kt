package com.naari.companion.diagnostics

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naari.companion.designsystem.MinimalistBackground
import com.naari.companion.designsystem.MinimalistBorder
import com.naari.companion.designsystem.MinimalistSurface
import com.naari.companion.designsystem.MinimalistSurfaceSecondary
import com.naari.companion.designsystem.MinimalistTextPrimary
import com.naari.companion.designsystem.MinimalistTextSecondary
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariShapes
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.history.RedactionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Technical diagnostics screen isolated from the calm everyday UI.
 * Exposes BLE UUIDs, serialized GATT operation queue status, write-ahead Room ledger statistics,
 * and masked request logs per PRD Section 4 & Section 7.
 */
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        SectionHeader(
            title = "Diagnostics & Link Layer",
            subtitle = "Technical parameters, hardware UUIDs, and protocol ledger"
        )

        // 1. Connection & Hardware Link Telemetry Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hardware Link Telemetry",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusChip(
                        label = state.deviceStatus.userMessage,
                        tone = when (state.deviceStatus) {
                            DeviceStatus.Ready -> StatusTone.Positive
                            DeviceStatus.Connected -> StatusTone.Positive
                            is DeviceStatus.NeedsAttention -> StatusTone.Critical
                            DeviceStatus.BluetoothOff -> StatusTone.Critical
                            else -> StatusTone.Neutral
                        }
                    )
                }

                DiagnosticField("Device Name", state.pairedName ?: "Naari Watch")
                DiagnosticField("Paired Bluetooth MAC", state.pairedAddress ?: "None (Unbonded)")
                DiagnosticField("Battery Level", state.batteryPercent?.let { "$it%" } ?: "Unavailable")
                DiagnosticField("Keepalive Window", "45s sliding window handshake")
                DiagnosticField(
                    "Last Successful Sync",
                    state.lastSyncTimestampMs?.let {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: "Never"
                )
            }
        }

        // 2. Bluetooth LE GATT Architecture Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "BLE Protocol & GATT Architecture",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Serialized operation queue preventing GATT 133 / radio congestion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))
                DiagnosticField("Primary Service UUID", "d973f2e0-b19e-11ee-a506-0242ac120002")
                DiagnosticField("Request Char UUID", "d973f2e1-b19e-11ee-a506-0242ac120002")
                DiagnosticField("Reply Char UUID", "d973f2e2-b19e-11ee-a506-0242ac120002")
                DiagnosticField("Protocol v2 Service", "d973f2e3-b19e-11ee-a506-0242ac120002")
                DiagnosticField("GATT Queue Policy", "Mutex FIFO (Timeout: 4500ms)")
                DiagnosticField("Reconnect Backoff", "Exponential capped (1s to 30s)")
            }
        }

        // 3. Write-Ahead Safety Ledger Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Safety Ledger & Crash Recovery",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Write-ahead Room database persistence before external side effects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))
                DiagnosticField("Storage Engine", "Room SQLite (AppDatabase v2)")
                DiagnosticField("Deduplication Key", "MAC + RequestId (60s window)")
                DiagnosticField("In-Flight Crash Recovery", "Active (Recovers to UnknownOutcome on launch)")
                DiagnosticField("Auto-Resend Policy", "Zero auto-resend on ambiguous states")
                DiagnosticField("Emergency Redirection", "112, 1091, 911 -> System Dialer (needs_phone)")
            }
        }

        // 4. Live Redacted Event Ledger
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Recent Diagnostic Records",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Masked via RedactionUtils for privacy. Zero cloud telemetry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (state.recentLedgerEntries.isEmpty()) {
                    Text(
                        text = "No recorded safety ledger events yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    state.recentLedgerEntries.forEach { entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MinimalistSurfaceSecondary)
                                .border(1.dp, MinimalistBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${entry.type} (#${entry.watchRequestId})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MinimalistTextPrimary
                                    )
                                    Text(
                                        text = entry.status.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MinimalistTextSecondary
                                    )
                                }
                                Text(
                                    text = "Target: ${RedactionUtils.redactPhoneNumber(entry.targetPhone)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MinimalistTextSecondary
                                )
                                entry.detail?.let { detail ->
                                    Text(
                                        text = "Outcome: $detail",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MinimalistTextSecondary
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
