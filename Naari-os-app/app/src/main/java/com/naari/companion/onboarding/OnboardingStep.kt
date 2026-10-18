package com.naari.companion.onboarding

/**
 * Six authoritative onboarding stages defined in PRD Section 1 & Master Prompt Task 14:
 * install → pair → grant capabilities → set guardians → verify readiness → wear watch.
 */
sealed class OnboardingStep(
    val stepIndex: Int,
    val stepNumber: Int,
    val title: String,
    val subtitle: String
) {
    object Welcome : OnboardingStep(
        stepIndex = 0,
        stepNumber = 1,
        title = "Welcome to Naari",
        subtitle = "Personal safety and companion system"
    )

    object PairWatch : OnboardingStep(
        stepIndex = 1,
        stepNumber = 2,
        title = "Pair Your Watch",
        subtitle = "Authenticate via Bluetooth Companion Manager"
    )

    object GrantCapabilities : OnboardingStep(
        stepIndex = 2,
        stepNumber = 3,
        title = "Grant Capabilities",
        subtitle = "Essential permissions with full transparency"
    )

    object SetGuardians : OnboardingStep(
        stepIndex = 3,
        stepNumber = 4,
        title = "Configure Guardians",
        subtitle = "Exactly 2 hardware emergency contact slots"
    )

    object VerifyReadiness : OnboardingStep(
        stepIndex = 4,
        stepNumber = 5,
        title = "Verify Readiness",
        subtitle = "Multi-factor check of system protection"
    )

    object WearWatch : OnboardingStep(
        stepIndex = 5,
        stepNumber = 6,
        title = "Wear Your Watch",
        subtitle = "Proper fit and emergency operation guide"
    )

    companion object {
        val allSteps: List<OnboardingStep> = listOf(
            Welcome,
            PairWatch,
            GrantCapabilities,
            SetGuardians,
            VerifyReadiness,
            WearWatch
        )

        fun fromIndex(index: Int): OnboardingStep {
            return allSteps.getOrElse(index.coerceIn(0, allSteps.lastIndex)) { Welcome }
        }
    }
}
