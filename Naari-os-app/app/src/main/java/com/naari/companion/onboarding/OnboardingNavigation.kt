package com.naari.companion.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Onboarding route wrapper conforming to PRD Section 1 & Task 14.
 */
@Composable
fun OnboardingRoute(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScreen(
        onFinishOnboarding = onFinish,
        modifier = modifier
    )
}
