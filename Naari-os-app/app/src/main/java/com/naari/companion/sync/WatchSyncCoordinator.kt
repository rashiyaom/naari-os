package com.naari.companion.sync

import android.content.Context
import com.naari.companion.ble.*
import com.naari.companion.data.*
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.incidents.*
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.protocol.*
import com.naari.companion.recordings.*
import com.naari.companion.safewalk.SafeWalkManager
import com.naari.companion.safewalk.SafeWalkState
import com.naari.companion.service.CompanionServiceController
import com.naari.companion.watch.WatchSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** One process-wide owner of watch-to-phone synchronization. No demo data enters this path. */
@Singleton
class WatchSyncCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ble: BleSessionManager,
    private val settings: WatchSettingsRepository,
    private val recordings: RecordingsRepository,
    private val recordingDao: RecordingDao,
    private val onboarding: OnboardingRepository,
    private val guardians: GuardiansRepository,
    private val permissions: PermissionsRepository,
    private val deviceState: DeviceStateRepository,
    private val incidents: IncidentsRepository,
    private val safeWalk: SafeWalkManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ids = AtomicInteger(1000000)
    private var started = false
    private var transferJob: Job? = null
    private var lastAttempt = 0L
    private var incident: Long? = null
    @Volatile private var walkStatusInitialized = false
    @Volatile private var suppressNextWalkCommand = false
    @Volatile private var pendingPhoneMode: WatchMode? = null
    @Volatile private var localWalkDirty = false
    private var seenWalkState = false
    fun start() {
        if(started) return
        started = true
        scope.launch {
            combine(onboarding.pairedDeviceAddress, permissions.permissions) { address, grants -> address to grants.canConnectBluetooth }
                .collect { (address, allowed) ->
                    if(address != null && allowed) {
                        runCatching { CompanionServiceController.start(context) }
                        ble.connect(address)
                    }
                }
        }
        scope.launch {
            ble.connectionState.collect { state ->
                if(state != BleConnectionState.Ready) {
                    transferJob?.cancel()
                    settings.setCapabilities(WatchCapabilities.NONE)
                    walkStatusInitialized = false
                }
            }
        }
        scope.launch {
            safeWalk.state
                .map { it is SafeWalkState.Active }
                .distinctUntilChanged()
                .collect { active ->
                    if (!seenWalkState) {
                        seenWalkState = true
                        if (!walkStatusInitialized) {
                            localWalkDirty = active
                            return@collect
                        }
                    }
                    if (suppressNextWalkCommand) {
                        suppressNextWalkCommand = false
                        return@collect
                    }
                    if (!walkStatusInitialized || ble.connectionState.value != BleConnectionState.Ready ||
                        !ble.hasV2ControlService.value) {
                        localWalkDirty = true
                        return@collect
                    }
                    val desired = if (active) WatchMode.WATCH else WatchMode.SAFE
                    pendingPhoneMode = desired
                    try {
                        command("set_mode", "{\"mode\":\"${desired.name}\"}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        pendingPhoneMode = null
                        localWalkDirty = true
                    }
                }
        }
        scope.launch {
            ble.rawSnapshots.collect { response ->
                val data = response.appliedData
                val snapshot = ProtocolV2Codec.parseStatusSnapshot(data)
                settings.handleStatusSnapshot(snapshot)
                reconcileSafeWalk(snapshot.mode)
                deviceState.notifySyncCompleted(System.currentTimeMillis())
                val mode = data["mode"] as? String
                if(mode == "EMERGENCY") {
                    val boot = (data["boot_id"] as? Number)?.toLong() ?: 0L
                    val sequence = (data["incident_id"] as? Number)?.toLong() ?: 0L
                    incident = (boot shl 32) or sequence
                    val seconds = (data["countdown"] as? Number)?.toLong() ?: 0
                    incidents.onWatchIncidentEvent(incident!!, if(data["trigger"] == "FALL") IncidentTrigger.FallDetected else IncidentTrigger.ManualSos,
                        if(seconds > 0) IncidentLifecycleState.Countdown else IncidentLifecycleState.Dispatched,
                        if(seconds > 0) System.currentTimeMillis() + seconds * 1000 else null)
                } else if(incident != null) {
                    incidents.resolveActiveIncident("RESOLVED_ON_WATCH")
                    incident = null
                }
                val count = (data["recording_count"] as? Number)?.toInt() ?: 0
                if(count > 0 && transferJob?.isActive != true && System.currentTimeMillis() - lastAttempt > 10000) {
                    lastAttempt = System.currentTimeMillis()
                    transferJob = scope.launch { syncRecordings(count) }
                }
            }
        }
    }
    private fun reconcileSafeWalk(mode: WatchMode?) {
        if (mode == null || mode == WatchMode.EMERGENCY) return
        walkStatusInitialized = true
        if (localWalkDirty) {
            localWalkDirty = false
            val desired = if (safeWalk.state.value is SafeWalkState.Active) WatchMode.WATCH else WatchMode.SAFE
            pendingPhoneMode = desired
            scope.launch {
                try {
                    command("set_mode", "{\"mode\":\"${desired.name}\"}")
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    pendingPhoneMode = null
                    localWalkDirty = true
                }
            }
            return
        }
        val pending = pendingPhoneMode
        if (pending != null) {
            if (mode == pending) pendingPhoneMode = null
            return
        }
        val phoneActive = safeWalk.state.value is SafeWalkState.Active
        if (mode == WatchMode.WATCH && !phoneActive) {
            suppressNextWalkCommand = true
            safeWalk.startJourney(15)
        } else if (mode == WatchMode.SAFE && phoneActive) {
            suppressNextWalkCommand = true
            safeWalk.cancel()
        }
    }
    private suspend fun command(op: String, args: String = "{}"): ControlResponse {
        val json = "{\"v\":2,\"cmd\":${ids.incrementAndGet()},\"op\":\"$op\",\"args\":$args}"
        return ble.control(json).getOrThrow()
    }
    private suspend fun syncRecordings(count: Int) {
        try {
            for(index in 0 until minOf(count, 6)) {
                val m = command("get_recording", "{\"index\":$index}").appliedData
                val id = m["id"] as? String ?: continue
                require(id.matches(Regex("[a-f0-9]{32}")))
                val hash = m["sha256"] as? String ?: continue
                val bytes = (m["bytes"] as? Number)?.toLong() ?: continue
                require(bytes in 2..320000 && bytes % 2 == 0L)
                val existing = recordingDao.getByRecordingId(id)
                if(existing?.syncState != RecordingSyncState.VerifiedOnPhone || existing.localFilePath?.let { File(it).exists() } != true) {
                    val captured = (m["timestamp_ms"] as? Number)?.toLong() ?: 0
                    recordings.addCapturedRecording(id, onboarding.pairedDeviceAddress.first().orEmpty(),
                        if(captured > 0) captured else System.currentTimeMillis(), bytes * 1000 / 32000,
                        bytes, hash)
                    recordings.executeTransfer(id) { offset, consume ->
                        var next = offset
                        while(next < bytes) {
                            ensureActiveSession()
                            command("recording_chunk", "{\"id\":\"$id\",\"offset\":$next}")
                            val packet = ble.readAudioChunk().getOrThrow()
                            val chunk = RecordingTransferClient.parseChunk(packet)
                            check(chunk.byteOffset == next && chunk.length > 0) { "Invalid memo chunk offset" }
                            consume(chunk); next += chunk.length
                        }
                    }.getOrThrow()
                }
                // Phone's WAV + Room entry are durable before allowing watch retention cleanup.
                command("recording_ack", "{\"id\":\"$id\",\"sha256\":\"$hash\"}")
            }
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { deviceState.notifySyncFailed(e.message ?: "Recording sync failed") }
    }
    private fun ensureActiveSession() { check(ble.connectionState.value == BleConnectionState.Ready) { "Watch disconnected" } }
}
