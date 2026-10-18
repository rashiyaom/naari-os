package com.naari.companion.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.naari.companion.designsystem.NaariIcons

/**
 * Top-level destinations displayed in the bottom navigation bar.
 */
sealed class BottomNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    object Home : BottomNavDestination(
        route = "home",
        label = "Home",
        icon = NaariIcons.Watch,
        contentDescription = "Navigate to Home dashboard"
    )

    object Guardians : BottomNavDestination(
        route = "guardians",
        label = "Guardians",
        icon = NaariIcons.Shield,
        contentDescription = "Navigate to Guardians emergency contacts"
    )

    object Recordings : BottomNavDestination(
        route = "recordings",
        label = "Recordings",
        icon = NaariIcons.Mic,
        contentDescription = "Navigate to Audio recordings"
    )

    object Watch : BottomNavDestination(
        route = "watch",
        label = "Watch",
        icon = NaariIcons.Sliders,
        contentDescription = "Navigate to Watch controls and settings"
    )

    companion object {
        val items: List<BottomNavDestination>
            get() = listOf(Home, Guardians, Recordings, Watch)
    }
}

/**
 * Secondary destinations reachable via labeled entries and top navigation.
 */
object AppDestinations {
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val HISTORY = "history"
    const val ONBOARDING = "onboarding"
    const val INCIDENTS = "incidents"
    const val LANDING = "landing"
    const val DIAGNOSTICS = "diagnostics"
    const val TERMS = "terms"
}
