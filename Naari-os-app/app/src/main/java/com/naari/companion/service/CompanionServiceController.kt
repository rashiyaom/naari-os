package com.naari.companion.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Controller for starting and stopping the [CompanionService].
 */
object CompanionServiceController {

    fun start(context: Context) {
        val intent = Intent(context, CompanionService::class.java).apply {
            action = CompanionService.ACTION_START_SERVICE
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, CompanionService::class.java).apply {
            action = CompanionService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
    }
}
