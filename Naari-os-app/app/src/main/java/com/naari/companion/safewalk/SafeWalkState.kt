package com.naari.companion.safewalk

/**
 * State representation for the Safe Walk dead-man's journey timer.
 */
sealed interface SafeWalkState {
    /** Timer is inactive */
    data object Idle : SafeWalkState

    /** Timer is actively counting down */
    data class Active(
        val totalMinutes: Int,
        val targetEpochMs: Long,
        val remainingSeconds: Long
    ) : SafeWalkState {
        val progress: Float
            get() = if (totalMinutes > 0) {
                1f - (remainingSeconds.toFloat() / (totalMinutes * 60f)).coerceIn(0f, 1f)
            } else 0f

        val formattedRemaining: String
            get() {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                return "%02d:%02d".format(mins, secs)
            }
    }

    /** Timer expired and emergency SOS was dispatched to guardians */
    data class Triggered(
        val triggeredAtEpochMs: Long,
        val guardiansAlertedCount: Int,
        val locationSummary: String
    ) : SafeWalkState

    /** User checked in safely before expiry */
    data object Completed : SafeWalkState
}
