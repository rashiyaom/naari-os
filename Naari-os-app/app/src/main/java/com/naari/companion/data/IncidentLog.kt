package com.naari.companion.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity recording incident timeline and escalation history.
 * Ref: PRD Section 7 & Master Prompt Task 7.
 */
@Entity(
    tableName = "incident_logs",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["watchIncidentId"])
    ]
)
data class IncidentLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val watchIncidentId: Long,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val outcome: String,
    val note: String? = null
)
