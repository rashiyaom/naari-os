package com.naari.companion.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class StatusTone {
    Neutral,
    Positive,
    Warning,
    Critical
}

/**
 * Reusable StatusChip presenting high-level status with truthful tint and accessible label.
 */
@Composable
fun StatusChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    onClick: (() -> Unit)? = null,
    contentDescription: String = label
) {
    val isDark = MaterialTheme.colorScheme.background == DarkBackground

    val (bg, textColor, dotColor) = when (tone) {
        StatusTone.Positive -> Triple(
            ModernEmeraldContainer,
            ModernEmerald,
            ModernEmerald
        )
        StatusTone.Warning -> Triple(
            ModernAmberContainer,
            ModernAmber,
            ModernAmber
        )
        StatusTone.Critical -> Triple(
            ModernRedContainer,
            ModernRed,
            ModernRed
        )
        StatusTone.Neutral -> Triple(
            MinimalistSurfaceSecondary,
            MinimalistTextPrimary,
            MinimalistTextMuted
        )
    }

    val clickMod = if (onClick != null) {
        Modifier
            .defaultMinSize(minHeight = NaariSpacing.minTouchTarget)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }

    Row(
        modifier = modifier
            .then(clickMod)
            .clip(NaariShapes.chip)
            .background(bg)
            .border(0.5.dp, textColor.copy(alpha = 0.25f), NaariShapes.chip)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
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
