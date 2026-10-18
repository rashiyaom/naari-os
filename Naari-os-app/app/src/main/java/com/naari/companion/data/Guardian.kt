package com.naari.companion.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing one of the two hardware guardian slots (0 or 1).
 * Ref: PRD Section 7 & Master Prompt Task 7/8.
 */
@Entity(tableName = "guardians")
data class Guardian(
    @PrimaryKey
    val slotIndex: Int, // Must be 0 or 1
    val name: String,
    val phone: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(slotIndex in 0..1) { "Guardian slot index must be 0 or 1, got $slotIndex" }
    }
}
