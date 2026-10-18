package com.naari.companion.guardians

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
import com.naari.companion.data.AppDatabase
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.SectionHeader

import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Guardians destination conforming to PRD Section 7 & 8:
 * Exactly two persisted slots for emergency contacts synchronized with watch firmware.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun GuardiansScreen(
    viewModel: GuardiansViewModel? = null,
    modifier: Modifier = Modifier
) {
    val actualViewModel: GuardiansViewModel = viewModel ?: hiltViewModel()

    val state by actualViewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        SectionHeader(
            title = "Guardians",
            subtitle = "Your emergency contacts notified during SOS"
        )

        // Truthful Sync State Banner
        if (state.slot0 != null || state.slot1 != null) {
            NaariCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Watch Sync Status",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.syncState.displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state.isPendingSync) {
                        SecondaryButton(
                            text = "Sync Now",
                            onClick = { actualViewModel.retrySync() },
                            contentDescription = "Sync guardian changes to watch immediately"
                        )
                    }
                }
            }
        }

        // Slot 0 (Primary)
        GuardianCard(
            slotIndex = 0,
            guardian = state.slot0,
            syncState = state.syncState,
            onEditClick = { actualViewModel.openEditor(0) },
            onClearClick = { actualViewModel.clearSlot(0) }
        )

        // Priority Reorder Card (when both slots are populated)
        if (state.slot0 != null && state.slot1 != null) {
            NaariCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Call & Alert Priority",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap to swap priority. Primary contact is phoned first during SOS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SecondaryButton(
                        text = "Swap Priority",
                        onClick = { actualViewModel.swapGuardians() },
                        contentDescription = "Swap primary and secondary guardian call priority"
                    )
                }
            }
        }

        // Slot 1 (Secondary)
        GuardianCard(
            slotIndex = 1,
            guardian = state.slot1,
            syncState = state.syncState,
            onEditClick = { actualViewModel.openEditor(1) },
            onClearClick = { actualViewModel.clearSlot(1) }
        )

        // Educational hardware info
        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Emergency Contact Protection",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Your Naari Watch stores 2 emergency contacts directly on the watch. If an emergency occurs, it alerts these contacts even if your phone is locked. Names can be up to 32 characters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Modal Editor Sheet
    if (state.isEditorOpen) {
        GuardianEditorSheet(
            slotIndex = state.editingSlot ?: 0,
            name = state.editorName,
            phone = state.editorPhone,
            phoneError = state.editorPhoneError,
            remainingBytes = state.editorRemainingBytes,
            isSaving = state.isSaving,
            isSaveEnabled = state.isEditorSaveEnabled,
            onNameChange = actualViewModel::onNameChanged,
            onPhoneChange = actualViewModel::onPhoneChanged,
            onContactPicked = actualViewModel::onContactPicked,
            onSave = actualViewModel::saveEditor,
            onDismiss = actualViewModel::closeEditor
        )
    }
}
