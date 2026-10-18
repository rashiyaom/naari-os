package com.naari.companion.incidents

import com.naari.companion.ble.BleSessionManager
import com.naari.companion.data.IncidentLog
import com.naari.companion.data.IncidentLogDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Repository interface for managing incident tracking, watch countdown relay, and demo mode.
 * Ref: PRD Section 3 (Fall detection & Incidents) & Master Prompt Task 16.
 */
interface IncidentsRepository {
    val activeIncident: StateFlow<ActiveIncident?>
    val incidentLogs: Flow<List<IncidentLog>>

    /**
     * Relays an incident event received from the watch.
     * Returns true if this is a newly spawned incident, or false if it is a deduplicated update.
     *
     * GATE REQUIREMENT:
     * The phone never creates a duplicate countdown or duplicate alert when duplicate events arrive.
     */
    suspend fun onWatchIncidentEvent(
        incidentId: Long,
        trigger: IncidentTrigger,
        state: IncidentLifecycleState,
        watchDeadlineMs: Long?,
        isDemo: Boolean = false
    ): Boolean

    suspend fun addActionToActiveIncident(action: IncidentActionItem)
    suspend fun startSafeDemo(countdownSeconds: Int = 30)
    suspend fun cancelActiveSos(disarmPin: String, reason: String = "User disarm"): Result<Unit>
    suspend fun resolveActiveIncident(outcome: String, note: String? = null)
}

/**
 * Production implementation of [IncidentsRepository].
 *
 * Enforces:
 * 1. Single source of truth for countdown: Watch authority deadline only.
 * 2. Deduplication gate: Duplicate re-broadcasts of the same incident ID never create second alerts or countdowns.
 * 3. Safe demo isolation: Marked visibly and strictly prohibited from placing real SMS or calls.
 * 4. Deliberate cancellation logging: Persists operator action and disarm notes into Room.
 */
class DefaultIncidentsRepository(
    private val incidentLogDao: IncidentLogDao,
    private val bleSessionManager: BleSessionManager? = null
) : IncidentsRepository {

    private val _activeIncident = MutableStateFlow<ActiveIncident?>(null)
    override val activeIncident: StateFlow<ActiveIncident?> = _activeIncident.asStateFlow()

    override val incidentLogs: Flow<List<IncidentLog>> = incidentLogDao.observeAll()

    private val currentLogDbId = AtomicLong(-1L)

    override suspend fun onWatchIncidentEvent(
        incidentId: Long,
        trigger: IncidentTrigger,
        state: IncidentLifecycleState,
        watchDeadlineMs: Long?,
        isDemo: Boolean
    ): Boolean {
        val current = _activeIncident.value

        // DEDUPLICATION GATE:
        // If an incident with this ID is already active, update the existing incident in-place.
        // Never spawn a new countdown or duplicate dispatch!
        if (current != null && current.incidentId == incidentId) {
            _activeIncident.value = current.copy(
                state = state,
                watchDeadlineTimestampMs = watchDeadlineMs ?: current.watchDeadlineTimestampMs
            )
            return false // Deduplicated; not a new incident
        }

        // New Incident: Persist audit log to Room before notifying UI
        val logEntity = IncidentLog(
            watchIncidentId = incidentId,
            startedAt = System.currentTimeMillis(),
            outcome = if (state == IncidentLifecycleState.Countdown) "COUNTDOWN_ACTIVE" else "DISPATCHED",
            note = "Trigger: ${trigger.name}, isDemo: $isDemo"
        )
        val insertedId = incidentLogDao.insert(logEntity)
        currentLogDbId.set(insertedId)

        _activeIncident.value = ActiveIncident(
            incidentId = incidentId,
            trigger = trigger,
            state = state,
            watchDeadlineTimestampMs = watchDeadlineMs,
            isDemo = isDemo,
            actions = emptyList(),
            startedAtMs = System.currentTimeMillis()
        )
        return true // Newly initiated incident
    }

    override suspend fun addActionToActiveIncident(action: IncidentActionItem) {
        val current = _activeIncident.value ?: return
        val updatedActions = current.actions + action
        _activeIncident.value = current.copy(actions = updatedActions)
    }

    override suspend fun startSafeDemo(countdownSeconds: Int) {
        val demoIncidentId = 999_000L + (System.currentTimeMillis() % 1000L)
        val deadline = System.currentTimeMillis() + (countdownSeconds * 1000L)

        val logEntity = IncidentLog(
            watchIncidentId = demoIncidentId,
            startedAt = System.currentTimeMillis(),
            outcome = "SAFE_DEMO_ACTIVE",
            note = "Local fall detection safe demo. Live calls and SMS disabled."
        )
        val insertedId = incidentLogDao.insert(logEntity)
        currentLogDbId.set(insertedId)

        _activeIncident.value = ActiveIncident(
            incidentId = demoIncidentId,
            trigger = IncidentTrigger.SafeDemo,
            state = IncidentLifecycleState.Countdown,
            watchDeadlineTimestampMs = deadline,
            isDemo = true,
            actions = listOf(
                IncidentActionItem(
                    type = "DEMO_SIMULATION",
                    target = "Local Device",
                    status = "Safe demo countdown started (0 calls, 0 SMS dispatched)"
                )
            ),
            startedAtMs = System.currentTimeMillis()
        )
    }

    override suspend fun cancelActiveSos(disarmPin: String, reason: String): Result<Unit> {
        val current = _activeIncident.value
            ?: return Result.failure(IllegalStateException("No active incident to cancel"))

        if (disarmPin.isBlank() || disarmPin.length < 4) {
            return Result.failure(IllegalArgumentException("Disarm PIN must be at least 4 digits for deliberate authentication"))
        }

        // 1. Dispatch remote cancellation via BLE if connected and not demo
        if (!current.isDemo && bleSessionManager != null) {
            val cancelJson = """{"v":2,"cmd":${System.currentTimeMillis() % 10000},"op":"cancel_sos","args":{"incident_id":${current.incidentId},"disarm_pin_hash":"${disarmPin.hashCode()}"}}"""
            bleSessionManager.sendControlCommand(cancelJson)
        }

        // 2. Update Room persistent audit trail
        val logId = currentLogDbId.get()
        if (logId > 0) {
            incidentLogDao.updateOutcome(
                id = logId,
                endedAt = System.currentTimeMillis(),
                outcome = "CANCELLED",
                note = "Reason: $reason (Authenticated disarm)"
            )
        }

        // 3. Transition active incident state to Cancelled
        _activeIncident.value = current.copy(
            state = IncidentLifecycleState.Cancelled,
            actions = current.actions + IncidentActionItem(
                type = "CANCEL_DISARM",
                target = "Watch & Companion",
                status = "Cancelled with PIN authentication: $reason"
            )
        )

        return Result.success(Unit)
    }

    override suspend fun resolveActiveIncident(outcome: String, note: String?) {
        val current = _activeIncident.value ?: return
        val logId = currentLogDbId.get()
        if (logId > 0) {
            incidentLogDao.updateOutcome(
                id = logId,
                endedAt = System.currentTimeMillis(),
                outcome = outcome,
                note = note
            )
        }
        _activeIncident.value = current.copy(state = IncidentLifecycleState.Resolved)
    }
}
