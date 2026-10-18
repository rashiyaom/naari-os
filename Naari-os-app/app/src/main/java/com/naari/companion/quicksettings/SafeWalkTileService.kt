package com.naari.companion.quicksettings

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.naari.companion.safewalk.SafeWalkState
import com.naari.companion.service.CompanionServiceDependencies
import com.naari.companion.widget.NaariSafetyWidgetProvider

/**
 * Android Quick Settings Tile for "Safe Walk" Journey Timer.
 *
 * Allows user to toggle and monitor the Dead-Man's switch journey timer directly
 * from Android notification shade:
 * - When Idle: Tap starts 15-minute Safe Walk.
 * - When Active: Shows remaining minutes; tap marks "I've Arrived Safely".
 */
class SafeWalkTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileFromState()
    }

    override fun onClick() {
        super.onClick()

        val safeWalkManager = CompanionServiceDependencies.getSafeWalkManager(applicationContext)
        val currentState = safeWalkManager.state.value

        if (currentState is SafeWalkState.Active) {
            safeWalkManager.arriveSafely()
            showToast("✓ Arrived safely! Safe Walk completed.")
        } else {
            safeWalkManager.startJourney(15)
            showToast("🚶‍♀️ Safe Walk started (15 minutes). Dead-Man's switch active.")
        }

        refreshTileFromState()
        NaariSafetyWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun refreshTileFromState() {
        val safeWalkManager = try {
            CompanionServiceDependencies.getSafeWalkManager(applicationContext)
        } catch (_: Exception) {
            null
        }

        val state = safeWalkManager?.state?.value ?: SafeWalkState.Idle

        when (state) {
            is SafeWalkState.Active -> {
                val mins = (state.remainingSeconds + 59) / 60
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "Safe Walk (${mins}m)",
                    subtitle = "Tap to confirm arrival"
                )
            }
            is SafeWalkState.Completed -> {
                updateTileUi(
                    state = Tile.STATE_INACTIVE,
                    label = "Safe Walk",
                    subtitle = "✓ Arrived Safely"
                )
            }
            is SafeWalkState.Triggered -> {
                updateTileUi(
                    state = Tile.STATE_ACTIVE,
                    label = "Safe Walk (SOS)",
                    subtitle = "🚨 SOS Dispatched"
                )
            }
            else -> {
                updateTileUi(
                    state = Tile.STATE_INACTIVE,
                    label = "Safe Walk",
                    subtitle = "Tap to start 15m timer"
                )
            }
        }
    }

    private fun updateTileUi(state: Int, label: String, subtitle: String) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    private fun showToast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
