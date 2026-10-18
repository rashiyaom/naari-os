package com.naari.companion

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base Application class for Naari OS Companion initializing Hilt dependency injection.
 * Recovers any in-flight safety ledger requests into UnknownOutcome state upon startup.
 */
@HiltAndroidApp
class NaariApplication : Application() {

    @javax.inject.Inject lateinit var watchSync: com.naari.companion.sync.WatchSyncCoordinator

    override fun onCreate() {
        super.onCreate()
        watchSync.start()
        CoroutineScope(Dispatchers.IO).launch {
            val db = com.naari.companion.data.AppDatabase.getInstance(this@NaariApplication)
            val repo = com.naari.companion.ledger.DefaultRequestLedgerRepository(db.requestLedgerDao())
            repo.recoverInflightOnStartup()
        }
    }
}
