package com.naari.companion.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeoutException

/**
 * Serialized GATT operation queue.
 *
 * Android GATT operations must be executed strictly one at a time. Concurrency causes
 * dropped operations or status 133 failures. This queue guarantees sequential execution
 * and enforces a 5-second timeout on every operation.
 */
class GattOperationQueue(
    private val transport: GattTransport,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val defaultTimeoutMs: Long = 5_000L
) : GattTransportCallback {

    private val executionMutex = Mutex()

    @Volatile
    private var pendingOperation: PendingGattOperation<*>? = null

    // Callbacks for asynchronous incoming notifications
    var onNotificationReceived: ((serviceUuid: UUID, charUuid: UUID, data: ByteArray) -> Unit)? = null
    var onConnectionStateChanged: ((status: Int, newState: Int) -> Unit)? = null

    init {
        transport.setCallback(this)
    }

    private data class PendingGattOperation<T>(
        val name: String,
        val deferred: CompletableDeferred<T>,
        val matcher: (CallbackEvent) -> T?
    )

    private sealed class CallbackEvent {
        data class Mtu(val mtu: Int, val status: Int) : CallbackEvent()
        data class Services(val services: List<DiscoveredGattService>, val status: Int) : CallbackEvent()
        data class Read(val serviceUuid: UUID, val charUuid: UUID, val value: ByteArray, val status: Int) : CallbackEvent()
        data class Write(val serviceUuid: UUID, val charUuid: UUID, val status: Int) : CallbackEvent()
        data class DescWrite(val serviceUuid: UUID, val charUuid: UUID, val descriptorUuid: UUID, val status: Int) : CallbackEvent()
    }

    /**
     * Executes an operation with serialization and timeout.
     */
    private suspend fun <T> executeOperation(
        operationName: String,
        timeoutMs: Long = defaultTimeoutMs,
        matcher: (CallbackEvent) -> T?,
        initiate: (GattTransport) -> Boolean
    ): Result<T> = withContext(dispatcher) {
        executionMutex.withLock {
            val deferred = CompletableDeferred<T>()
            val pending = PendingGattOperation(operationName, deferred, matcher)
            pendingOperation = pending

            val initiated = try {
                initiate(transport)
            } catch (e: kotlinx.coroutines.CancellationException) {
                pendingOperation = null
                throw e
            } catch (e: Exception) {
                pendingOperation = null
                return@withContext Result.failure(e)
            }

            if (!initiated) {
                pendingOperation = null
                return@withContext Result.failure(IOException("Failed to initiate GATT operation: $operationName"))
            }

            val result = try {
                withTimeoutOrNull(timeoutMs) {
                    deferred.await()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                pendingOperation = null
                throw e
            } catch (e: Exception) {
                pendingOperation = null
                return@withContext Result.failure(e)
            }

            pendingOperation = null

            if (result != null) {
                Result.success(result)
            } else {
                transport.close() // A late callback must never satisfy the next operation.
                onConnectionStateChanged?.invoke(8, 0)
                Result.failure(TimeoutException("GATT operation '$operationName' timed out after ${timeoutMs}ms"))
            }
        }
    }

    /**
     * Requests MTU negotiation (e.g. 512).
     */
    suspend fun requestMtu(mtu: Int, timeoutMs: Long = defaultTimeoutMs): Result<Int> {
        return executeOperation(
            operationName = "RequestMtu($mtu)",
            timeoutMs = timeoutMs,
            matcher = { event ->
                if (event is CallbackEvent.Mtu) {
                    if (event.status == 0) event.mtu else null
                } else null
            },
            initiate = { it.requestMtu(mtu) }
        )
    }

    /**
     * Discovers GATT services.
     */
    suspend fun discoverServices(timeoutMs: Long = defaultTimeoutMs): Result<List<DiscoveredGattService>> {
        return executeOperation(
            operationName = "DiscoverServices",
            timeoutMs = timeoutMs,
            matcher = { event ->
                if (event is CallbackEvent.Services) {
                    if (event.status == 0) event.services else null
                } else null
            },
            initiate = { it.discoverServices() }
        )
    }

    /**
     * Reads a characteristic value.
     */
    suspend fun readCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        timeoutMs: Long = defaultTimeoutMs
    ): Result<ByteArray> {
        return executeOperation(
            operationName = "ReadCharacteristic($charUuid)",
            timeoutMs = timeoutMs,
            matcher = { event ->
                if (event is CallbackEvent.Read &&
                    event.serviceUuid == serviceUuid &&
                    event.charUuid == charUuid
                ) {
                    if (event.status == 0) event.value else null
                } else null
            },
            initiate = { it.readCharacteristic(serviceUuid, charUuid) }
        )
    }

    /**
     * Writes a characteristic with response.
     */
    suspend fun writeCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        writeType: Int,
        timeoutMs: Long = defaultTimeoutMs
    ): Result<Unit> {
        return executeOperation(
            operationName = "WriteCharacteristic($charUuid, len=${data.size})",
            timeoutMs = timeoutMs,
            matcher = { event ->
                if (event is CallbackEvent.Write &&
                    event.serviceUuid == serviceUuid &&
                    event.charUuid == charUuid
                ) {
                    if (event.status == 0) Unit else null
                } else null
            },
            initiate = { it.writeCharacteristic(serviceUuid, charUuid, data, writeType) }
        )
    }

    /**
     * Writes a descriptor value (used to enable notifications on CCCD 0x2902).
     */
    suspend fun writeDescriptor(
        serviceUuid: UUID,
        charUuid: UUID,
        descriptorUuid: UUID,
        data: ByteArray,
        timeoutMs: Long = defaultTimeoutMs
    ): Result<Unit> {
        return executeOperation(
            operationName = "WriteDescriptor($descriptorUuid)",
            timeoutMs = timeoutMs,
            matcher = { event ->
                if (event is CallbackEvent.DescWrite &&
                    event.serviceUuid == serviceUuid &&
                    event.charUuid == charUuid &&
                    event.descriptorUuid == descriptorUuid
                ) {
                    if (event.status == 0) Unit else null
                } else null
            },
            initiate = { it.writeDescriptor(serviceUuid, charUuid, descriptorUuid, data) }
        )
    }

    /**
     * Cancels any pending operation when disconnected.
     */
    fun cancelPending(reason: String) {
        val pending = pendingOperation
        if (pending != null) {
            pending.deferred.completeExceptionally(IOException(reason))
            pendingOperation = null
        }
    }

    // --- GattTransportCallback implementation ---

    override fun onConnectionStateChange(status: Int, newState: Int) {
        if (newState == 0) { // Disconnected
            cancelPending("GATT disconnected with status $status")
        }
        onConnectionStateChanged?.invoke(status, newState)
    }

    override fun onServicesDiscovered(status: Int, services: List<DiscoveredGattService>) {
        dispatchCallbackEvent(CallbackEvent.Services(services, status))
    }

    override fun onMtuChanged(mtu: Int, status: Int) {
        dispatchCallbackEvent(CallbackEvent.Mtu(mtu, status))
    }

    override fun onCharacteristicRead(serviceUuid: UUID, charUuid: UUID, value: ByteArray, status: Int) {
        dispatchCallbackEvent(CallbackEvent.Read(serviceUuid, charUuid, value, status))
    }

    override fun onCharacteristicWrite(serviceUuid: UUID, charUuid: UUID, status: Int) {
        dispatchCallbackEvent(CallbackEvent.Write(serviceUuid, charUuid, status))
    }

    override fun onDescriptorWrite(serviceUuid: UUID, charUuid: UUID, descriptorUuid: UUID, status: Int) {
        dispatchCallbackEvent(CallbackEvent.DescWrite(serviceUuid, charUuid, descriptorUuid, status))
    }

    override fun onCharacteristicChanged(serviceUuid: UUID, charUuid: UUID, value: ByteArray) {
        onNotificationReceived?.invoke(serviceUuid, charUuid, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun dispatchCallbackEvent(event: CallbackEvent) {
        val pending = pendingOperation as? PendingGattOperation<Any> ?: return
        val status = when (event) {
            is CallbackEvent.Mtu -> event.status
            is CallbackEvent.Services -> event.status
            is CallbackEvent.Read -> event.status
            is CallbackEvent.Write -> event.status
            is CallbackEvent.DescWrite -> event.status
        }
        if (status != 0) {
            pending.deferred.completeExceptionally(
                IOException("GATT operation '${pending.name}' failed with status $status")
            )
            return
        }
        val matched = pending.matcher(event)
        if (matched != null) {
            pending.deferred.complete(matched)
        }
    }
}
