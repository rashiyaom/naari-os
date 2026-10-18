package com.naari.companion

import com.naari.companion.navigation.AppDestinations
import com.naari.companion.navigation.BottomNavDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

    @Test
    fun bottomNavDestinations_haveUniqueRoutesAndLabels() {
        val items = BottomNavDestination.items
        assertEquals(4, items.size)

        val routes = items.map { it.route }
        assertEquals(4, routes.distinct().size)
        assertTrue(routes.contains("home"))
        assertTrue(routes.contains("guardians"))
        assertTrue(routes.contains("recordings"))
        assertTrue(routes.contains("watch"))
    }

    @Test
    fun bottomNavDestinations_haveAccessibleDescriptions() {
        BottomNavDestination.items.forEach { dest ->
            assertTrue(
                "Destination ${dest.route} must have a non-blank contentDescription",
                dest.contentDescription.isNotBlank()
            )
            assertTrue(
                "Destination ${dest.route} must have a non-blank label",
                dest.label.isNotBlank()
            )
        }
    }

    @Test
    fun secondaryDestinations_haveExpectedRoutes() {
        assertEquals("settings", AppDestinations.SETTINGS)
        assertEquals("permissions", AppDestinations.PERMISSIONS)
        assertEquals("history", AppDestinations.HISTORY)
        assertEquals("onboarding", AppDestinations.ONBOARDING)
        assertEquals("landing", AppDestinations.LANDING)
    }
}
