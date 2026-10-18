package com.naari.companion.ble

import java.util.UUID

/**
 * Representation of a discovered GATT service and its characteristics.
 */
data class DiscoveredGattService(
    val uuid: UUID,
    val characteristics: Set<UUID>
)

/**
 * Callbacks from the low-level GATT transport layer.
 */
interface GattTransportCallback {
    fun onConnectionStateChange(status: Int, newState: Int)
    fun onServicesDiscovered(status: Int, services: List<DiscoveredGattService>)
    fun onMtuChanged(mtu: Int, status: Int)
    fun onCharacteristicRead(serviceUuid: UUID, charUuid: UUID, value: ByteArray, status: Int)
    fun onCharacteristicWrite(serviceUuid: UUID, charUuid: UUID, status: Int)
    fun onDescriptorWrite(serviceUuid: UUID, charUuid: UUID, descriptorUuid: UUID, status: Int)
    fun onCharacteristicChanged(serviceUuid: UUID, charUuid: UUID, value: ByteArray)
}

/**
 * Transport abstraction over Android's BluetoothGatt to enable deterministic host JVM unit testing
 * without stub runtime errors.
 */
interface GattTransport {
    fun connect(address: String, autoConnect: Boolean): Boolean
    fun disconnect()
    fun close()
    fun discoverServices(): Boolean
    fun requestMtu(mtu: Int): Boolean
    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean
    fun writeCharacteristic(serviceUuid: UUID, charUuid: UUID, data: ByteArray, writeType: Int): Boolean
    fun writeDescriptor(serviceUuid: UUID, charUuid: UUID, descriptorUuid: UUID, data: ByteArray): Boolean
    fun setCharacteristicNotification(serviceUuid: UUID, charUuid: UUID, enable: Boolean): Boolean
    fun setCallback(callback: GattTransportCallback?)
}
