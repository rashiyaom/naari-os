package com.naari.companion.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class InlineErrorSeverity {
    Warning,
    Critical
}

/**
 * Inline failure banner conforming to PRD Section 4:
 * Clear inline visibility near relevant action with optional retry.
 */
@Composable
fun InlineError(
    message: String,
    modifier: Modifier = Modifier,
    severity: InlineErrorSeverity = InlineErrorSeverity.Critical,
    retryAction: (() -> Unit)? = null,
    retryLabel: String = "Retry"
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground

    val (bg, stroke, textColor, dotColor) = when (severity) {
        InlineErrorSeverity.Critical -> Quadruple(
            if (isDark) EmergencyRedContainerDark else EmergencyRedContainerLight,
            EmergencyRed.copy(alpha = 0.4f),
            if (isDark) Color(0xFFFFB4AB) else EmergencyRed,
            EmergencyRed
        )
        InlineErrorSeverity.Warning -> Quadruple(
            if (isDark) AttentionAmberContainerDark else AttentionAmberContainerLight,
            AttentionAmber.copy(alpha = 0.4f),
            if (isDark) Color(0xFFFFD580) else Color(0xFFB45309),
            AttentionAmber
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NaariShapes.button)
            .background(bg)
            .border(1.dp, stroke, NaariShapes.button)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dot indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
        if (retryAction != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = retryAction,
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = retryLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
