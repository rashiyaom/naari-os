package com.naari.companion.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Visual design system component gallery demonstrating all Section 4 tokens and reusable elements.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemGallery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(NaariSpacing.md),
        verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
    ) {
        // Section: Typography
        SectionHeader(
            title = "Typography Scale",
            subtitle = "Title 30sp · Section 21sp · Body 16sp · Label 13sp"
        )

        NaariCard {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = "Title Headline (30sp)",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Section Title (21sp)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Body text (16sp) with readable contrast in both themes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Label medium (13sp) caption",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section: Buttons
        SectionHeader(title = "Buttons (Min 48dp Touch Targets)")

        PrimaryButton(
            text = "Connect Watch",
            onClick = {},
            leadingIcon = {
                Icon(
                    imageVector = NaariIcons.Watch,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Black,
                    modifier = Modifier.padding(2.dp)
                )
            }
        )

        SecondaryButton(
            text = "Add Guardian Contact",
            onClick = {},
            leadingIcon = {
                Icon(
                    imageVector = NaariIcons.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(2.dp)
                )
            }
        )

        DangerButton(
            text = "Cancel Emergency SOS",
            onClick = {},
            leadingIcon = {
                Icon(
                    imageVector = NaariIcons.Alert,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(2.dp)
                )
            }
        )

        // Section: Status Chips
        SectionHeader(title = "Status Chips")

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
        ) {
            StatusChip(label = "Ready", tone = StatusTone.Positive)
            StatusChip(label = "Syncing Memos", tone = StatusTone.Neutral)
            StatusChip(label = "Low Battery", tone = StatusTone.Warning)
            StatusChip(label = "Transfer Failed", tone = StatusTone.Critical)
        }

        // Section: Readiness Badges
        SectionHeader(title = "Readiness Badges (Truthful States)")

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NaariSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)
        ) {
            ReadinessBadge(readiness = ReadinessLevel.Ready)
            ReadinessBadge(readiness = ReadinessLevel.Connecting)
            ReadinessBadge(readiness = ReadinessLevel.Paired)
            ReadinessBadge(readiness = ReadinessLevel.PermissionLimited)
            ReadinessBadge(readiness = ReadinessLevel.ConnectedServiceUnavailable)
            ReadinessBadge(readiness = ReadinessLevel.Disconnected)
            ReadinessBadge(readiness = ReadinessLevel.BluetoothOff)
        }

        // Section: Inline Failures
        SectionHeader(title = "Inline Failures & Alerts")

        InlineError(
            message = "SMS permission revoked. Watch SOS cannot send texts.",
            severity = InlineErrorSeverity.Critical,
            retryAction = {},
            retryLabel = "Grant"
        )

        InlineError(
            message = "GPS location accuracy is low (>150 m). Target <= 100 m.",
            severity = InlineErrorSeverity.Warning
        )

        // Section: Interactive Card
        SectionHeader(title = "Interactive Card")

        NaariCard(
            onClick = {},
            contentDescription = "Battery level card"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Naari Watch Active",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Battery 84% · Connected via BLE",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(label = "Bonded", tone = StatusTone.Positive)
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true, widthDp = 360)
@Composable
fun GalleryPreviewLight() {
    NaariTheme(darkTheme = false) {
        Surface {
            DesignSystemGallery()
        }
    }
}

@Preview(name = "Dark Mode", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
fun GalleryPreviewDark() {
    NaariTheme(darkTheme = true) {
        Surface {
            DesignSystemGallery()
        }
    }
}

@Preview(name = "200% Font Scale", showBackground = true, fontScale = 2.0f, widthDp = 360)
@Composable
fun GalleryPreviewFontScale() {
    NaariTheme(darkTheme = false) {
        Surface {
            DesignSystemGallery()
        }
    }
}
