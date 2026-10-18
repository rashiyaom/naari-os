package com.naari.companion.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Motion specifications implementing PRD Section 4:
 * 180–280 ms ordinary transitions, interruptible restrained spring, reduced-motion support.
 */
object NaariMotion {
    const val DurationShort = 180
    const val DurationMedium = 220
    const val DurationLong = 280

    val tweenShort = tween<Float>(durationMillis = DurationShort, easing = FastOutSlowInEasing)
    val tweenMedium = tween<Float>(durationMillis = DurationMedium, easing = FastOutSlowInEasing)
    val tweenLong = tween<Float>(durationMillis = DurationLong, easing = FastOutSlowInEasing)

    /**
     * Restrained interruptible spring for state changes.
     */
    val restrainedSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /**
     * Helper to return instantaneous snap if reduced motion is requested, or the target spec otherwise.
     */
    @Composable
    fun <T> responsiveSpec(normalSpec: AnimationSpec<T>): AnimationSpec<T> {
        val reduced = LocalReducedMotion.current
        return if (reduced) snap() else normalSpec
    }
}

/**
 * CompositionLocal to track if reduced-motion is requested by accessibility settings or user preference.
 */
val LocalReducedMotion = compositionLocalOf { false }
