package com.naari.companion.guardians

import android.content.Context
import com.naari.companion.ble.BleConnectionState
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.data.Guardian
import com.naari.companion.data.GuardiansDao
import com.naari.companion.protocol.InvalidPhoneNumberException
import com.naari.companion.protocol.PhoneProfileMessage
import com.naari.companion.protocol.ProtocolV1Codec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface governing hardware guardian slots and BLE profile synchronization.
 */
interface GuardiansRepository {
    val slot0: StateFlow<Guardian?>
    val slot1: StateFlow<Guardian?>
    val syncState: StateFlow<GuardianSyncState>
    val isPendingSync: StateFlow<Boolean>

    suspend fun saveGuardian(slotIndex: Int, name: String, phone: String): Result<Unit>
    suspend fun clearGuardian(slotIndex: Int): Result<Unit>
    suspend fun swapGuardians(): Result<Unit>
    suspend fun syncProfileToWatch(): Result<Unit>
}

/**
 * Authoritative implementation of [GuardiansRepository].
 *
 * Enforces:
 * - Exactly 2 guardian slots (0: Primary, 1: Secondary)
 * - Safe UTF-8 name truncation up to 32 bytes
 * - Phone number validation (optional '+', 3 to 20 digits)
 * - Persisting to Room database before BLE transmission
 * - Truthful states: Never show "Saved" without qualifier if watch has not confirmed
 * - Automatic background sync whenever watch transitions to [BleConnectionState.Ready]
 * - Guard: Zero silent seeding of fake guardians
 * - Persistence of pending sync state across process restarts
 */
class DefaultGuardiansRepository(
    private val dao: GuardiansDao,
    private val bleSessionManager: BleSessionManager? = null,
    context: Context? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    externalScope: CoroutineScope? = null
) : GuardiansRepository {

    private val syncMutex = Mutex()
    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + dispatcher)
    private val prefs = context?.getSharedPreferences("naari_guardians_sync", Context.MODE_PRIVATE)

    override val slot0: StateFlow<Guardian?> = dao.observeSlot(0)
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val slot1: StateFlow<Guardian?> = dao.observeSlot(1)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _isPendingSync = MutableStateFlow(prefs?.getBoolean("pending_sync", false) ?: false)
    override val isPendingSync: StateFlow<Boolean> = _isPendingSync.asStateFlow()

    private val _syncState = MutableStateFlow<GuardianSyncState>(
        if (_isPendingSync.value) GuardianSyncState.PendingSync else GuardianSyncState.Empty
    )
    override val syncState: StateFlow<GuardianSyncState> = _syncState.asStateFlow()

    private fun updatePendingSync(pending: Boolean) {
        _isPendingSync.value = pending
        prefs?.edit()?.putBoolean("pending_sync", pending)?.commit()
    }

    init {
        // Observe BLE connection state: automatically sync pending profile when watch is Ready
        if (bleSessionManager != null) {
            scope.launch {
                bleSessionManager.connectionState.collect { state ->
                    if (state == BleConnectionState.Ready) {
                        if(_isPendingSync.value) syncProfileToWatch() else reconcileFromWatch()
                    }
                }
            }
        }
    }

    override suspend fun saveGuardian(slotIndex: Int, name: String, phone: String): Result<Unit> {
        require(slotIndex in 0..1) { "Invalid slot index $slotIndex; hardware supports only 0 and 1" }

        val cleanPhone = ProtocolV1Codec.sanitizePhoneNumber(phone)
        if (!ProtocolV1Codec.validatePhoneNumber(cleanPhone)) {
            return Result.failure(InvalidPhoneNumberException(cleanPhone))
        }

        val cleanName = ProtocolV1Codec.truncateNameUtf8(name.trim()).ifBlank { "Guardian" }

        val guardian = Guardian(
            slotIndex = slotIndex,
            name = cleanName,
            phone = cleanPhone,
            updatedAt = System.currentTimeMillis()
        )

        return syncMutex.withLock {
            updatePendingSync(true)
            dao.upsert(guardian)
            syncLocked()
        }
    }

    override suspend fun clearGuardian(slotIndex: Int): Result<Unit> {
        require(slotIndex in 0..1) { "Invalid slot index $slotIndex; hardware supports only 0 and 1" }

        return syncMutex.withLock {
            updatePendingSync(true)
            dao.clearSlot(slotIndex)
            syncLocked()
        }
    }

    override suspend fun swapGuardians(): Result<Unit> = syncMutex.withLock {
        val g0 = dao.getSlot(0)
        val g1 = dao.getSlot(1)

        if (g0 == null && g1 == null) {
            return@withLock Result.success(Unit)
        }

        val now = System.currentTimeMillis()
        updatePendingSync(true)
        dao.replaceAll(listOfNotNull(
            g1?.copy(slotIndex = 0, updatedAt = now),
            g0?.copy(slotIndex = 1, updatedAt = now)
        ))
        syncLocked()
    }

    override suspend fun syncProfileToWatch(): Result<Unit> = syncMutex.withLock { syncLocked() }
    private suspend fun syncLocked(): Result<Unit> {
        val g0 = dao.getSlot(0)
        val g1 = dao.getSlot(1)

        val name0 = g0?.name.orEmpty()
        val phone0 = g0?.phone.orEmpty()
        val name1 = g1?.name.orEmpty()
        val phone1 = g1?.phone.orEmpty()

        val profileMessage = PhoneProfileMessage(
            version = 1,
            op = "profile",
            name0 = name0,
            phone0 = phone0,
            name1 = name1,
            phone1 = phone1
        )

        val ble = bleSessionManager
        if (ble == null || ble.connectionState.value != BleConnectionState.Ready) {
            // Watch is disconnected or not ready: truthfully inform user
            updatePendingSync(true)
            _syncState.value = GuardianSyncState.PendingSync
            return Result.success(Unit)
        }

        _syncState.value = GuardianSyncState.Syncing

        val writeResult = ble.syncProfile(profileMessage)
        return if (writeResult.isSuccess) {
            _syncState.value = GuardianSyncState.SyncedToWatch
            val unchanged = dao.getSlot(0)?.phone.orEmpty() == phone0 && dao.getSlot(1)?.phone.orEmpty() == phone1 &&
                dao.getSlot(0)?.name.orEmpty() == name0 && dao.getSlot(1)?.name.orEmpty() == name1
            updatePendingSync(!unchanged)
            if(!unchanged) scope.launch { syncProfileToWatch() }
            Result.success(Unit)
        } else {
            val errorMsg = writeResult.exceptionOrNull()?.message ?: "BLE write to watch failed"
            _syncState.value = GuardianSyncState.SyncFailed(errorMsg)
            updatePendingSync(true)
            Result.failure(writeResult.exceptionOrNull() ?: Exception(errorMsg))
        }
    }
    private suspend fun reconcileFromWatch() = syncMutex.withLock {
        val ble = bleSessionManager ?: return@withLock
        if(!ble.hasV2ControlService.value) return@withLock
        val response = ble.control(com.naari.companion.protocol.ProtocolV2Codec.buildGetProfileCommand(900000)).getOrNull() ?: return@withLock
        val data = response.appliedData
        if(_isPendingSync.value) return@withLock
        val remoteEmpty = (data["phone0"] as? String).isNullOrEmpty() && (data["phone1"] as? String).isNullOrEmpty()
        if(remoteEmpty && (dao.getSlot(0) != null || dao.getSlot(1) != null)) {
            updatePendingSync(true); syncLocked(); return@withLock
        }
        val remote = (0..1).mapNotNull { i ->
            val phone = data["phone$i"] as? String ?: return@withLock
            val name = data["name$i"] as? String ?: "Guardian"
            if(phone.isEmpty()) null
            else Guardian(slotIndex=i, name=name, phone=phone, updatedAt=System.currentTimeMillis())
        }
        dao.replaceAll(remote)
        _syncState.value = if(remoteEmpty) GuardianSyncState.Empty else GuardianSyncState.SyncedToWatch
    }

}
