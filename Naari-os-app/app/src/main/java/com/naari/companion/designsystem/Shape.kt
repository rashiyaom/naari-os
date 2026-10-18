package com.naari.companion.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shape scale implementing PRD Section 4:
 * Card corners: 20 to 24 dp.
 */
object NaariShapes {
    val card = RoundedCornerShape(24.dp)
    val cardLarge = RoundedCornerShape(28.dp)
    val button = RoundedCornerShape(16.dp)
    val buttonPill = CircleShape
    val chip = RoundedCornerShape(16.dp)
    val iconSquircle = RoundedCornerShape(14.dp)
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val badge = RoundedCornerShape(10.dp)
}
