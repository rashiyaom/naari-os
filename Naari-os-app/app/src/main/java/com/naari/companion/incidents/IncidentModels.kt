package com.naari.companion.incidents

/**
 * Origin trigger of an incident.
 */
enum class IncidentTrigger {
    FallDetected,
    ManualSos,
    SafeDemo
}

/**
 * Lifecycle state of an incident.
 */
enum class IncidentLifecycleState {
    Idle,
    Countdown,
    Dispatched,
    Resolved,
    Cancelled
}

/**
 * Concrete action taken or relayed during an incident.
 */
data class IncidentActionItem(
    val type: String, // "SMS", "CALL", "LOCATION"
    val target: String,
    val status: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Active incident data model.
 *
 * CRITICAL SAFETY REQUIREMENT:
 * The watch is the sole authority for countdown timing (watchDeadlineTimestampMs).
 * The phone never starts or calculates its own independent countdown.
 */
data class ActiveIncident(
    val incidentId: Long,
    val trigger: IncidentTrigger,
    val state: IncidentLifecycleState,
    val watchDeadlineTimestampMs: Long?, // Auth deadline from watch!
    val isDemo: Boolean = false,
    val actions: List<IncidentActionItem> = emptyList(),
    val startedAtMs: Long = System.currentTimeMillis()
) {
    fun remainingSeconds(currentTimeMs: Long = System.currentTimeMillis()): Int {
        val deadline = watchDeadlineTimestampMs ?: return 0
        val remainingMs = deadline - currentTimeMs
        return if (remainingMs > 0) ((remainingMs + 999) / 1000).toInt() else 0
    }
}
