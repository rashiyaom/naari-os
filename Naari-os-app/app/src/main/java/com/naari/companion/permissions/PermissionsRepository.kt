package com.naari.companion.permissions

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository interface exposing live device capability and permission Flow state.
 * Ref: PRD Section 7 & Master Prompt Task 4.
 */
interface PermissionsRepository {
    val permissions: StateFlow<PermissionsSnapshot>

    fun refreshPermissions()

    fun getRequiredBluetoothPermissions(): List<String>

    fun getRequiredSafetyPermissions(): List<String>
}

class AndroidPermissionsRepository(
    private val context: Context
) : PermissionsRepository {

    private val _permissions = MutableStateFlow(inspectCurrentPermissions())
    override val permissions: StateFlow<PermissionsSnapshot> = _permissions.asStateFlow()

    override fun refreshPermissions() {
        _permissions.value = inspectCurrentPermissions()
    }

    override fun getRequiredBluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun getRequiredSafetyPermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    private fun inspectCurrentPermissions(): PermissionsSnapshot {
        // Bluetooth permissions check
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) &&
                    hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Bluetooth adapter hardware toggle check
        val btAdapterEnabled = try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }

        // SMS permission check
        val smsGranted = hasPermission(Manifest.permission.SEND_SMS)

        // Phone calls permission check
        val callGranted = hasPermission(Manifest.permission.CALL_PHONE)

        // Location permission check
        val fineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationState = when {
            fineLocation -> LocationPermissionState.Precise
            coarseLocation -> LocationPermissionState.Approximate
            else -> LocationPermissionState.Denied
        }

        // Location provider enabled check
        val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locEnabled = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locManager?.isLocationEnabled == true
            } else {
                locManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                        locManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            }
        } catch (_: Exception) {
            false
        }

        // Notifications permission check (Android 13+)
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Granted at install time on Android 12 and below
        }

        // Optional contacts check
        val contactsGranted = hasPermission(Manifest.permission.READ_CONTACTS)

        return PermissionsSnapshot(
            bluetoothGranted = btGranted,
            bluetoothAdapterEnabled = btAdapterEnabled,
            smsGranted = smsGranted,
            callGranted = callGranted,
            locationState = locationState,
            locationServiceEnabled = locEnabled,
            notificationsGranted = notifGranted,
            contactsGranted = contactsGranted
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return try {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }
}
