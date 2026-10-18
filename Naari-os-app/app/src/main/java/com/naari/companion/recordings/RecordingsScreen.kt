package com.naari.companion.recordings

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.naari.companion.data.AppDatabase
import com.naari.companion.data.RecordingEntity
import com.naari.companion.data.RecordingSyncState
import com.naari.companion.designsystem.DangerButton
import com.naari.companion.designsystem.InlineError
import com.naari.companion.designsystem.InlineErrorSeverity
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone
import com.naari.companion.designsystem.TealAccent
import com.naari.companion.service.CompanionServiceDependencies
import com.naari.companion.watch.DefaultWatchSettingsRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recordings screen conforming to PRD Section 3 (Recordings), Section 7 & Task 17:
 * - Room voice memos catalog.
 * - Standard WAV (16 kHz, mono, PCM16) playback.
 * - Available storage indicator and privacy guarantee (no cloud upload).
 * - Truthful capability gating ("Recording sync not supported by this firmware").
 * - Local-only deletion and FileProvider share sheet export.
 */
@Composable
fun RecordingsScreen(
    viewModel: RecordingsViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveViewModel = viewModel ?: remember(context) {
        val db = AppDatabase.getInstance(context)
        val storageManager = RecordingsStorageManager(File(context.filesDir, "recordings"))
        val repo = DefaultRecordingsRepository(db.recordingDao(), storageManager)
        val ble = CompanionServiceDependencies.getBleSessionManager(context)
        val settingsRepo = DefaultWatchSettingsRepository(ble)
        RecordingsViewModel(
            recordingsRepository = repo,
            watchSettingsRepository = settingsRepo,
            bleSessionManager = ble
        )
    }

    val uiState by effectiveViewModel.uiState.collectAsState()

    var renameTarget by remember { mutableStateOf<RecordingEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordingEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        // Section Header
        SectionHeader(
            title = "Recordings",
            subtitle = "Audio memos recorded during safety events",
            trailing = {
                StatusChip(
                    label = "${uiState.recordings.size} Memos",
                    tone = if (uiState.recordings.isNotEmpty()) StatusTone.Positive else StatusTone.Neutral
                )
            }
        )

        // Error Banner
        uiState.inlineError?.let { err ->
            InlineError(message = err, severity = InlineErrorSeverity.Critical)
        }

        // Firmware Capability Gating Notice
        if (!uiState.isFirmwareSupported) {
            InlineError(
                message = "The watch records up to 10-second emergency audio memos for on-device speaker playback. BLE audio transfer to your phone will be enabled in an upcoming watch software release.",
                severity = InlineErrorSeverity.Warning
            )
        }

        // Storage & Privacy Status Card
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phone Storage Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${uiState.availableStorageMb} MB Available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "All voice memos remain in app-owned private storage and are never uploaded to any cloud server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Memos List
        if (uiState.recordings.isEmpty()) {
            NaariCard {
                Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
                    Text(
                        text = "No Synchronized Memos",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Audio memos captured during watch safety incidents are saved to watch memory for playback. BLE audio sync to your phone will appear here once enabled in watch firmware.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            uiState.recordings.forEach { memo ->
                RecordingItemCard(
                    memo = memo,
                    isPlaying = uiState.currentPlayingId == memo.id && uiState.isPlaying,
                    playbackPositionMs = if (uiState.currentPlayingId == memo.id) uiState.playbackPositionMs else 0,
                    playbackDurationMs = if (uiState.currentPlayingId == memo.id) uiState.playbackDurationMs else memo.durationMs.toInt(),
                    onPlay = { effectiveViewModel.playRecording(memo) },
                    onPause = { effectiveViewModel.pausePlayback() },
                    onSeek = { effectiveViewModel.seekPlayback(it) },
                    onRename = { renameTarget = memo },
                    onDelete = { deleteTarget = memo },
                    onExport = {
                        val uri = effectiveViewModel.getExportUri(memo.id, context)
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Voice Memo"))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(NaariSpacing.lg))
    }

    // Rename Dialog
    renameTarget?.let { memo ->
        RenameDialog(
            currentTitle = memo.title,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                effectiveViewModel.renameRecording(memo.id, newTitle)
                renameTarget = null
            }
        )
    }

    // Delete Confirmation Dialog
    deleteTarget?.let { memo ->
        DeleteDialog(
            memoTitle = memo.title,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                effectiveViewModel.deleteRecording(memo.id)
                deleteTarget = null
            }
        )
    }
}

@Composable
private fun RecordingItemCard(
    memo: RecordingEntity,
    isPlaying: Boolean,
    playbackPositionMs: Int,
    playbackDurationMs: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val isVerified = memo.syncState == RecordingSyncState.VerifiedOnPhone

    NaariCard {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            // Header Row: Title, Date, Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = memo.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateFormat.format(Date(memo.timestampMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val (statusLabel, statusTone) = when (memo.syncState) {
                    RecordingSyncState.VerifiedOnPhone -> "Verified" to StatusTone.Positive
                    RecordingSyncState.Transferring -> "Syncing..." to StatusTone.Warning
                    RecordingSyncState.WaitingForTransfer -> "Queued" to StatusTone.Neutral
                    RecordingSyncState.CapturedOnWatch -> "On Watch" to StatusTone.Neutral
                    RecordingSyncState.Failed -> "Failed" to StatusTone.Critical
                }
                StatusChip(label = statusLabel, tone = statusTone)
            }

            // Technical details (duration, size, hash)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Duration: ${(memo.durationMs / 1000f)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Size: ${memo.totalBytes / 1024} KB (16 kHz WAV)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Audio Player Controls (when verified)
            if (isVerified) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
                ) {
                    // Play/Pause button
                    IconButton(
                        onClick = { if (isPlaying) onPause() else onPlay() },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = if (isPlaying) "Pause audio playback" else "Play voice memo"
                            }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) NaariIcons.Pause else NaariIcons.Play,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Progress Slider
                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = playbackPositionMs.toFloat(),
                            onValueChange = { onSeek(it.toInt()) },
                            valueRange = 0f..(playbackDurationMs.coerceAtLeast(1).toFloat()),
                            colors = SliderDefaults.colors(
                                thumbColor = TealAccent,
                                activeTrackColor = TealAccent
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = "Audio playback progress slider"
                            }
                        )
                    }

                    // Duration display
                    Text(
                        text = "%02d:%02d".format((playbackPositionMs / 1000) / 60, (playbackPositionMs / 1000) % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons: Rename, Export, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
            ) {
                SecondaryButton(
                    text = "Rename",
                    onClick = onRename,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Export",
                    onClick = onExport,
                    enabled = isVerified,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Delete",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Rename Voice Memo") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Memo Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onConfirm(title) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DeleteDialog(
    memoTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete Local Memo") },
        text = {
            Text(
                text = "Are you sure you want to delete '$memoTitle' from your phone? Deleting this local copy does not delete the original recording from your watch.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            DangerButton(
                text = "Delete",
                onClick = onConfirm
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
