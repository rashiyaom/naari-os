package com.naari.companion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for hardware guardian slots (0 and 1).
 * Ref: PRD Section 7 & Master Prompt Task 7/8.
 */
@Dao
interface GuardiansDao {

    @Query("SELECT * FROM guardians WHERE slotIndex = :slotIndex")
    suspend fun getSlot(slotIndex: Int): Guardian?

    @Query("SELECT * FROM guardians WHERE slotIndex = :slotIndex")
    fun observeSlot(slotIndex: Int): Flow<Guardian?>

    @Query("SELECT * FROM guardians ORDER BY slotIndex ASC")
    suspend fun getAll(): List<Guardian>

    @Query("SELECT * FROM guardians ORDER BY slotIndex ASC")
    fun observeAll(): Flow<List<Guardian>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(guardian: Guardian)

    @Query("DELETE FROM guardians WHERE slotIndex = :slotIndex")
    suspend fun clearSlot(slotIndex: Int): Int

    @Transaction
    suspend fun replaceAll(guardians: List<Guardian>) {
        clearAll()
        guardians.forEach { upsert(it) }
    }

    @Query("DELETE FROM guardians")
    suspend fun clearAll(): Int
}
