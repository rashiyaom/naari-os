package com.naari.companion.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * High-level status of OEM and Android battery restrictions.
 * Ref: PRD Section 7 & Master Prompt Task 13.
 */
sealed class BatteryRestrictionStatus {
    /**
     * App is exempted from battery optimizations; background BLE monitoring operates at full reliability.
     */
    data object Unrestricted : BatteryRestrictionStatus()

    /**
     * Battery optimization is active and may delay watch alerts when phone is idle or screen is off.
     */
    data class Optimized(
        val warning: String = "Battery optimization is active. Emergency watch alerts may be delayed while phone screen is off or idle."
    ) : BatteryRestrictionStatus()
}

/**
 * Helper for evaluating system battery restrictions, guiding users to exemptions,
 * and providing truthful recovery guidance for force-stop scenarios.
 *
 * SAFETY RULES:
 * - Explain battery restrictions ONLY when they affect readiness
 * - NEVER promise reconnection after a force-stop; explain recovery instead
 */
object BatteryOptimizationHelper {

    const val FORCE_STOP_RECOVERY_GUIDANCE =
        "When an app is force-stopped in Android Settings, the operating system permanently halts all background services and BLE listeners. Android strictly prohibits automatic restarts after a force-stop. To resume watch monitoring, you must manually open the Naari app."

    /**
     * Returns whether the companion app is currently exempt from Android battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(PowerManager::class.java) ?: return true
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    /**
     * Evaluates whether battery restrictions affect readiness.
     */
    fun getBatteryRestrictionStatus(context: Context): BatteryRestrictionStatus {
        return if (isIgnoringBatteryOptimizations(context)) {
            BatteryRestrictionStatus.Unrestricted
        } else {
            BatteryRestrictionStatus.Optimized()
        }
    }

    /**
     * Creates an intent to request battery optimization exemption from the user.
     */
    @SuppressLint("BatteryLife")
    fun createRequestExemptionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Creates an intent to open general battery optimization settings.
     */
    fun createBatterySettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }
}
