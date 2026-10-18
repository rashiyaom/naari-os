package com.naari.companion.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Primary action button styled with brand Teal accent.
 * Minimum 48 dp height touch target.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentDescription: String = text,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = NaariSpacing.minTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled && !isLoading,
        shape = NaariShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MinimalistBlack,
            contentColor = MinimalistWhite,
            disabledContainerColor = MinimalistBlack.copy(alpha = 0.38f),
            disabledContentColor = MinimalistWhite.copy(alpha = 0.38f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MinimalistWhite,
                strokeWidth = 2.dp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MinimalistWhite
                )
            }
        }
    }
}

/**
 * Secondary action button with subtle outline and neutral text.
 * Minimum 48 dp height touch target.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    contentDescription: String = text,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = NaariSpacing.minTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        shape = NaariShapes.button,
        border = BorderStroke(1.dp, MinimalistBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MinimalistSurface,
            contentColor = MinimalistTextPrimary,
            disabledContentColor = MinimalistTextMuted
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MinimalistTextPrimary
            )
        }
    }
}

/**
 * Destructive / Safety action button styled with Emergency Red.
 * Minimum 48 dp height touch target.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentDescription: String = text,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = NaariSpacing.minTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        enabled = enabled && !isLoading,
        shape = NaariShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = EmergencyRed,
            contentColor = Color.White,
            disabledContainerColor = EmergencyRed.copy(alpha = 0.38f),
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
