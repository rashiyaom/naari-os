package com.naari.companion.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.naari.companion.ble.ReadinessState

/**
 * Typealias for backward compatibility with Task 2 design system components.
 */
typealias ReadinessLevel = ReadinessState

/**
 * Visual badge for device and system readiness.
 * Truthful: Never presents "Ready" purely because BLE is connected.
 */
@Composable
fun ReadinessBadge(
    readiness: ReadinessState,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground

    val (label, bg, strokeColor, dotColor, textColor) = when (readiness) {
        ReadinessState.Ready -> Quintuple(
            "Ready",
            if (isDark) TealContainerDark else TealContainerLight,
            TealAccent.copy(alpha = 0.4f),
            TealAccent,
            if (isDark) TealAccent else TealAccentDark
        )
        ReadinessState.PermissionLimited -> Quintuple(
            "Permissions Limited",
            if (isDark) AttentionAmberContainerDark else AttentionAmberContainerLight,
            AttentionAmber.copy(alpha = 0.4f),
            AttentionAmber,
            if (isDark) AttentionAmber else Color(0xFFB45309)
        )
        ReadinessState.Connecting -> Quintuple(
            "Connecting…",
            if (isDark) NeutralChipDark else NeutralChipLight,
            MaterialTheme.colorScheme.outline,
            AttentionAmber,
            MaterialTheme.colorScheme.onSurface
        )
        ReadinessState.Paired -> Quintuple(
            "Paired",
            if (isDark) NeutralChipDark else NeutralChipLight,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurface
        )
        ReadinessState.ConnectedServiceUnavailable -> Quintuple(
            "Service Unavailable",
            if (isDark) AttentionAmberContainerDark else AttentionAmberContainerLight,
            AttentionAmber.copy(alpha = 0.4f),
            AttentionAmber,
            if (isDark) AttentionAmber else Color(0xFFB45309)
        )
        ReadinessState.BluetoothOff -> Quintuple(
            "Bluetooth Off",
            if (isDark) NeutralChipDark else NeutralChipLight,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ReadinessState.Disconnected -> Quintuple(
            "Disconnected",
            if (isDark) NeutralChipDark else NeutralChipLight,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val accessibleDescription = "System status: $label"

    Row(
        modifier = modifier
            .semantics { this.contentDescription = accessibleDescription }
            .clip(NaariShapes.badge)
            .background(bg)
            .border(1.dp, strokeColor, NaariShapes.badge)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
