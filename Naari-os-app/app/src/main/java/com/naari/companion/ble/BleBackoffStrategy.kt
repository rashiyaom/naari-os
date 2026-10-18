package com.naari.companion.ble

import kotlin.math.min
import kotlin.math.pow

/**
 * Exponential backoff calculation for BLE reconnection attempts.
 * Specified sequence: 1s -> 2s -> 4s -> 8s -> 16s -> 30s cap.
 */
object BleBackoffStrategy {
    const val INITIAL_DELAY_MS: Long = 1_000L
    const val MAX_DELAY_MS: Long = 30_000L
    private const val MULTIPLIER: Double = 2.0

    /**
     * Calculates the reconnection delay for a 0-indexed attempt count.
     * attempt 0: 1000ms
     * attempt 1: 2000ms
     * attempt 2: 4000ms
     * attempt 3: 8000ms
     * attempt 4: 16000ms
     * attempt >= 5: 30000ms
     */
    fun calculateDelayMs(attempt: Int): Long {
        if (attempt <= 0) return INITIAL_DELAY_MS
        val calculated = (INITIAL_DELAY_MS * MULTIPLIER.pow(attempt.toDouble())).toLong()
        return min(calculated, MAX_DELAY_MS)
    }
}
