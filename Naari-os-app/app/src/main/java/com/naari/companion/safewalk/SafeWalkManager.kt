package com.naari.companion.safewalk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.naari.companion.MainActivity
import com.naari.companion.R
import com.naari.companion.actions.SmsExecutionResult
import com.naari.companion.actions.SmsExecutor
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.location.LocationProvider
import com.naari.companion.location.LocationResult
import com.naari.companion.protocol.WatchRequest
import com.naari.companion.protocol.WatchRequestType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.naari.companion.widget.NaariSafetyWidgetProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dead-man's journey timer manager for Safe Walk.
 *
 * If the user starts a journey (e.g. 15 minutes) and does not check in before
 * the timer reaches zero, the system automatically dispatches an emergency SOS SMS
 * with live GPS coordinates to both configured guardians.
 */
@Singleton
class SafeWalkManager(
    private val context: Context,
    private val locationProvider: LocationProvider,
    private val smsExecutor: SmsExecutor,
    private val guardiansRepository: GuardiansRepository,
    private val ledgerRepository: RequestLedgerRepository,
    private val ioDispatcher: CoroutineDispatcher,
    externalScope: CoroutineScope? = null
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        locationProvider: LocationProvider,
        smsExecutor: SmsExecutor,
        guardiansRepository: GuardiansRepository,
        ledgerRepository: RequestLedgerRepository
    ) : this(
        context = context,
        locationProvider = locationProvider,
        smsExecutor = smsExecutor,
        guardiansRepository = guardiansRepository,
        ledgerRepository = ledgerRepository,
        ioDispatcher = Dispatchers.IO,
        externalScope = null
    )

    private val scope = externalScope ?: CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    private val _state = MutableStateFlow<SafeWalkState>(SafeWalkState.Idle)
    val state: StateFlow<SafeWalkState> = _state.asStateFlow()

    private val notificationManager by lazy {
        try {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        } catch (_: Exception) {
            null
        }
    }

    init {
        createNotificationChannel()
    }

    /**
     * Starts a new Safe Walk journey for [durationMinutes].
     */
    fun startJourney(durationMinutes: Int) {
        val minutes = durationMinutes.coerceIn(1, 180)
        val totalSeconds = minutes * 60L
        val targetEpochMs = System.currentTimeMillis() + (totalSeconds * 1000L)

        timerJob?.cancel()
        _state.value = SafeWalkState.Active(
            totalMinutes = minutes,
            targetEpochMs = targetEpochMs,
            remainingSeconds = totalSeconds
        )

        updateNotification(minutes, totalSeconds)
        NaariSafetyWidgetProvider.updateAllWidgets(context)

        timerJob = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000L)
                remaining--
                val currentState = _state.value
                if (currentState is SafeWalkState.Active) {
                    _state.value = currentState.copy(remainingSeconds = remaining)
                    if (remaining % 10 == 0L || remaining <= 30) {
                        updateNotification(minutes, remaining)
                    }
                    if (remaining % 30 == 0L || remaining <= 10) {
                        NaariSafetyWidgetProvider.updateAllWidgets(context)
                    }
                } else {
                    break
                }
            }

            if (_state.value is SafeWalkState.Active) {
                triggerExpiredSos()
            }
        }
    }

    /**
     * Extends the active journey by [extraMinutes].
     */
    fun extend(extraMinutes: Int) {
        val current = _state.value as? SafeWalkState.Active ?: return
        val additionalSeconds = extraMinutes.coerceIn(1, 60) * 60L
        val newRemaining = current.remainingSeconds + additionalSeconds
        val newTotalMinutes = current.totalMinutes + extraMinutes
        val newTargetMs = current.targetEpochMs + (additionalSeconds * 1000L)

        _state.value = SafeWalkState.Active(
            totalMinutes = newTotalMinutes,
            targetEpochMs = newTargetMs,
            remainingSeconds = newRemaining
        )
        updateNotification(newTotalMinutes, newRemaining)
        NaariSafetyWidgetProvider.updateAllWidgets(context)
    }

    /**
     * User confirms they arrived safely at their destination.
     */
    fun arriveSafely() {
        timerJob?.cancel()
        timerJob = null
        cancelNotification()
        _state.value = SafeWalkState.Completed
        NaariSafetyWidgetProvider.updateAllWidgets(context)
    }

    /**
     * Cancels the journey timer without incident.
     */
    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        cancelNotification()
        _state.value = SafeWalkState.Idle
        NaariSafetyWidgetProvider.updateAllWidgets(context)
    }

    /**
     * Resets back to Idle from Completed or Triggered state.
     */
    fun reset() {
        cancel()
    }

    private suspend fun triggerExpiredSos() {
        cancelNotification()

        // 1. Acquire current location fix
        val locationResult = locationProvider.acquireLocation()
        val locationSummary = when (locationResult) {
            is LocationResult.Fresh -> "GPS: ${locationResult.fix.latitude}, ${locationResult.fix.longitude} (±${locationResult.fix.accuracyMeters?.toInt()}m)"
            is LocationResult.Cached -> "Cached GPS: ${locationResult.fix.latitude}, ${locationResult.fix.longitude}"
            else -> "GPS Unavailable"
        }

        // 2. Compose emergency SOS message
        val alertMessage = locationProvider.composeLocationAlert(
            baseMessage = "EMERGENCY: Safe Walk journey timer expired without check-in. Please verify safety immediately!",
            locationResult = locationResult
        )

        // 3. Extract coordinates
        val (lat, lon) = when (locationResult) {
            is LocationResult.Fresh -> Pair(locationResult.fix.latitude, locationResult.fix.longitude)
            is LocationResult.Cached -> Pair(locationResult.fix.latitude, locationResult.fix.longitude)
            else -> Pair(null, null)
        }

        // 4. Dispatch SMS to configured guardians
        val slot0 = guardiansRepository.slot0.first()
        val slot1 = guardiansRepository.slot1.first()

        var dispatchedCount = 0

        val recipients = listOfNotNull(slot0, slot1)
        for (guardian in recipients) {
            if (guardian.phone.isNotBlank()) {
                val requestId = System.currentTimeMillis()

                // Record in ledger
                ledgerRepository.submitRequest(
                    request = WatchRequest(
                        id = requestId,
                        type = WatchRequestType.LOCATION_SMS,
                        phone = guardian.phone,
                        message = alertMessage
                    ),
                    deviceAddress = "PHONE_SAFEWALK"
                )

                // Dispatch via SmsExecutor
                val result = smsExecutor.sendSms(
                    requestId = requestId,
                    phone = guardian.phone,
                    message = alertMessage,
                    lat = lat,
                    lon = lon
                )

                if (result is SmsExecutionResult.Success) {
                    dispatchedCount++
                }
            }
        }

        _state.value = SafeWalkState.Triggered(
            triggeredAtEpochMs = System.currentTimeMillis(),
            guardiansAlertedCount = dispatchedCount,
            locationSummary = locationSummary
        )

        showSosTriggeredNotification(dispatchedCount, locationSummary)
        NaariSafetyWidgetProvider.updateAllWidgets(context)
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Safe Walk Journey Timer",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Monitors your journey countdown and triggers emergency SOS if check-in is missed"
                    enableVibration(true)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (_: Throwable) {
        }
    }

    private fun updateNotification(totalMinutes: Int, remainingSeconds: Long) {
        try {
            val mins = remainingSeconds / 60
            val secs = remainingSeconds % 60
            val timeString = "%02d:%02d".format(mins, secs)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Safe Walk Active: $timeString remaining")
                .setContentText("Emergency SOS will alert guardians if you do not check in")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()

            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }

    private fun showSosTriggeredNotification(guardianCount: Int, location: String) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                102,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("EMERGENCY SOS: Safe Walk Expired")
                .setContentText("Emergency alert with your location was sent to $guardianCount guardian(s)")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Emergency SOS was automatically dispatched to $guardianCount guardian(s) with your last known $location."))
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .build()

            notificationManager?.notify(NOTIFICATION_ID + 1, notification)
        } catch (_: Throwable) {
        }
    }

    private fun cancelNotification() {
        try {
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val CHANNEL_ID = "naari_safewalk_channel"
        const val NOTIFICATION_ID = 4001
    }
}
