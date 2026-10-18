package com.naari.companion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.naari.companion.ledger.RequestLedgerDao
import com.naari.companion.ledger.RequestLedgerEntry

/**
 * Authoritative Room database for Naari OS Companion.
 * Houses the request execution ledger, guardian slots, and incident history.
 */
@Database(
    entities = [
        RequestLedgerEntry::class,
        Guardian::class,
        IncidentLog::class,
        RecordingEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun requestLedgerDao(): RequestLedgerDao
    abstract fun guardiansDao(): GuardiansDao
    abstract fun incidentLogDao(): IncidentLogDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        const val DATABASE_NAME = "naari_companion.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Creates an isolated in-memory database instance for testing.
         */
        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
