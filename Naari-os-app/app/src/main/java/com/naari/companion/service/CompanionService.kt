package com.naari.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.naari.companion.MainActivity
import com.naari.companion.R
import com.naari.companion.ble.BleConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground companion service maintaining the live BLE connection,
 * request notifications listener, and user-visible ongoing system notification.
 * Ref: PRD Section 7 & Master Prompt Task 13.
 */
class CompanionService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var listenerJob: Job? = null

    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Start foreground immediately within 5 seconds of service creation
        val initialNotification = buildNotification("Initializing watch companion…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        // Recover any in-flight requests on service startup
        val db = com.naari.companion.data.AppDatabase.getInstance(applicationContext)
        val ledger = com.naari.companion.ledger.DefaultRequestLedgerRepository(db.requestLedgerDao())
        serviceScope.launch(Dispatchers.IO) {
            ledger.recoverInflightOnStartup()
        }

        // Start BLE incoming requests listener
        val dispatcher = CompanionServiceDependencies.getActionDispatcher(applicationContext)
        listenerJob = dispatcher.startListening(serviceScope)

        // Observe connection state and dynamically update ongoing notification
        val bleSession = CompanionServiceDependencies.getBleSessionManager(applicationContext)
        val antiLossManager = CompanionServiceDependencies.getAntiLossManager(applicationContext)

        var previousConnectionState: BleConnectionState = BleConnectionState.Disconnected
        serviceScope.launch {
            bleSession.connectionState.collect { state ->
                val statusText = formatStatusText(state)
                val updatedNotification = buildNotification(statusText)
                notificationManager?.notify(NOTIFICATION_ID, updatedNotification)

                // Trigger Anti-Loss separation alert on unexpected link drop
                antiLossManager.onConnectionStateChanged(previousConnectionState, state)
                previousConnectionState = state
            }
        }

        // Feature 4: Low Watch Battery Guardian Warning (< 20%)
        var lastReportedBattery: Int? = null
        serviceScope.launch {
            bleSession.batteryPercent.collect { battery ->
                if (battery != null) {
                    if (battery <= 20 && (lastReportedBattery == null || lastReportedBattery!! > 20)) {
                        showLowBatteryNotification(battery)
                    }
                    lastReportedBattery = battery
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // Upgrade while the user is in the app. Android restricts starting a location
        // foreground service from the background; boot recovery retains BLE only.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && MainActivity.isVisible) {
            val fine = androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val coarse = androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                try {
                    startForeground(NOTIFICATION_ID, buildNotification("Watch companion · location requests enabled"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } catch (_: SecurityException) {
                    // If the activity stopped in the meantime, the existing BLE service stays alive.
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        listenerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                CHANNEL_ID,
                "Naari Watch Companion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays live Naari watch connection and emergency monitoring status"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(statusChannel)

            val batteryChannel = NotificationChannel(
                BATTERY_CHANNEL_ID,
                "Naari Watch Battery Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warns when the watch battery is low to ensure wrist SOS protection remains operational"
                enableVibration(true)
            }
            notificationManager?.createNotificationChannel(batteryChannel)
        }
    }

    private fun showLowBatteryNotification(battery: Int) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            BATTERY_NOTIFICATION_ID,
            openAppIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, BATTERY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Naari Watch Battery Low ($battery%)")
            .setContentText("Watch battery is below 20%. Please recharge before going out.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Watch battery has dropped to $battery%. Recharge your watch soon so your wrist SOS protection stays ready when you need it."
                )
            )
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notificationManager?.notify(BATTERY_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Naari Watch")
            .setContentText(statusText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun formatStatusText(state: BleConnectionState): String {
        return when (state) {
            BleConnectionState.Ready -> "Connected & Ready -- Safety Monitoring Active"
            BleConnectionState.Connecting -> "Connecting to Naari Watch…"
            BleConnectionState.Scanning -> "Scanning for watch…"
            BleConnectionState.Disconnected -> "Disconnected -- Looking for watch"
            BleConnectionState.BluetoothDisabled -> "Bluetooth disabled -- Safety monitoring paused"
            BleConnectionState.ConnectedServiceUnavailable -> "Connected -- Preparing services…"
        }
    }

    companion object {
        const val CHANNEL_ID = "naari_companion_status_channel"
        const val BATTERY_CHANNEL_ID = "naari_companion_battery_channel"
        const val NOTIFICATION_ID = 2001
        const val BATTERY_NOTIFICATION_ID = 2002
        const val ACTION_START_SERVICE = "com.naari.companion.START_FOREGROUND_SERVICE"
        const val ACTION_STOP_SERVICE = "com.naari.companion.STOP_FOREGROUND_SERVICE"
    }
}
