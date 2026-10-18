package com.naari.companion.antiloss

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.naari.companion.MainActivity
import com.naari.companion.R
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.location.LocationProvider
import com.naari.companion.location.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data model for the last known location where the watch separated/disconnected.
 */
data class DisconnectEvent(
    val timestampMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val locationSummary: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("h:mm a, d MMM", Locale.getDefault()).format(Date(timestampMs))

    val mapsUrl: String?
        get() = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else null
}

/**
 * Manager handling Anti-Loss Separation alerts when the watch link is lost unexpectedly.
 */
@Singleton
class AntiLossManager(
    private val context: Context,
    private val locationProvider: LocationProvider,
    externalScope: CoroutineScope?
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        locationProvider: LocationProvider
    ) : this(context, locationProvider, null)

    private val prefs by lazy {
        try {
            context.getSharedPreferences("naari_antiloss_prefs", Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    private val scope = externalScope ?: CoroutineScope(Dispatchers.Default)

    private val _isEnabled = MutableStateFlow(prefs?.getBoolean(KEY_ENABLED, true) ?: true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _lastDisconnectEvent = MutableStateFlow<DisconnectEvent?>(loadLastEvent())
    val lastDisconnectEvent: StateFlow<DisconnectEvent?> = _lastDisconnectEvent.asStateFlow()

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

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    fun onConnectionStateChanged(previous: BleConnectionState, current: BleConnectionState) {
        if (!_isEnabled.value) return

        // Trigger separation alert only when link was actively Ready and dropped unexpectedly
        if (previous == BleConnectionState.Ready && current != BleConnectionState.Ready) {
            scope.launch {
                handleSeparation()
            }
        }
    }

    fun clearAlert() {
        notificationManager?.cancel(NOTIFICATION_ID)
        _lastDisconnectEvent.value = null
        prefs?.edit()
            ?.remove(KEY_LAST_TIME)
            ?.remove(KEY_LAST_LAT)
            ?.remove(KEY_LAST_LON)
            ?.remove(KEY_LAST_ACCURACY)
            ?.apply()
    }

    private suspend fun handleSeparation() {
        val now = System.currentTimeMillis()
        val locationResult = locationProvider.acquireLocation()

        val (lat, lon, accuracy) = when (locationResult) {
            is LocationResult.Fresh -> Triple(locationResult.fix.latitude, locationResult.fix.longitude, locationResult.fix.accuracyMeters)
            is LocationResult.Cached -> Triple(locationResult.fix.latitude, locationResult.fix.longitude, locationResult.fix.accuracyMeters)
            else -> Triple(null, null, null)
        }

        val summary = if (lat != null && lon != null) {
            "Lat: %.4f, Lon: %.4f".format(lat, lon)
        } else {
            "Location unavailable at disconnect"
        }

        val event = DisconnectEvent(
            timestampMs = now,
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            locationSummary = summary
        )

        _lastDisconnectEvent.value = event
        saveEvent(event)
        showSeparationNotification(event)
    }

    private fun showSeparationNotification(event: DisconnectEvent) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val text = if (event.latitude != null && event.longitude != null) {
                "Watch disconnected near ${event.locationSummary} at ${event.formattedTime}. Tap to locate."
            } else {
                "Watch connection lost at ${event.formattedTime}. Did you leave your watch behind?"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Naari Watch Disconnected")
                .setContentText("Did you leave your watch behind?")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build()

            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Notifications are optional in unit tests
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Watch Anti-Loss & Separation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts you immediately when your watch disconnects unexpectedly"
                    enableVibration(true)
                }
                notificationManager?.createNotificationChannel(channel)
            }
        } catch (_: Throwable) {
        }
    }

    private fun saveEvent(event: DisconnectEvent) {
        prefs?.edit()
            ?.putLong(KEY_LAST_TIME, event.timestampMs)
            ?.apply {
                if (event.latitude != null) putFloat(KEY_LAST_LAT, event.latitude.toFloat())
                if (event.longitude != null) putFloat(KEY_LAST_LON, event.longitude.toFloat())
                if (event.accuracyMeters != null) putFloat(KEY_LAST_ACCURACY, event.accuracyMeters)
            }
            ?.apply()
    }

    private fun loadLastEvent(): DisconnectEvent? {
        val currentPrefs = prefs ?: return null
        val time = currentPrefs.getLong(KEY_LAST_TIME, 0L)
        if (time == 0L) return null

        val lat = if (currentPrefs.contains(KEY_LAST_LAT)) currentPrefs.getFloat(KEY_LAST_LAT, 0f).toDouble() else null
        val lon = if (currentPrefs.contains(KEY_LAST_LON)) currentPrefs.getFloat(KEY_LAST_LON, 0f).toDouble() else null
        val acc = if (currentPrefs.contains(KEY_LAST_ACCURACY)) currentPrefs.getFloat(KEY_LAST_ACCURACY, 0f) else null

        val summary = if (lat != null && lon != null) "Lat: %.4f, Lon: %.4f".format(lat, lon) else "Location unavailable"
        return DisconnectEvent(time, lat, lon, acc, summary)
    }

    companion object {
        const val CHANNEL_ID = "naari_antiloss_channel"
        const val NOTIFICATION_ID = 4002
        private const val KEY_ENABLED = "antiloss_enabled"
        private const val KEY_LAST_TIME = "antiloss_last_time"
        private const val KEY_LAST_LAT = "antiloss_last_lat"
        private const val KEY_LAST_LON = "antiloss_last_lon"
        private const val KEY_LAST_ACCURACY = "antiloss_last_accuracy"
    }
}
