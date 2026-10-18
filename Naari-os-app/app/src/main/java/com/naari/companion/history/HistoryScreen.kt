package com.naari.companion.history

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naari.companion.designsystem.DangerButton
import com.naari.companion.designsystem.InlineError
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerStatus
import com.naari.companion.protocol.WatchRequestType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel? = null,
    modifier: Modifier = Modifier
) {
    val effectiveViewModel: HistoryViewModel = viewModel ?: hiltViewModel()

    val entries by effectiveViewModel.entries.collectAsState()
    val pendingRetryEntry by effectiveViewModel.pendingRetryEntry.collectAsState()
    val isRetrying by effectiveViewModel.isRetrying.collectAsState()
    val retryFeedback by effectiveViewModel.retryFeedback.collectAsState()

    HistoryScreenContent(
        entries = entries,
        pendingRetryEntry = pendingRetryEntry,
        isRetrying = isRetrying,
        retryFeedback = retryFeedback,
        onRetryClick = effectiveViewModel::onRetryRequested,
        onConfirmRetry = effectiveViewModel::confirmRetry,
        onDismissRetryDialog = effectiveViewModel::dismissRetryDialog,
        onClearFeedback = effectiveViewModel::clearFeedback,
        modifier = modifier
    )
}

@Composable
fun HistoryScreenContent(
    entries: List<RequestLedgerEntry>,
    pendingRetryEntry: RequestLedgerEntry?,
    isRetrying: Boolean,
    retryFeedback: String?,
    onRetryClick: (RequestLedgerEntry) -> Unit,
    onConfirmRetry: () -> Unit,
    onDismissRetryDialog: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        SectionHeader(
            title = "Activity History",
            subtitle = "Durable ledger of watch safety requests and execution outcomes"
        )

        // Feedback alert banner if user just retried an action
        retryFeedback?.let { feedback ->
            NaariCard {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onClearFeedback) {
                        Text("Dismiss")
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            // Honest empty state
            NaariCard {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                    Text(
                        text = "No Recorded Events",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "All emergency SMS, call, and location requests received over BLE are recorded here before execution.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Crashes or interrupted dispatches remain in UnknownOutcome state and are never automatically replayed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            entries.forEach { entry ->
                ActivityLedgerCard(
                    entry = entry,
                    onRetryClick = { onRetryClick(entry) }
                )
            }
        }
    }

    // Duplicate-Risk Confirmation Dialog for Manual Retry
    if (pendingRetryEntry != null) {
        AlertDialog(
            onDismissRequest = onDismissRetryDialog,
            title = {
                Text(
                    text = "Duplicate Action Risk",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                    Text(
                        text = "Retrying an unconfirmed or interrupted action may dispatch a second SMS or call to the recipient if the cellular carrier previously accepted it.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Target: ${RedactionUtils.redactPhoneNumber(pendingRetryEntry.targetPhone)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Do you want to retry execution now?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                DangerButton(
                    text = if (isRetrying) "Retrying…" else "Retry Anyway",
                    onClick = onConfirmRetry,
                    enabled = !isRetrying,
                    modifier = Modifier.padding(horizontal = NaariSpacing.xs)
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissRetryDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ActivityLedgerCard(
    entry: RequestLedgerEntry,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = when (entry.status) {
        RequestLedgerStatus.Delivered,
        RequestLedgerStatus.Located,
        RequestLedgerStatus.Calling,
        RequestLedgerStatus.Sent -> StatusTone.Positive

        RequestLedgerStatus.Received,
        RequestLedgerStatus.Executing -> StatusTone.Warning

        RequestLedgerStatus.NeedsPhone -> StatusTone.Neutral

        RequestLedgerStatus.Failed,
        RequestLedgerStatus.UnknownOutcome -> StatusTone.Critical
    }

    val typeLabel = when (entry.type) {
        WatchRequestType.SMS -> "SMS"
        WatchRequestType.CALL -> "Voice Call"
        WatchRequestType.LOCATION -> "Location"
        WatchRequestType.LOCATION_SMS -> "Location SMS"
    }

    val timeFormatted = formatTimestamp(entry.receivedAt)

    NaariCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Activity event: $typeLabel, status ${entry.status.name}, recorded at $timeFormatted"
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusChip(
                    label = entry.status.name,
                    tone = tone
                )
            }

            Text(
                text = "Recorded: $timeFormatted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            entry.targetPhone?.let { rawPhone ->
                Text(
                    text = "Recipient: ${RedactionUtils.redactPhoneNumber(rawPhone)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            entry.messagePayload?.let { rawPayload ->
                Text(
                    text = "Payload: \"${RedactionUtils.redactMessageSnippet(rawPayload)}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.failureReason?.let { failure ->
                InlineError(
                    message = failure,
                    modifier = Modifier.padding(top = NaariSpacing.xs)
                )
            }

            // Retry button shown only for Failed or UnknownOutcome terminal errors
            if (entry.status == RequestLedgerStatus.Failed || entry.status == RequestLedgerStatus.UnknownOutcome) {
                Spacer(modifier = Modifier.height(NaariSpacing.xs))
                SecondaryButton(
                    text = "Retry Action",
                    onClick = onRetryClick,
                    contentDescription = "Retry failed $typeLabel action"
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
