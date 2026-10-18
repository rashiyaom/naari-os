package com.naari.companion.history

/**
 * Utility for redacting personally identifiable information (phone numbers, full alert payloads)
 * in diagnostic UI displays and logs per privacy and safety requirements.
 * Ref: PRD Section 7 & Master Prompt Task 12.
 */
object RedactionUtils {

    /**
     * Redacts phone number for user privacy.
     * Examples:
     * - `+919876543210` -> `+91 98*** **210`
     * - `9876543210`    -> `98*** **210`
     * - `112`           -> `112` (short emergency number preserved)
     */
    fun redactPhoneNumber(phone: String?): String {
        if (phone.isNullOrBlank()) return "Unknown"
        val trimmed = phone.trim()
        if (trimmed.length <= 4) return trimmed // Emergency short codes remain readable

        return if (trimmed.startsWith("+")) {
            val countryCode = trimmed.take(3) // e.g. +91
            val remainder = trimmed.drop(3)
            if (remainder.length >= 6) {
                val start = remainder.take(2)
                val end = remainder.takeLast(3)
                "$countryCode $start*** **$end"
            } else {
                "$countryCode ***"
            }
        } else {
            if (trimmed.length >= 6) {
                val start = trimmed.take(2)
                val end = trimmed.takeLast(3)
                "$start*** **$end"
            } else {
                "***" + trimmed.takeLast(2)
            }
        }
    }

    /**
     * Truncates message payload snippet to prevent overflowing UI cards.
     */
    fun redactMessageSnippet(message: String?, maxLength: Int = 40): String {
        if (message.isNullOrBlank()) return "No message content"
        val trimmed = message.trim()
        return if (trimmed.length > maxLength) {
            "${trimmed.take(maxLength)}…"
        } else {
            trimmed
        }
    }
}
