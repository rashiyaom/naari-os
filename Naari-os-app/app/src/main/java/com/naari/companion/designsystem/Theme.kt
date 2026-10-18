package com.naari.companion.designsystem

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MinimalistWhite,
    onPrimary = MinimalistBlack,
    secondary = MinimalistSurfaceTertiary,
    onSecondary = MinimalistWhite,
    error = ModernRed,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = MinimalistWhite,
    onSurface = MinimalistWhite,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155)
)

private val LightColorScheme = lightColorScheme(
    primary = MinimalistBlack,
    onPrimary = MinimalistWhite,
    primaryContainer = MinimalistBlack,
    onPrimaryContainer = MinimalistWhite,
    secondary = MinimalistSurfaceSecondary,
    onSecondary = MinimalistTextPrimary,
    secondaryContainer = MinimalistSurfaceSecondary,
    onSecondaryContainer = MinimalistTextPrimary,
    error = ModernRed,
    onError = MinimalistWhite,
    errorContainer = ModernRedContainer,
    onErrorContainer = ModernRed,
    background = MinimalistBackground,
    surface = MinimalistSurface,
    onBackground = MinimalistTextPrimary,
    onSurface = MinimalistTextPrimary,
    surfaceVariant = MinimalistSurfaceSecondary,
    onSurfaceVariant = MinimalistTextSecondary,
    outline = MinimalistBorder
)

@Composable
fun NaariTheme(
    darkTheme: Boolean = false,
    forceReducedMotion: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemReducedMotion = remember(context) {
        try {
            val animScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            val transScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
            )
            animScale == 0f || transScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    val isReducedMotion = forceReducedMotion ?: systemReducedMotion
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalReducedMotion provides isReducedMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NaariTypography,
            content = content
        )
    }
}
