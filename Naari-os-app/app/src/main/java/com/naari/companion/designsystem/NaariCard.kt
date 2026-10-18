package com.naari.companion.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable surface card conforming to PRD Section 4:
 * 20–24 dp corners, 16–20 dp inner padding, subtle outline.
 */
@Composable
fun NaariCard(
    modifier: Modifier = Modifier,
    innerPadding: Dp = NaariSpacing.cardPadding,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface

    val clickModifier = if (onClick != null) {
        Modifier
            .clip(NaariShapes.card)
            .clickable(
                onClick = onClick,
                role = Role.Button
            )
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
                this.role = Role.Button
            }
    } else {
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = NaariShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MinimalistSurface
        ),
        border = BorderStroke(1.dp, MinimalistBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            content = content
        )
    }
}
