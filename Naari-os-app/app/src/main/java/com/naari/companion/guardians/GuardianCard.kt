package com.naari.companion.guardians

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import com.naari.companion.data.Guardian
import com.naari.companion.designsystem.DangerButton
import com.naari.companion.designsystem.MinimalistBlack
import com.naari.companion.designsystem.MinimalistTextPrimary
import com.naari.companion.designsystem.MinimalistTextSecondary
import com.naari.companion.designsystem.MinimalistWhite
import com.naari.companion.designsystem.NaariCard
import com.naari.companion.designsystem.NaariIcons
import com.naari.companion.designsystem.NaariShapes
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.SecondaryButton
import com.naari.companion.designsystem.StatusChip
import com.naari.companion.designsystem.StatusTone

@Composable
fun GuardianCard(
    slotIndex: Int,
    guardian: Guardian?,
    syncState: GuardianSyncState,
    onEditClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slotTitle = if (slotIndex == 0) "Primary Guardian" else "Secondary Guardian"

    NaariCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.sm)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = slotTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (slotIndex == 0) "Called first & receives instant SMS" else "Secondary fallback contact",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (guardian != null) {
                    val (label, tone) = when (syncState) {
                        is GuardianSyncState.SyncedToWatch -> "Saved to watch" to StatusTone.Positive
                        is GuardianSyncState.PendingSync -> "Pending watch sync" to StatusTone.Warning
                        is GuardianSyncState.Syncing -> "Syncing..." to StatusTone.Neutral
                        is GuardianSyncState.SyncFailed -> "Sync failed" to StatusTone.Critical
                        is GuardianSyncState.Empty -> "Not synced" to StatusTone.Neutral
                    }
                    StatusChip(label = label, tone = tone)
                }
            }

            if (guardian == null) {
                // Empty state for this slot
                Text(
                    text = if (slotIndex == 0) {
                        "Primary emergency contact. Receives initial SOS SMS with precise GPS coordinates."
                    } else {
                        "Secondary emergency contact. Receives fallback alert and calls."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                PrimaryButton(
                    text = if (slotIndex == 0) "Set Primary Guardian" else "Set Secondary Guardian",
                    onClick = onEditClick,
                    contentDescription = "Configure guardian slot $slotIndex",
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Populated slot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(NaariShapes.iconSquircle)
                            .background(MinimalistBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        if (guardian.name.isNotBlank()) {
                            Text(
                                text = guardian.name.trim().take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MinimalistWhite
                            )
                        } else {
                            Icon(
                                imageVector = NaariIcons.Shield,
                                contentDescription = null,
                                tint = MinimalistWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (guardian.name.isNotBlank()) guardian.name else "Guardian ${slotIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MinimalistTextPrimary
                        )
                        Text(
                            text = if (guardian.phone.isNotBlank()) guardian.phone else "Tap Edit to set phone number",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MinimalistTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
                ) {
                    SecondaryButton(
                        text = "Edit",
                        onClick = onEditClick,
                        contentDescription = "Edit details for slot $slotIndex",
                        modifier = Modifier.weight(1f)
                    )
                    DangerButton(
                        text = "Clear Slot",
                        onClick = onClearClick,
                        contentDescription = "Remove guardian from slot $slotIndex and watch",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
