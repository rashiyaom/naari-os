package com.naari.companion.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale implementing PRD Section 4:
 * 4, 8, 12, 16, 24, 32 dp.
 */
object NaariSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp

    // Touch targets: minimum 48 dp per accessibility rules
    val minTouchTarget: Dp = 48.dp

    // Card padding
    val cardPadding: Dp = 18.dp
}
