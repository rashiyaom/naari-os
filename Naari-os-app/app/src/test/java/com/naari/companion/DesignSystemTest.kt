package com.naari.companion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.naari.companion.designsystem.AttentionAmber
import com.naari.companion.designsystem.DarkBackground
import com.naari.companion.designsystem.DarkSurface
import com.naari.companion.designsystem.EmergencyRed
import com.naari.companion.designsystem.LightBackground
import com.naari.companion.designsystem.LightSurface
import com.naari.companion.designsystem.NaariMotion
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.ReadinessLevel
import com.naari.companion.designsystem.TealAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTest {

    @Test
    fun colorTokens_matchPrdSpecification() {
        assertEquals(Color(0xFFF5F5F7), LightBackground)
        assertEquals(Color(0xFF08090C), DarkBackground)
        assertEquals(Color(0xFFFFFFFF), LightSurface)
        assertEquals(Color(0xFF17191F), DarkSurface)
        assertEquals(Color(0xFF00BFA5), TealAccent)
        assertEquals(Color(0xFFE63D4F), EmergencyRed)
        assertEquals(Color(0xFFFFA000), AttentionAmber)
    }

    @Test
    fun spacingScale_matchesPrdSpecification() {
        assertEquals(4.dp, NaariSpacing.xxs)
        assertEquals(8.dp, NaariSpacing.xs)
        assertEquals(12.dp, NaariSpacing.sm)
        assertEquals(16.dp, NaariSpacing.md)
        assertEquals(24.dp, NaariSpacing.lg)
        assertEquals(32.dp, NaariSpacing.xl)
    }

    @Test
    fun touchTargets_satisfyAccessibilityMinimum() {
        assertTrue("Touch target must be at least 48 dp", NaariSpacing.minTouchTarget >= 48.dp)
    }

    @Test
    fun motionDurations_fallWithinPrdBounds() {
        assertTrue("Short duration within 180-280 ms", NaariMotion.DurationShort in 180..280)
        assertTrue("Medium duration within 180-280 ms", NaariMotion.DurationMedium in 180..280)
        assertTrue("Long duration within 180-280 ms", NaariMotion.DurationLong in 180..280)
    }

    @Test
    fun readinessLevels_coverAllRequiredStates() {
        val states = ReadinessLevel.values().map { it.name }
        assertTrue(states.contains("BluetoothOff"))
        assertTrue(states.contains("Disconnected"))
        assertTrue(states.contains("Connecting"))
        assertTrue(states.contains("Paired"))
        assertTrue(states.contains("ConnectedServiceUnavailable"))
        assertTrue(states.contains("Ready"))
        assertTrue(states.contains("PermissionLimited"))
    }
}
