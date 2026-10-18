package com.naari.companion.ble

import com.naari.companion.protocol.PhoneReply
import com.naari.companion.protocol.PhoneReplyStatus
import com.naari.companion.protocol.ProtocolConstants
import com.naari.companion.protocol.WatchRequestType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class BleSessionManagerTest {

    private class FakeGattTransport : GattTransport {
        private var internalCallback: GattTransportCallback? = null
        val callback: GattTransportCallback? get() = internalCallback
        val callLog = mutableListOf<String>()

        var shouldMtuSucceed = true
        var negotiatedMtuValue = 512
        var autoRespondReads: ByteArray? = null
        var ignoreCallbacks = false
        var hasV2 = false
        var responseJson = "{}"
        var rejectCommands = false
        val frames = mutableListOf<ByteArray>()

        override fun connect(address: String, autoConnect: Boolean): Boolean {
            callLog.add("connect($address)")
            return true
        }

        override fun disconnect() {
            callLog.add("disconnect")
        }

        override fun close() {
            callLog.add("close")
        }

        override fun discoverServices(): Boolean {
            callLog.add("discoverServices")
            if (!ignoreCallbacks) {
                callback?.onServicesDiscovered(
                    status = 0,
                    services = listOf(
                        DiscoveredGattService(
                            uuid = ProtocolConstants.SERVICE_UUID_V1,
                            characteristics = setOf(
                                ProtocolConstants.REQUEST_CHAR_UUID_V1,
                                ProtocolConstants.REPLY_CHAR_UUID_V1
                            )
                        )
                    ) + if (hasV2) listOf(DiscoveredGattService(
                        ProtocolConstants.SERVICE_UUID_V2,
                        setOf(ProtocolConstants.CONTROL_CMD_CHAR_UUID_V2,
                            ProtocolConstants.EVENTS_CHAR_UUID_V2,
                            ProtocolConstants.DATA_TRANSFER_CHAR_UUID_V2)
                    )) else emptyList()
                )
            }
            return true
        }

        override fun requestMtu(mtu: Int): Boolean {
            callLog.add("requestMtu($mtu)")
            if (!ignoreCallbacks) {
                if (shouldMtuSucceed) {
                    callback?.onMtuChanged(negotiatedMtuValue, 0)
                } else {
                    callback?.onMtuChanged(ProtocolConstants.DEFAULT_MTU, 1) // status 1 failure
                }
            }
            return true
        }

        override fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean {
            callLog.add("readCharacteristic($charUuid)")
            if (!ignoreCallbacks) {
                val data = if (charUuid == ProtocolConstants.EVENTS_CHAR_UUID_V2) responseJson.toByteArray()
                    else autoRespondReads ?: ByteArray(0)
                callback?.onCharacteristicRead(serviceUuid, charUuid, data, 0)
            }
            return true
        }

        override fun writeCharacteristic(
            serviceUuid: UUID,
            charUuid: UUID,
            data: ByteArray,
            writeType: Int
        ): Boolean {
            frames.add(data.copyOf())
            val content = String(data, StandardCharsets.UTF_8)
            if (charUuid == ProtocolConstants.CONTROL_CMD_CHAR_UUID_V2) {
                val cmd = Regex("\"cmd\":(\\d+)").find(content)?.groupValues?.get(1) ?: "0"
                responseJson = if (rejectCommands) """{"v":2,"cmd":$cmd,"status":"rejected","err":"Storage failed"}"""
                    else """{"v":2,"cmd":$cmd,"status":"applied","applied":{"boot_id":7,"seq":1,"battery":81,"wallpaper":2,"capabilities":["wallpaper","audio_transfer"]}}"""
            }
            callLog.add("writeCharacteristic($charUuid, len=${data.size}, type=$writeType, content='$content')")
            if (!ignoreCallbacks) {
                callback?.onCharacteristicWrite(serviceUuid, charUuid, 0)
            }
            return true
        }

        override fun writeDescriptor(
            serviceUuid: UUID,
            charUuid: UUID,
            descriptorUuid: UUID,
            data: ByteArray
        ): Boolean {
            callLog.add("writeDescriptor($descriptorUuid)")
            if (!ignoreCallbacks) {
                callback?.onDescriptorWrite(serviceUuid, charUuid, descriptorUuid, 0)
            }
            return true
        }

        override fun setCharacteristicNotification(
            serviceUuid: UUID,
            charUuid: UUID,
            enable: Boolean
        ): Boolean {
            callLog.add("setCharacteristicNotification($charUuid, $enable)")
            return true
        }

        override fun setCallback(callback: GattTransportCallback?) {
            this.internalCallback = callback
        }
    }

    @Test
    fun reconnectBackoffSequence_matchesSpecification() {
        // Required sequence: 1s -> 2s -> 4s -> 8s -> 16s -> 30s cap
        assertEquals(1000L, BleBackoffStrategy.calculateDelayMs(0))
        assertEquals(2000L, BleBackoffStrategy.calculateDelayMs(1))
        assertEquals(4000L, BleBackoffStrategy.calculateDelayMs(2))
        assertEquals(8000L, BleBackoffStrategy.calculateDelayMs(3))
        assertEquals(16000L, BleBackoffStrategy.calculateDelayMs(4))
        assertEquals(30000L, BleBackoffStrategy.calculateDelayMs(5))
        assertEquals(30000L, BleBackoffStrategy.calculateDelayMs(6))
        assertEquals(30000L, BleBackoffStrategy.calculateDelayMs(10))
    }

    @Test
    fun queuedOperations_executeSequentially() = runTest {
        val transport = FakeGattTransport()
        val queue = GattOperationQueue(transport, dispatcher = StandardTestDispatcher(testScheduler))

        var op1Completed = false
        var op2Completed = false

        // Operation 1
        val res1 = queue.writeCharacteristic(
            serviceUuid = ProtocolConstants.SERVICE_UUID_V1,
            charUuid = ProtocolConstants.REPLY_CHAR_UUID_V1,
            data = "msg1".toByteArray(StandardCharsets.UTF_8),
            writeType = 2
        )
        op1Completed = res1.isSuccess

        // Operation 2
        val res2 = queue.writeCharacteristic(
            serviceUuid = ProtocolConstants.SERVICE_UUID_V1,
            charUuid = ProtocolConstants.REPLY_CHAR_UUID_V1,
            data = "msg2".toByteArray(StandardCharsets.UTF_8),
            writeType = 2
        )
        op2Completed = res2.isSuccess

        assertTrue(op1Completed)
        assertTrue(op2Completed)

        val writes = transport.callLog.filter { it.startsWith("writeCharacteristic") }
        assertEquals(2, writes.size)
        assertTrue(writes[0].contains("msg1"))
        assertTrue(writes[1].contains("msg2"))
    }

    @Test
    fun timeoutHandling_whenGattCallbackDoesNotArriveWithin5s() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGattTransport().apply {
            ignoreCallbacks = true // Never respond
        }
        val queue = GattOperationQueue(transport, dispatcher = testDispatcher, defaultTimeoutMs = 5_000L)

        val result = queue.requestMtu(512)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TimeoutException)

        assertTrue(transport.callLog.contains("close"))
        // A new transport session may execute operations after the timed out one is closed.
        transport.ignoreCallbacks = false
        val recoveryResult = queue.discoverServices()
        assertTrue(recoveryResult.isSuccess)
    }

    @Test
    fun mtuNegotiationFailure_gracefulFallbackToDefault23() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGattTransport().apply {
            shouldMtuSucceed = false // MTU fails with status 1
        }
        val queue = GattOperationQueue(transport, dispatcher = testDispatcher)
        val session = DefaultBleSessionManager(
            transport = transport,
            queue = queue,
            dispatcher = testDispatcher
        )

        session.connect("AA:BB:CC:DD:EE:FF")
        assertEquals(BleConnectionState.Connecting, session.connectionState.value)

        // Simulate connection event
        transport.callback?.onConnectionStateChange(status = 0, newState = 2) // Connected
        testScheduler.runCurrent()

        // MTU negotiation failed, but session gracefully fell back to default 23 and reached Ready
        assertEquals(ProtocolConstants.DEFAULT_MTU, session.negotiatedMtu.value)
        assertEquals(BleConnectionState.Ready, session.connectionState.value)

        session.disconnect()
    }

    @Test
    fun successfulConnectionFlow_negotiates512MtuAndEntersReady() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGattTransport().apply {
            shouldMtuSucceed = true
            negotiatedMtuValue = 512
        }
        val queue = GattOperationQueue(transport, dispatcher = testDispatcher)
        val session = DefaultBleSessionManager(
            transport = transport,
            queue = queue,
            dispatcher = testDispatcher
        )

        session.connect("11:22:33:44:55:66")
        transport.callback?.onConnectionStateChange(status = 0, newState = 2)
        testScheduler.runCurrent()

        assertEquals(512, session.negotiatedMtu.value)
        assertEquals(BleConnectionState.Ready, session.connectionState.value)

        // Verify CCCD was written to enable notifications
        assertTrue(transport.callLog.any { it.startsWith("writeDescriptor") })

        session.disconnect()
        assertEquals(BleConnectionState.Disconnected, session.connectionState.value)
    }

    @Test
    fun notificationOnE1_triggersReadAndEmitsWatchRequest() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val validRequestJson = """{"v":1,"id":77,"type":"location_sms","phone":"+15555550101","message":"Need help"}"""
        val transport = FakeGattTransport().apply {
            autoRespondReads = validRequestJson.toByteArray(StandardCharsets.UTF_8)
        }
        val queue = GattOperationQueue(transport, dispatcher = testDispatcher)
        val session = DefaultBleSessionManager(
            transport = transport,
            queue = queue,
            dispatcher = testDispatcher
        )

        session.connect("11:22:33:44:55:66")
        transport.callback?.onConnectionStateChange(status = 0, newState = 2)
        testScheduler.runCurrent()

        assertEquals(BleConnectionState.Ready, session.connectionState.value)

        // Subscribe before notification: stale requests must never replay after a reconnect.
        val pendingRequest = async(start = CoroutineStart.UNDISPATCHED) { session.incomingRequests.first() }
        // Watch notifies on e1 with 4-byte LE ID 77
        val notifIdBytes = byteArrayOf(77, 0, 0, 0)
        transport.callback?.onCharacteristicChanged(
            serviceUuid = ProtocolConstants.SERVICE_UUID_V1,
            charUuid = ProtocolConstants.REQUEST_CHAR_UUID_V1,
            value = notifIdBytes
        )
        testScheduler.runCurrent()

        // Incoming requests SharedFlow receives the parsed WatchRequest
        val request = pendingRequest.await()
        assertNotNull(request)
        assertEquals(77L, request.id)
        assertEquals(WatchRequestType.LOCATION_SMS, request.type)
        assertEquals("+15555550101", request.phone)
        assertEquals("Need help", request.message)

        session.disconnect()
    }

    @Test
    fun sendReply_writesToReplyCharWithResponse() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGattTransport()
        val queue = GattOperationQueue(transport, dispatcher = testDispatcher)
        val session = DefaultBleSessionManager(
            transport = transport,
            queue = queue,
            dispatcher = testDispatcher
        )

        session.connect("11:22:33:44:55:66")
        transport.callback?.onConnectionStateChange(status = 0, newState = 2)
        testScheduler.runCurrent()

        val reply = PhoneReply(
            id = 77,
            status = PhoneReplyStatus.SENT,
            detail = "SMS successfully transmitted",
            lat = 19.0760,
            lon = 72.8777
        )

        val result = session.sendReply(reply)
        assertTrue(result.isSuccess)

        val writeCalls = transport.callLog.filter { it.startsWith("writeCharacteristic") }
        assertTrue(writeCalls.any { it.contains(""""status":"sent"""") && it.contains(""""id":77""") })

        session.disconnect()
    }
    @Test
    fun controlRequiresAppliedAckAndPublishesRealWatchSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGattTransport().apply { hasV2 = true }
        val session = DefaultBleSessionManager(transport, GattOperationQueue(transport, dispatcher), dispatcher = dispatcher)
        session.connect("11:22:33:44:55:66")
        transport.callback?.onConnectionStateChange(0, 2)
        testScheduler.runCurrent()
        assertTrue(session.hasV2ControlService.value)
        assertEquals(81, session.batteryPercent.value)
        val accepted = session.control("""{"v":2,"cmd":321,"op":"set_wallpaper","args":{"index":2}}""")
        assertTrue(accepted.isSuccess)
        assertEquals(321, accepted.getOrThrow().commandId)
        assertEquals(2, session.snapshots.first().activeWallpaper)
        transport.rejectCommands = true
        val rejected = session.control("""{"v":2,"cmd":322,"op":"set_wallpaper","args":{"index":1}}""")
        assertTrue(rejected.isFailure)
        assertTrue(rejected.exceptionOrNull()?.message?.contains("Storage failed") == true)
        session.disconnect()
    }

    @Test
    fun smallMtuSplitsJsonIntoContiguousAcknowledgedFrames() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = FakeGattTransport().apply { negotiatedMtuValue = 23 }
        val session = DefaultBleSessionManager(transport, GattOperationQueue(transport, dispatcher), dispatcher = dispatcher)
        session.connect("11:22:33:44:55:66")
        transport.callback?.onConnectionStateChange(0, 2)
        testScheduler.runCurrent()
        transport.frames.clear()
        val result = session.sendReply(PhoneReply(id = 77, status = PhoneReplyStatus.SENT, detail = "Sent safely"))
        assertTrue(result.isSuccess)
        assertTrue(transport.frames.size > 1)
        val assembled = mutableListOf<Byte>()
        for ((index, frame) in transport.frames.withIndex()) {
            assertTrue(frame.size <= 20)
            assertEquals(0x7e.toByte(), frame[0])
            assertEquals(assembled.size, (frame[2].toInt() and 255) or ((frame[3].toInt() and 255) shl 8))
            assertEquals(if(index == transport.frames.lastIndex) 1 else 0, frame[4].toInt())
            assembled.addAll(frame.drop(5))
        }
        assertTrue(assembled.toByteArray().toString(Charsets.UTF_8).contains("\"id\":77"))
        session.disconnect()
    }

}
