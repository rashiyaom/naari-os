package com.naari.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for IncidentLog records.
 */
@Dao
interface IncidentLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: IncidentLog): Long

    @Query("SELECT * FROM incident_logs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<IncidentLog>>

    @Query("SELECT * FROM incident_logs WHERE id = :id")
    suspend fun getById(id: Long): IncidentLog?

    @Query("SELECT * FROM incident_logs WHERE watchIncidentId = :watchIncidentId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getByWatchIncidentId(watchIncidentId: Long): IncidentLog?

    @Query("UPDATE incident_logs SET endedAt = :endedAt, outcome = :outcome, note = :note WHERE id = :id")
    suspend fun updateOutcome(id: Long, endedAt: Long, outcome: String, note: String?): Int
}
