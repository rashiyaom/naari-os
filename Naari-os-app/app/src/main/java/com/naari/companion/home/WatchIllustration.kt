package com.naari.companion.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naari.companion.ble.ReadinessState
import com.naari.companion.designsystem.AttentionAmber
import com.naari.companion.designsystem.EmergencyRed
import com.naari.companion.designsystem.TealAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centered original watch illustration conforming to PRD Section 4 & Master Prompt Task 14:
 * - High-resolution Apple-inspired watch chassis (410x502 CO5300 display proportion)
 * - Dynamic status ring/aura reflecting live [ReadinessState]
 * - Straps, digital crown, OLED screen, time, and battery telemetry
 * - Full TalkBack semantic accessibility
 */
@Composable
fun WatchIllustration(
    readiness: ReadinessState,
    batteryPercent: Int?,
    modifier: Modifier = Modifier
) {
    // Dynamic ring color based on live readiness
    val targetRingColor = when (readiness) {
        ReadinessState.Ready -> TealAccent
        ReadinessState.BluetoothOff -> EmergencyRed
        else -> AttentionAmber
    }

    val animatedRingColor by animateColorAsState(
        targetValue = targetRingColor,
        animationSpec = com.naari.companion.designsystem.NaariMotion.responsiveSpec(tween(durationMillis = 240)),
        label = "WatchStatusRingColor"
    )

    val formattedTime = rememberFormattedTime()

    val accessibilityDesc = "Naari Watch illustration. Readiness status: ${readiness.name}. Battery: ${batteryPercent?.let { "$it percent" } ?: "Not available"}."

    Box(
        modifier = modifier
            .semantics { contentDescription = accessibilityDesc }
            .size(width = 200.dp, height = 250.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Straps (top and bottom)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strapWidth = 84.dp.toPx()
            val strapLeft = (size.width - strapWidth) / 2f

            // Top strap
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E222B), Color(0xFF2C323E))
                ),
                topLeft = Offset(strapLeft, 0f),
                size = Size(strapWidth, 42.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Top strap subtle ridges
            for (i in 1..3) {
                val y = i * 9.dp.toPx()
                drawLine(
                    color = Color(0xFF15181F),
                    start = Offset(strapLeft + 8.dp.toPx(), y),
                    end = Offset(strapLeft + strapWidth - 8.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Bottom strap
            val bottomStrapY = size.height - 42.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C323E), Color(0xFF1E222B))
                ),
                topLeft = Offset(strapLeft, bottomStrapY),
                size = Size(strapWidth, 42.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Bottom strap ridges
            for (i in 1..3) {
                val y = bottomStrapY + (i * 9.dp.toPx())
                drawLine(
                    color = Color(0xFF15181F),
                    start = Offset(strapLeft + 8.dp.toPx(), y),
                    end = Offset(strapLeft + strapWidth - 8.dp.toPx(), y),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // 2. Hardware buttons (Crown and action button on right edge)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val chassisWidth = 144.dp.toPx()
            val chassisHeight = 176.dp.toPx()
            val chassisRight = (size.width + chassisWidth) / 2f
            val centerY = size.height / 2f

            // Digital Crown (Upper right)
            drawRoundRect(
                color = Color(0xFF4A5160),
                topLeft = Offset(chassisRight - 2.dp.toPx(), centerY - 38.dp.toPx()),
                size = Size(8.dp.toPx(), 26.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Physical side button (Lower right)
            drawRoundRect(
                color = Color(0xFF383E4A),
                topLeft = Offset(chassisRight - 2.dp.toPx(), centerY + 12.dp.toPx()),
                size = Size(6.dp.toPx(), 24.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // 3. Dynamic Outer Glow / Status Aura Ring
        Box(
            modifier = Modifier
                .size(width = 152.dp, height = 184.dp)
                .border(
                    width = 2.5.dp,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedRingColor.copy(alpha = 0.9f),
                            animatedRingColor.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
        )

        // 4. Watch Chassis Case
        Box(
            modifier = Modifier
                .size(width = 144.dp, height = 176.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF282D37), Color(0xFF1B1E26))
                    )
                )
                .border(
                    width = 1.dp,
                    color = animatedRingColor.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // 5. OLED Display Screen (410x502 CO5300 ratio)
            Box(
                modifier = Modifier
                    .size(width = 126.dp, height = 154.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF090B0E))
                    .border(0.5.dp, Color(0xFF323846), RoundedCornerShape(18.dp))
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Watch Header: NAARI OS + Live LED indicator
                    Row(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "NAARI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 1.2.sp
                            ),
                            color = Color(0xFFA0A6B2),
                            fontWeight = FontWeight.Bold
                        )

                        // Circular micro status LED
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(animatedRingColor)
                        )
                    }

                    // Watch Face Center: Digital Clock
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(2f)
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color.White
                        )
                        Text(
                            text = when (readiness) {
                                ReadinessState.Ready -> "PROTECTED"
                                ReadinessState.Connecting -> "SYNCING"
                                ReadinessState.BluetoothOff -> "BT OFF"
                                ReadinessState.PermissionLimited -> "LIMITED"
                                else -> "STANDBY"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = animatedRingColor
                        )
                    }

                    // Watch Face Footer: Battery & Link status
                    Row(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = batteryPercent?.let { "$it%" } ?: "--%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFFC4C9D4)
                        )

                        Text(
                            text = if (readiness == ReadinessState.Ready) "READY" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (readiness == ReadinessState.Ready) TealAccent else Color(0xFF888E99)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberFormattedTime(): String {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    return remember { formatter.format(Date()) }
}
