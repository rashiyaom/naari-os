package com.naari.companion.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Concrete Android implementation of [GattTransport] wrapping [BluetoothGatt].
 * Handles API 33+ and legacy SDK methods safely.
 */
class AndroidGattTransport(
    private val context: Context
) : GattTransport {

    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    private var callback: GattTransportCallback? = null

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var connectJob: kotlinx.coroutines.Job? = null
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== bluetoothGatt) return
            callback?.onConnectionStateChange(status, newState)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== bluetoothGatt) return
            val discoveredList = gatt.services.map { service ->
                DiscoveredGattService(
                    uuid = service.uuid,
                    characteristics = service.characteristics.map { it.uuid }.toSet()
                )
            }
            callback?.onServicesDiscovered(status, discoveredList)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== bluetoothGatt) return
            callback?.onMtuChanged(mtu, status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (gatt !== bluetoothGatt) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: ByteArray(0)
                callback?.onCharacteristicRead(
                    characteristic.service.uuid,
                    characteristic.uuid,
                    value,
                    status
                )
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (gatt !== bluetoothGatt) return
            callback?.onCharacteristicRead(
                characteristic.service.uuid,
                characteristic.uuid,
                value,
                status
            )
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (gatt !== bluetoothGatt) return
            callback?.onCharacteristicWrite(
                characteristic.service.uuid,
                characteristic.uuid,
                status
            )
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (gatt !== bluetoothGatt) return
            callback?.onDescriptorWrite(
                descriptor.characteristic.service.uuid,
                descriptor.characteristic.uuid,
                descriptor.uuid,
                status
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (gatt !== bluetoothGatt) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: ByteArray(0)
                callback?.onCharacteristicChanged(
                    characteristic.service.uuid,
                    characteristic.uuid,
                    value
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (gatt !== bluetoothGatt) return
            callback?.onCharacteristicChanged(
                characteristic.service.uuid,
                characteristic.uuid,
                value
            )
        }
    }

    override fun setCallback(callback: GattTransportCallback?) {
        this.callback = callback
    }

    @SuppressLint("MissingPermission")
    override fun connect(address: String, autoConnect: Boolean): Boolean {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                ?: return false
            val adapter = bluetoothManager.adapter ?: return false
            if (!adapter.isEnabled) return false

            val device: BluetoothDevice = adapter.getRemoteDevice(address)

            connectJob?.cancel()
            bluetoothGatt?.close()
            bluetoothGatt = null
            connectJob = scope.launch {
                try {
                    if (device.bondState == BluetoothDevice.BOND_NONE && !device.createBond()) {
                        callback?.onConnectionStateChange(5, BluetoothProfile.STATE_DISCONNECTED)
                        return@launch
                    }
                    val bonded = kotlinx.coroutines.withTimeoutOrNull<Boolean>(90_000) {
                        var observedBonding = false
                        val startedAt = android.os.SystemClock.elapsedRealtime()
                        while (device.bondState != BluetoothDevice.BOND_BONDED) {
                            when (device.bondState) {
                                BluetoothDevice.BOND_BONDING -> observedBonding = true
                                BluetoothDevice.BOND_NONE -> {
                                    // createBond() may return before Android publishes BOND_BONDING.
                                    // Once bonding was observed, BOND_NONE means the user rejected it.
                                    val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
                                    if (observedBonding || elapsed >= 10_000) {
                                        return@withTimeoutOrNull false
                                    }
                                }
                            }
                            kotlinx.coroutines.delay(250)
                        }
                        true
                    } ?: false
                    if(!bonded) {
                        callback?.onConnectionStateChange(5, BluetoothProfile.STATE_DISCONNECTED)
                        return@launch
                    }
                    bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    if(bluetoothGatt == null) callback?.onConnectionStateChange(133, BluetoothProfile.STATE_DISCONNECTED)
                } catch(e: SecurityException) {
                    callback?.onConnectionStateChange(5, BluetoothProfile.STATE_DISCONNECTED)
                }
            }
            return true
        } catch (e: SecurityException) {
            return false
        } catch (e: Exception) {
            return false
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        connectJob?.cancel()
        try {
            bluetoothGatt?.close()
        } catch (_: SecurityException) {
        } finally {
            bluetoothGatt = null
        }
    }

    @SuppressLint("MissingPermission")
    override fun discoverServices(): Boolean {
        val gatt = bluetoothGatt ?: return false
        return try {
            gatt.discoverServices()
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestMtu(mtu: Int): Boolean {
        val gatt = bluetoothGatt ?: return false
        return try {
            gatt.requestMtu(mtu)
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestHighConnectionPriority(): Boolean {
        val gatt = bluetoothGatt ?: return false
        return try {
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = gatt.getService(serviceUuid)?.getCharacteristic(charUuid) ?: return false
        return try {
            gatt.readCharacteristic(char)
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun writeCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        writeType: Int
    ): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = gatt.getService(serviceUuid)?.getCharacteristic(charUuid) ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeCharacteristic(char, data, writeType)
                status == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                @Suppress("DEPRECATION")
                char.writeType = writeType
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun writeDescriptor(
        serviceUuid: UUID,
        charUuid: UUID,
        descriptorUuid: UUID,
        data: ByteArray
    ): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = gatt.getService(serviceUuid)?.getCharacteristic(charUuid) ?: return false
        val desc = char.getDescriptor(descriptorUuid) ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeDescriptor(desc, data)
                status == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                desc.value = data
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(desc)
            }
        } catch (e: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun setCharacteristicNotification(
        serviceUuid: UUID,
        charUuid: UUID,
        enable: Boolean
    ): Boolean {
        val gatt = bluetoothGatt ?: return false
        val char = gatt.getService(serviceUuid)?.getCharacteristic(charUuid) ?: return false
        return try {
            gatt.setCharacteristicNotification(char, enable)
        } catch (e: SecurityException) {
            false
        }
    }
}
