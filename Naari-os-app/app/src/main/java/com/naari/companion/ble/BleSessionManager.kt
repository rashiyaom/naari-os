package com.naari.companion.ble

import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

interface BleSessionManager {
    val connectionState: StateFlow<BleConnectionState>
    val incomingRequests: SharedFlow<WatchRequest>
    val negotiatedMtu: StateFlow<Int>
    val lastHelloTimestampMs: StateFlow<Long?> get() = MutableStateFlow(null)
    val connectionStep: StateFlow<BleConnectionStep> get() = MutableStateFlow(BleConnectionStep.Disconnected)
    val hasRequiredGattService: StateFlow<Boolean> get() = MutableStateFlow(false)
    val batteryPercent: StateFlow<Int?> get() = MutableStateFlow(null)
    val hasV2ControlService: StateFlow<Boolean> get() = MutableStateFlow(false)
    val snapshots: SharedFlow<WatchStatusSnapshot> get() = MutableSharedFlow()
    val rawSnapshots: SharedFlow<ControlResponse> get() = MutableSharedFlow()
    fun connect(deviceAddress: String)
    suspend fun sendReply(reply: PhoneReply): Result<Unit>
    suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit>
    suspend fun sendControlCommand(commandJson: String): Result<Unit> = Result.failure(UnsupportedOperationException("Watch sync service unavailable"))
    suspend fun control(commandJson: String): Result<ControlResponse> = Result.failure(UnsupportedOperationException("Watch sync service unavailable"))
    suspend fun readAudioChunk(): Result<ByteArray> = Result.failure(UnsupportedOperationException("Watch audio service unavailable"))
    fun disconnect()
}

class DefaultBleSessionManager(
    private val transport: GattTransport,
    private val queue: GattOperationQueue,
    private val permissionsRepository: PermissionsRepository? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val helloIntervalMs: Long = ProtocolConstants.HELLO_INTERVAL_MS
) : BleSessionManager {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _connectionState = MutableStateFlow(BleConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()
    private val _incomingRequests = MutableSharedFlow<WatchRequest>(extraBufferCapacity = 64)
    override val incomingRequests = _incomingRequests.asSharedFlow()
    private val _negotiatedMtu = MutableStateFlow(23)
    override val negotiatedMtu = _negotiatedMtu.asStateFlow()
    private val _lastHelloTimestampMs = MutableStateFlow<Long?>(null)
    override val lastHelloTimestampMs = _lastHelloTimestampMs.asStateFlow()
    private val _connectionStep = MutableStateFlow(BleConnectionStep.Disconnected)
    override val connectionStep = _connectionStep.asStateFlow()
    private val _hasRequiredGattService = MutableStateFlow(false)
    override val hasRequiredGattService = _hasRequiredGattService.asStateFlow()
    private val _batteryPercent = MutableStateFlow<Int?>(null)
    override val batteryPercent = _batteryPercent.asStateFlow()
    private val _hasV2ControlService = MutableStateFlow(false)
    override val hasV2ControlService = _hasV2ControlService.asStateFlow()
    private val _snapshots = MutableSharedFlow<WatchStatusSnapshot>(replay = 1)
    override val snapshots = _snapshots.asSharedFlow()
    private val _rawSnapshots = MutableSharedFlow<ControlResponse>(replay = 1)
    override val rawSnapshots = _rawSnapshots.asSharedFlow()
    private val writeMutex = Mutex()
    private val controlMutex = Mutex()
    private val requestReadMutex = Mutex()
    private val commandIds = AtomicInteger(10000)
    private var fragmentId = 0
    private var address: String? = null
    private var intentional = false
    private var attempt = 0
    private var setupJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    init {
        queue.onConnectionStateChanged = { status, state ->
            if(state == 2 && status == 0) {
                setupJob?.cancel()
                setupJob = scope.launch {
                    try { setup() } catch(e: CancellationException) { throw e }
                    catch(e: Exception) { failConnection() }
                }
            } else if(state == 0) {
                setupJob?.cancel(); heartbeatJob?.cancel()
                resetState()
                if(status == 5) intentional = true // Repeated rejected pairing must not loop system dialogs.
                if(!intentional) reconnect()
            }
        }
        queue.onNotificationReceived = { service, characteristic, _ ->
            if(service == ProtocolConstants.SERVICE_UUID_V1 && characteristic == ProtocolConstants.REQUEST_CHAR_UUID_V1)
                scope.launch { runCatching { readPendingRequest() } }
            // Control acknowledgements are read by the serialized control transaction.
            // Polling snapshots reconciles missed notifications and watch-local changes.
        }
    }
    override fun connect(deviceAddress: String) {
        if(address == deviceAddress && _connectionState.value != BleConnectionState.Disconnected) return
        setupJob?.cancel(); heartbeatJob?.cancel(); reconnectJob?.cancel()
        queue.cancelPending("Starting new connection")
        address = deviceAddress; intentional = false; attempt = 0
        _connectionState.value = BleConnectionState.Connecting
        _connectionStep.value = BleConnectionStep.Connecting
        if(!transport.connect(deviceAddress, false)) failConnection()
    }
    private fun resetState() {
        _connectionState.value = BleConnectionState.Disconnected
        _connectionStep.value = BleConnectionStep.Disconnected
        _hasRequiredGattService.value = false; _hasV2ControlService.value = false
        _lastHelloTimestampMs.value = null; _batteryPercent.value = null
        _negotiatedMtu.value = 23
    }
    private fun failConnection() {
        transport.disconnect(); transport.close(); queue.cancelPending("Connection setup failed")
        resetState(); if(!intentional) reconnect()
    }
    override fun disconnect() {
        intentional = true; setupJob?.cancel(); heartbeatJob?.cancel(); reconnectJob?.cancel()
        transport.disconnect(); transport.close(); queue.cancelPending("Disconnected")
        resetState()
    }
    private fun reconnect() {
        reconnectJob?.cancel()
        val target = address ?: return
        reconnectJob = scope.launch {
            delay(BleBackoffStrategy.calculateDelayMs(attempt++))
            if(!intentional) {
                _connectionState.value = BleConnectionState.Connecting
                _connectionStep.value = BleConnectionStep.Connecting
                if(!transport.connect(target, false)) failConnection()
            }
        }
    }
    private suspend fun subscribe(service: java.util.UUID, characteristic: java.util.UUID) {
        check(transport.setCharacteristicNotification(service, characteristic, true)) { "Cannot subscribe to watch" }
        queue.writeDescriptor(service, characteristic, ProtocolConstants.CCCD_DESCRIPTOR_UUID, byteArrayOf(1,0), 15000).getOrThrow()
    }
    private suspend fun setup() {
        _connectionState.value = BleConnectionState.ConnectedServiceUnavailable
        _connectionStep.value = BleConnectionStep.ConnectedBonded
        // Prefer a short connection interval for status reconciliation and
        // recording transfer. Android may decline this without failing setup.
        transport.requestHighConnectionPriority()
        val mtuResult = queue.requestMtu(512, 15000)
        if (mtuResult.exceptionOrNull() is java.util.concurrent.TimeoutException) mtuResult.getOrThrow()
        _negotiatedMtu.value = mtuResult.getOrDefault(23)
        val services = queue.discoverServices(15000).getOrThrow()
        val v1 = services.find { it.uuid == ProtocolConstants.SERVICE_UUID_V1 }
        check(v1?.characteristics?.containsAll(setOf(ProtocolConstants.REQUEST_CHAR_UUID_V1, ProtocolConstants.REPLY_CHAR_UUID_V1)) == true) { "Naari service missing" }
        _hasRequiredGattService.value = true
        _connectionStep.value = BleConnectionStep.ServiceDiscovered
        val v2 = services.find { it.uuid == ProtocolConstants.SERVICE_UUID_V2 }
        _hasV2ControlService.value = v2?.characteristics?.containsAll(setOf(ProtocolConstants.CONTROL_CMD_CHAR_UUID_V2, ProtocolConstants.EVENTS_CHAR_UUID_V2, ProtocolConstants.DATA_TRANSFER_CHAR_UUID_V2)) == true
        subscribe(ProtocolConstants.SERVICE_UUID_V1, ProtocolConstants.REQUEST_CHAR_UUID_V1)
        if(_hasV2ControlService.value) subscribe(ProtocolConstants.SERVICE_UUID_V2, ProtocolConstants.EVENTS_CHAR_UUID_V2)
        sendHello().getOrThrow() // Initial hello is immediate, and Ready requires success.
        _connectionState.value = BleConnectionState.Ready
        attempt = 0
        if(_hasV2ControlService.value) querySnapshot()
        readPendingRequest() // Reconcile a request queued before notifications were enabled.
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var lastHello = System.currentTimeMillis()
            while(isActive && _connectionState.value == BleConnectionState.Ready) {
                delay(2000)
                try {
                    if(System.currentTimeMillis() - lastHello >= helloIntervalMs) {
                        sendHello().getOrThrow(); lastHello = System.currentTimeMillis()
                    }
                    if(_hasV2ControlService.value) querySnapshot()
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { failConnection(); break }
            }
        }
    }
    private suspend fun sendJson(service: java.util.UUID, characteristic: java.util.UUID, json: String): Result<Unit> = writeMutex.withLock {
        try {
            val bytes = json.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 480) { "BLE JSON exceeds firmware limit" }
            val capacity = _negotiatedMtu.value - 3
            if(bytes.size <= capacity) queue.writeCharacteristic(service, characteristic, bytes, 2, 15000).getOrThrow()
            else {
                require(capacity > 5)
                val transfer = (++fragmentId).toByte(); var offset = 0
                while(offset < bytes.size) {
                    val length = minOf(capacity - 5, bytes.size - offset)
                    val header = byteArrayOf(0x7e, transfer, offset.toByte(), (offset ushr 8).toByte(), if(offset + length == bytes.size) 1 else 0)
                    queue.writeCharacteristic(service, characteristic, header + bytes.copyOfRange(offset, offset + length), 2, 15000).getOrThrow()
                    offset += length
                }
            }
            Result.success(Unit)
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { Result.failure(e) }
    }
    private suspend fun sendHello(): Result<Unit> {
        val permissions = permissionsRepository?.permissions?.value
        val result = sendJson(ProtocolConstants.SERVICE_UUID_V1, ProtocolConstants.REPLY_CHAR_UUID_V1,
            ProtocolV1Codec.buildHelloJson(permissions?.canSendSms ?: false, permissions?.canPlaceCall ?: false, permissions?.canAccessLocation ?: false))
        if(result.isSuccess) _lastHelloTimestampMs.value = System.currentTimeMillis()
        return result
    }
    override suspend fun sendReply(reply: PhoneReply): Result<Unit> {
        if(_connectionState.value != BleConnectionState.Ready) return Result.failure(IOException("Watch not ready"))
        return sendJson(ProtocolConstants.SERVICE_UUID_V1, ProtocolConstants.REPLY_CHAR_UUID_V1, ProtocolV1Codec.buildReplyJson(reply))
    }
    override suspend fun syncProfile(profile: PhoneProfileMessage): Result<Unit> {
        if(_connectionState.value != BleConnectionState.Ready) return Result.failure(IOException("Watch not ready"))
        val result = sendJson(ProtocolConstants.SERVICE_UUID_V1, ProtocolConstants.REPLY_CHAR_UUID_V1, ProtocolV1Codec.buildProfileJson(profile))
        if(result.isFailure) return result
        if(!_hasV2ControlService.value) return Result.failure(IOException("Update watch firmware to confirm guardian storage"))
        return try {
            val ack = control(ProtocolV2Codec.buildGetProfileCommand(commandIds.incrementAndGet())).getOrThrow().appliedData
            for((key, value) in mapOf("phone0" to profile.phone0, "phone1" to profile.phone1, "name0" to profile.name0.ifBlank { "Guardian" }, "name1" to profile.name1.ifBlank { "Guardian" })) check(ack[key] == value) { "Guardian read-back mismatch" }
            Result.success(Unit)
        } catch(e: CancellationException) { throw e } catch(e: Exception) { Result.failure(e) }
    }
    override suspend fun control(commandJson: String): Result<ControlResponse> = controlMutex.withLock {
        try {
            check(_connectionState.value == BleConnectionState.Ready && _hasV2ControlService.value) { "Watch sync service unavailable" }
            val expected = Regex(""""cmd"\s*:\s*(\d+)""").find(commandJson)?.groupValues?.get(1)?.toInt() ?: error("Missing command id")
            sendJson(ProtocolConstants.SERVICE_UUID_V2, ProtocolConstants.CONTROL_CMD_CHAR_UUID_V2, commandJson).getOrThrow()
            val response = withTimeout(10000) {
                while(true) {
                    val bytes = queue.readCharacteristic(ProtocolConstants.SERVICE_UUID_V2, ProtocolConstants.EVENTS_CHAR_UUID_V2).getOrThrow()
                    val json = bytes.toString(Charsets.UTF_8)
                    if(json != "{}") {
                        val parsed = ProtocolV2Codec.parseControlResponseJson(json)
                        if(parsed.commandId == expected && parsed.status != ControlStatus.Accepted) return@withTimeout parsed
                    }
                    delay(40)
                }
                @Suppress("UNREACHABLE_CODE") error("No response")
            }
            check(response.status == ControlStatus.Applied) { response.error ?: "Watch rejected command" }
            if(response.appliedData.containsKey("boot_id")) {
                val snapshot = ProtocolV2Codec.parseStatusSnapshot(response.appliedData)
                _batteryPercent.value = snapshot.batteryPercent
                _snapshots.emit(snapshot); _rawSnapshots.emit(response)
            }
            Result.success(response)
        } catch(e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Result.failure(IOException("Watch acknowledgement timed out", e))
        } catch(e: CancellationException) { throw e } catch(e: Exception) { Result.failure(e) }
    }
    override suspend fun sendControlCommand(commandJson: String): Result<Unit> = control(commandJson).map { Unit }
    override suspend fun readAudioChunk(): Result<ByteArray> = queue.readCharacteristic(ProtocolConstants.SERVICE_UUID_V2, ProtocolConstants.DATA_TRANSFER_CHAR_UUID_V2)
    private suspend fun querySnapshot() { control(ProtocolV2Codec.buildGetStatusCommand(commandIds.incrementAndGet())).getOrThrow() }
    private suspend fun readPendingRequest() = requestReadMutex.withLock {
        val bytes = queue.readCharacteristic(ProtocolConstants.SERVICE_UUID_V1, ProtocolConstants.REQUEST_CHAR_UUID_V1).getOrThrow()
        ProtocolV1Codec.parseWatchRequest(bytes.toString(Charsets.UTF_8))?.let { _incomingRequests.emit(it) }
    }
    fun updateBatteryPercent(percent: Int?) { _batteryPercent.value = percent }
    fun updateLastHelloTimestamp(timestampMs: Long?) { _lastHelloTimestampMs.value = timestampMs }
}
