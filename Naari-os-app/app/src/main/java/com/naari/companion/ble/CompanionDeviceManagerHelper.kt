package com.naari.companion.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.naari.companion.protocol.ProtocolConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Callback for CompanionDeviceManager association flow.
 */
interface AssociationCallback {
    /** Called when system chooser dialog is ready to be launched via ActivityResultLauncher */
    fun onIntentSenderReady(intentSender: IntentSender)

    /** Called if association fails or is cancelled */
    fun onFailure(error: String)
}

/**
 * Helper class for Android CompanionDeviceManager (CDM).
 * Facilitates watch device association, background execution privilege binding,
 * and device presence observation on Android 12+.
 */
@Singleton
class CompanionDeviceManagerHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val companionManager: CompanionDeviceManager? by lazy {
        context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
    }

    /**
     * Checks if CompanionDeviceManager service is available on this Android device.
     */
    fun isAvailable(): Boolean = companionManager != null

    /**
     * Checks if the device address is already associated via CompanionDeviceManager.
     */
    fun isAssociated(deviceAddress: String): Boolean {
        val manager = companionManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.myAssociations.any { it.deviceMacAddress?.toString().equals(deviceAddress, ignoreCase = true) }
            } else {
                @Suppress("DEPRECATION")
                manager.associations.any { it.equals(deviceAddress, ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Builds an AssociationRequest configured to scan for Naari Watch BLE devices
     * matching either the device name prefix or the primary service UUID (d973f2e0-b19e-11ee-a506-0242ac120002).
     */
    fun buildAssociationRequest(namePrefix: String = ProtocolConstants.DEVICE_NAME_PREFIX): AssociationRequest {
        val pattern = Pattern.compile(".*$namePrefix.*", Pattern.CASE_INSENSITIVE)
        val nameFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(pattern)
            .build()

        val leScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(ProtocolConstants.SERVICE_UUID_V1))
            .build()

        val leFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(leScanFilter)
            .build()

        return AssociationRequest.Builder()
            .addDeviceFilter(nameFilter)
            .addDeviceFilter(leFilter)
            .setSingleDevice(true)
            .build()
    }

    /**
     * Requests association with a Naari Watch using Android's system CompanionDeviceManager.
     */
    @SuppressLint("MissingPermission")
    fun associate(
        callback: AssociationCallback,
        request: AssociationRequest = buildAssociationRequest()
    ) {
        val manager = companionManager
        if (manager == null) {
            callback.onFailure("CompanionDeviceManager is not supported on this device")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.associate(
                    request,
                    context.mainExecutor,
                    object : CompanionDeviceManager.Callback() {
                        override fun onAssociationPending(intentSender: IntentSender) {
                            callback.onIntentSenderReady(intentSender)
                        }

                        override fun onAssociationCreated(associationInfo: android.companion.AssociationInfo) {
                            // Automatically handled if system completes directly
                        }

                        override fun onFailure(error: CharSequence?) {
                            callback.onFailure(error?.toString() ?: "Device association failed")
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                manager.associate(
                    request,
                    object : CompanionDeviceManager.Callback() {
                        @Deprecated("Deprecated in Java")
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            callback.onIntentSenderReady(chooserLauncher)
                        }

                        override fun onFailure(error: CharSequence?) {
                            callback.onFailure(error?.toString() ?: "Device association failed")
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            }
        } catch (e: Exception) {
            callback.onFailure(e.message ?: "Failed to start device association")
        }
    }

    /**
     * Extracts the selected [BluetoothDevice] from the CDM system picker result Intent.
     */
    @Suppress("DEPRECATION")
    fun extractDeviceFromResult(intent: Intent?): BluetoothDevice? {
        if (intent == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association = intent.getParcelableExtra(
                    CompanionDeviceManager.EXTRA_ASSOCIATION,
                    android.companion.AssociationInfo::class.java
                )
                val address = association?.deviceMacAddress?.toString()
                if (address != null) {
                    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    manager?.adapter?.getRemoteDevice(address)?.let { return it }
                }
                val bluetoothDevice = intent.getParcelableExtra(
                    CompanionDeviceManager.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
                if (bluetoothDevice != null) return bluetoothDevice
                val scanResult = intent.getParcelableExtra(
                    CompanionDeviceManager.EXTRA_DEVICE,
                    android.bluetooth.le.ScanResult::class.java
                )
                scanResult?.device
            } else {
                val parcelable = intent.getParcelableExtra<android.os.Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
                when (parcelable) {
                    is BluetoothDevice -> parcelable
                    is android.bluetooth.le.ScanResult -> parcelable.device
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a device address is bonded in Android's BluetoothAdapter.
     */
    @SuppressLint("MissingPermission")
    fun isBonded(deviceAddress: String): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val device = bluetoothManager?.adapter?.getRemoteDevice(deviceAddress) ?: return false
            device.bondState == BluetoothDevice.BOND_BONDED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Initiates pairing/bonding for a given Bluetooth device address if not already bonded.
     */
    @SuppressLint("MissingPermission")
    fun createBond(deviceAddress: String): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val device = bluetoothManager?.adapter?.getRemoteDevice(deviceAddress) ?: return false
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                true
            } else {
                device.createBond()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts presence observation on Android 12+ (API 31+).
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun startObservingPresence(deviceAddress: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = companionManager ?: return false
            return try {
                manager.startObservingDevicePresence(deviceAddress)
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Stops presence observation on Android 12+ (API 31+).
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun stopObservingPresence(deviceAddress: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = companionManager ?: return false
            return try {
                manager.stopObservingDevicePresence(deviceAddress)
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Disassociates a previously paired device.
     */
    fun disassociate(deviceAddress: String): Boolean {
        val manager = companionManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val association = manager.myAssociations.firstOrNull {
                    it.deviceMacAddress?.toString().equals(deviceAddress, ignoreCase = true)
                }
                if (association != null) {
                    manager.disassociate(association.id)
                    true
                } else false
            } else {
                @Suppress("DEPRECATION")
                manager.disassociate(deviceAddress)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
