package com.naari.companion.quicksettings

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.naari.companion.service.CompanionServiceDependencies
import com.naari.companion.widget.NaariSafetyWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android Quick Settings Tile for One-Tap Safe Check-in.
 *
 * Allows user to pull down Android notification shade from anywhere and dispatch
 * reassuring check-in SMS with live Google Maps coordinates to guardians in 1 tap.
 */
class SafeCheckInTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTileUi(
            state = Tile.STATE_INACTIVE,
            label = "Safe Check-in",
            subtitle = "1-Tap Reassurance SMS"
        )
    }

    override fun onClick() {
        super.onClick()

        // 1. Indicate dispatch in progress
        updateTileUi(
            state = Tile.STATE_ACTIVE,
            label = "Sending Check-in...",
            subtitle = "Acquiring GPS fix"
        )
        showToast("Acquiring GPS & sending Safe Check-in...")

        // 2. Dispatch in background
        serviceScope.launch {
            try {
                val checkInManager = CompanionServiceDependencies.getSafeCheckInManager(applicationContext)
                val result = checkInManager.sendSafeCheckIn()

                result.onSuccess { res ->
                    showToast("✓ Safe check-in sent to ${res.guardiansMessagedCount} guardian(s)")
                    updateTileUi(
                        state = Tile.STATE_ACTIVE,
                        label = "Check-in Sent ✓",
                        subtitle = "Notified ${res.guardiansMessagedCount} guardian(s)"
                    )
                    NaariSafetyWidgetProvider.updateAllWidgets(applicationContext, "✓ Check-in sent", true)

                    // Revert to normal inactive state after 4 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        updateTileUi(
                            state = Tile.STATE_INACTIVE,
                            label = "Safe Check-in",
                            subtitle = "1-Tap Reassurance SMS"
                        )
                    }, 4000)
                }.onFailure { err ->
                    showToast("Check-in failed: ${err.message}")
                    updateTileUi(
                        state = Tile.STATE_INACTIVE,
                        label = "Check-in Failed",
                        subtitle = err.message ?: "Error"
                    )
                    NaariSafetyWidgetProvider.updateAllWidgets(applicationContext, "Failed", false)

                    Handler(Looper.getMainLooper()).postDelayed({
                        updateTileUi(
                            state = Tile.STATE_INACTIVE,
                            label = "Safe Check-in",
                            subtitle = "1-Tap Reassurance SMS"
                        )
                    }, 4000)
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                updateTileUi(
                    state = Tile.STATE_INACTIVE,
                    label = "Safe Check-in",
                    subtitle = "1-Tap Reassurance SMS"
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
