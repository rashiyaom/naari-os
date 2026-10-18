package com.naari.companion.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback utility conforming to PRD Section 4 & Task 18:
 * - Used sparingly for critical confirmations, safety disarms, and alerts.
 * - Strictly respects system [Settings.System.HAPTIC_FEEDBACK_ENABLED] setting.
 * - Respects system vibrator capabilities without crashing on devices lacking tactile motors.
 */
interface NaariHaptics {
    val isSystemEnabled: Boolean
    fun click()
    fun confirm()
    fun warning()
    fun alert()
}

class DefaultNaariHaptics(
    private val context: Context,
    private val view: View? = null
) : NaariHaptics {

    override val isSystemEnabled: Boolean
        @Suppress("DEPRECATION")
        get() = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) != 0
        } catch (_: Exception) {
            true
        }

    override fun click() {
        if (!isSystemEnabled) return
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun confirm() {
        if (!isSystemEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    override fun warning() {
        if (!isSystemEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    override fun alert() {
        if (!isSystemEnabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 80, 50, 80),
                            intArrayOf(0, 255, 0, 255),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 80, 50, 80), -1)
                }
            } else {
                view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        } catch (_: Exception) {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

@Composable
fun rememberNaariHaptics(): NaariHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view) {
        DefaultNaariHaptics(context, view)
    }
}
