package com.naari.companion.actions

import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager

/**
 * Interface contract for detecting emergency service phone numbers.
 * Ref: PRD Section 7 & Master Prompt Task 10.
 */
interface EmergencyNumberDetector {
    fun isEmergencyNumber(phoneNumber: String): Boolean
}

/**
 * Authoritative implementation of [EmergencyNumberDetector].
 *
 * Enforces detection of:
 * - India national emergency numbers: 112, 100, 101, 102, 108, 1091 (Women Helpline), 181 (Women in Distress), 1098
 * - International universal emergency services: 911 (US/CA), 999 (UK), 000 (AU), 111 (NZ), 110, 119
 * - Platform-level TelephonyManager / PhoneNumberUtils validation when available
 */
class DefaultEmergencyNumberDetector(
    private val context: Context? = null
) : EmergencyNumberDetector {

    override fun isEmergencyNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        if (cleanNumber.isEmpty()) return false

        val digitsOnly = cleanNumber.removePrefix("+")

        // 1. Check authoritative static emergency services set
        if (EMERGENCY_NUMBERS.contains(digitsOnly) || EMERGENCY_NUMBERS.contains(cleanNumber)) {
            return true
        }

        // Check if prefixed with India country code +91
        if (digitsOnly.startsWith("91") && digitsOnly.length in 5..6) {
            val localNumber = digitsOnly.removePrefix("91")
            if (EMERGENCY_NUMBERS.contains(localNumber)) {
                return true
            }
        }

        // 2. Query Android platform telephony emergency database if Context is provided
        context?.let { ctx ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val telephonyManager = ctx.getSystemService(TelephonyManager::class.java)
                    if (telephonyManager != null && telephonyManager.isEmergencyNumber(cleanNumber)) {
                        return true
                    }
                }
                @Suppress("DEPRECATION")
                if (PhoneNumberUtils.isEmergencyNumber(cleanNumber)) {
                    return true
                }
            } catch (_: Exception) {
                // Ignore platform lookup failures on unprovisioned devices / emulators
            }
        }

        return false
    }

    companion object {
        val EMERGENCY_NUMBERS = setOf(
            "112",  // Universal / India / EU
            "911",  // US / North America
            "1091", // India Women Helpline
            "181",  // India Women in Distress
            "100",  // India Police
            "101",  // India Fire
            "102",  // India Ambulance
            "108",  // India Emergency Medical & Disaster
            "1098", // India Childline
            "999",  // UK Emergency
            "000",  // Australia Emergency
            "111",  // New Zealand Emergency
            "110",  // Japan Police
            "119"   // Japan Fire / Ambulance
        )
    }
}
