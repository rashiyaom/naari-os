package com.naari.companion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import com.naari.companion.MainActivity
import com.naari.companion.R
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import com.naari.companion.safewalk.SafeWalkState
import com.naari.companion.service.CompanionServiceDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 1-Tap Home Screen Safety Widget for Naari OS.
 *
 * Allows instant 1-tap:
 * 1. "Check-in" -> Dispatches reassuring SMS with live Google Maps link to guardians.
 * 2. "Safe Walk" -> Starts 15-minute Dead-Man's journey timer, or confirms safe arrival.
 * 3. "SOS" -> Immediate emergency SOS with live GPS.
 */
class NaariSafetyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, null, null)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_WIDGET_CHECKIN -> {
                handleCheckIn(context)
            }
            ACTION_WIDGET_SAFEWALK -> {
                handleSafeWalkToggle(context)
            }
            ACTION_WIDGET_SOS -> {
                handleImmediateSos(context)
            }
            ACTION_UPDATE_WIDGETS -> {
                val customStatus = intent.getStringExtra(EXTRA_STATUS_TEXT)
                val isSuccess = intent.getBooleanExtra(EXTRA_IS_SUCCESS, true)
                updateAllWidgets(context, customStatus, isSuccess)
            }
        }
    }

    private fun handleCheckIn(context: Context) {
        showToast(context, "Acquiring GPS & sending Safe Check-in...")
        updateAllWidgets(context, "Sending...", true)

        widgetScope.launch {
            try {
                val checkInManager = CompanionServiceDependencies.getSafeCheckInManager(context)
                val result = checkInManager.sendSafeCheckIn()

                result.onSuccess { res ->
                    showToast(context, "✓ Reached Safely sent to ${res.guardiansMessagedCount} guardian(s)")
                    updateAllWidgets(context, "✓ Check-in sent (${res.guardiansMessagedCount})", true)

                    // Revert status to Standby after 4 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        updateAllWidgets(context)
                    }, 4000)
                }.onFailure { err ->
                    showToast(context, "Check-in failed: ${err.message}")
                    updateAllWidgets(context, "Check-in failed", false)
                }
            } catch (e: Exception) {
                showToast(context, "Error: ${e.message}")
                updateAllWidgets(context, "Error", false)
            }
        }
    }

    private fun handleSafeWalkToggle(context: Context) {
        val safeWalkManager = CompanionServiceDependencies.getSafeWalkManager(context)
        val currentState = safeWalkManager.state.value

        if (currentState is SafeWalkState.Active) {
            safeWalkManager.arriveSafely()
            showToast(context, "✓ Arrived safely! Safe Walk completed.")
        } else {
            safeWalkManager.startJourney(15)
            showToast(context, "🚶‍♀️ Safe Walk started (15 mins). Dead-Man's switch active.")
        }

        updateAllWidgets(context)
    }

    private fun handleImmediateSos(context: Context) {
        showToast(context, "🚨 Triggering Emergency SOS...")
        updateAllWidgets(context, "🚨 SOS Triggered", false)

        widgetScope.launch {
            try {
                val dispatcher = CompanionServiceDependencies.getActionDispatcher(context)
                val request = WatchRequest(
                    id = System.currentTimeMillis(),
                    type = WatchRequestType.LOCATION_SMS
                )

                dispatcher.dispatchRequest(request, "DIRECT_WIDGET")
                showToast(context, "🚨 Emergency SOS sent to guardians!")
            } catch (e: Exception) {
                showToast(context, "SOS Dispatch failed: ${e.message}")
            }
        }
    }

    private fun showToast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_WIDGET_CHECKIN = "com.naari.companion.widget.ACTION_CHECKIN"
        const val ACTION_WIDGET_SAFEWALK = "com.naari.companion.widget.ACTION_SAFEWALK"
        const val ACTION_WIDGET_SOS = "com.naari.companion.widget.ACTION_SOS"
        const val ACTION_UPDATE_WIDGETS = "com.naari.companion.widget.ACTION_UPDATE_WIDGETS"
        const val EXTRA_STATUS_TEXT = "extra_status_text"
        const val EXTRA_IS_SUCCESS = "extra_is_success"

        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateAllWidgets(
            context: Context,
            customStatus: String? = null,
            isSuccess: Boolean = true
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val thisWidget = ComponentName(context, NaariSafetyWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget) ?: return

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, customStatus, isSuccess)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            customStatus: String?,
            isSuccess: Boolean?
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_naari_safety)

            // 1. App Launch Intent
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_app, openAppPendingIntent)

            // 2. Check-in Button Intent
            val checkInIntent = Intent(context, NaariSafetyWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_CHECKIN
            }
            val checkInPendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                checkInIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_checkin, checkInPendingIntent)

            // 3. Safe Walk Button Intent
            val safeWalkIntent = Intent(context, NaariSafetyWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_SAFEWALK
            }
            val safeWalkPendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                safeWalkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_safewalk, safeWalkPendingIntent)

            // 4. SOS Button Intent
            val sosIntent = Intent(context, NaariSafetyWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_SOS
            }
            val sosPendingIntent = PendingIntent.getBroadcast(
                context,
                103,
                sosIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_sos, sosPendingIntent)

            // 5. Update Status & Safe Walk State
            val safeWalkState = try {
                CompanionServiceDependencies.getSafeWalkManager(context).state.value
            } catch (_: Exception) {
                SafeWalkState.Idle
            }

            if (customStatus != null) {
                views.setTextViewText(R.id.widget_status_text, customStatus)
                views.setTextColor(
                    R.id.widget_status_text,
                    if (isSuccess == true) 0xFF10B981.toInt() else 0xFFF43F5E.toInt()
                )
            } else {
                when (safeWalkState) {
                    is SafeWalkState.Active -> {
                        val mins = safeWalkState.remainingSeconds / 60
                        val secs = safeWalkState.remainingSeconds % 60
                        val timeFormatted = String.format("%02d:%02d", mins, secs)

                        views.setTextViewText(R.id.widget_status_text, "Walk Active: $timeFormatted")
                        views.setTextColor(R.id.widget_status_text, 0xFF38BDF8.toInt())

                        views.setTextViewText(R.id.txt_safewalk_btn, "Arrived")
                        views.setTextViewText(R.id.txt_safewalk_sub, "$timeFormatted left")
                        views.setInt(
                            R.id.btn_widget_safewalk,
                            "setBackgroundResource",
                            R.drawable.widget_button_active_bg
                        )
                    }
                    is SafeWalkState.Completed -> {
                        views.setTextViewText(R.id.widget_status_text, "✓ Arrived Safely")
                        views.setTextColor(R.id.widget_status_text, 0xFF10B981.toInt())

                        views.setTextViewText(R.id.txt_safewalk_btn, "Safe Walk")
                        views.setTextViewText(R.id.txt_safewalk_sub, "15m Timer")
                        views.setInt(
                            R.id.btn_widget_safewalk,
                            "setBackgroundResource",
                            R.drawable.widget_button_bg
                        )
                    }
                    is SafeWalkState.Triggered -> {
                        views.setTextViewText(R.id.widget_status_text, "🚨 Walk Expired (SOS)")
                        views.setTextColor(R.id.widget_status_text, 0xFFF43F5E.toInt())

                        views.setTextViewText(R.id.txt_safewalk_btn, "Safe Walk")
                        views.setTextViewText(R.id.txt_safewalk_sub, "Reset")
                        views.setInt(
                            R.id.btn_widget_safewalk,
                            "setBackgroundResource",
                            R.drawable.widget_button_bg
                        )
                    }
                    else -> {
                        views.setTextViewText(R.id.widget_status_text, "Standby • Ready")
                        views.setTextColor(R.id.widget_status_text, 0xFF10B981.toInt())

                        views.setTextViewText(R.id.txt_safewalk_btn, "Safe Walk")
                        views.setTextViewText(R.id.txt_safewalk_sub, "15m Timer")
                        views.setInt(
                            R.id.btn_widget_safewalk,
                            "setBackgroundResource",
                            R.drawable.widget_button_bg
                        )
                    }
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
