package com.naari.companion.guardians

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.ble.ReadinessState
import com.naari.companion.data.Guardian
import com.naari.companion.domain.DeviceStateRepository
import com.naari.companion.domain.model.DeviceStatus
import com.naari.companion.domain.model.SyncStatus
import com.naari.companion.protocol.ProtocolConstants
import com.naari.companion.protocol.ProtocolV1Codec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class GuardiansUiState(
    val slot0: Guardian? = null,
    val slot1: Guardian? = null,
    val syncState: GuardianSyncState = GuardianSyncState.Empty,
    val isPendingSync: Boolean = false,
    val deviceStatus: DeviceStatus = DeviceStatus.NotPaired,
    val watchSyncStatus: SyncStatus = SyncStatus.UpToDate(),
    val editingSlot: Int? = null,
    val editorName: String = "",
    val editorPhone: String = "",
    val editorPhoneError: String? = null,
    val editorRemainingBytes: Int = ProtocolConstants.MAX_NAME_BYTES,
    val isSaving: Boolean = false
) {
    val isEditorOpen: Boolean
        get() = editingSlot != null

    val isEditorSaveEnabled: Boolean
        get() = editorName.isNotBlank() &&
            editorPhone.isNotBlank() &&
            editorPhoneError == null &&
            editorRemainingBytes >= 0
}

@HiltViewModel
class GuardiansViewModel @Inject constructor(
    private val repository: GuardiansRepository,
    private val deviceStateRepository: DeviceStateRepository
) : ViewModel() {

    // Secondary constructor for unit testing with repository only
    constructor(repository: GuardiansRepository) : this(
        repository = repository,
        deviceStateRepository = createFallbackDeviceStateRepo()
    )

    private val _editorSlot = MutableStateFlow<Int?>(null)
    private val _editorName = MutableStateFlow("")
    private val _editorPhone = MutableStateFlow("")
    private val _editorPhoneError = MutableStateFlow<String?>(null)
    private val _isSaving = MutableStateFlow(false)

    val uiState: StateFlow<GuardiansUiState> = combine(
        repository.slot0,
        repository.slot1,
        repository.syncState,
        repository.isPendingSync,
        deviceStateRepository.deviceStatus,
        deviceStateRepository.syncStatus,
        _editorSlot,
        _editorName,
        _editorPhone,
        _editorPhoneError,
        _isSaving
    ) { params ->
        val g0 = params[0] as? Guardian
        val g1 = params[1] as? Guardian
        val sync = params[2] as GuardianSyncState
        val pending = params[3] as Boolean
        val devStatus = params[4] as DeviceStatus
        val watchSync = params[5] as SyncStatus
        val slot = params[6] as? Int
        val name = params[7] as String
        val phone = params[8] as String
        val error = params[9] as? String
        val saving = params[10] as Boolean

        val usedBytes = name.toByteArray(StandardCharsets.UTF_8).size
        val remainingBytes = (ProtocolConstants.MAX_NAME_BYTES - usedBytes).coerceAtLeast(0)

        GuardiansUiState(
            slot0 = g0,
            slot1 = g1,
            syncState = sync,
            isPendingSync = pending,
            deviceStatus = devStatus,
            watchSyncStatus = watchSync,
            editingSlot = slot,
            editorName = name,
            editorPhone = phone,
            editorPhoneError = error,
            editorRemainingBytes = remainingBytes,
            isSaving = saving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = GuardiansUiState()
    )

    fun openEditor(slotIndex: Int) {
        val currentGuardian = when (slotIndex) {
            0 -> uiState.value.slot0
            1 -> uiState.value.slot1
            else -> null
        }

        _editorSlot.value = slotIndex
        _editorName.value = currentGuardian?.name ?: ""
        _editorPhone.value = currentGuardian?.phone ?: ""
        _editorPhoneError.value = null
        _isSaving.value = false
    }

    fun closeEditor() {
        _editorSlot.value = null
        _editorName.value = ""
        _editorPhone.value = ""
        _editorPhoneError.value = null
        _isSaving.value = false
    }

    fun onNameChanged(name: String) {
        val bytes = name.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= ProtocolConstants.MAX_NAME_BYTES) {
            _editorName.value = name
        } else {
            // Trim to fit within 32 UTF-8 bytes limit
            var trimmed = name
            while (trimmed.isNotEmpty() && trimmed.toByteArray(StandardCharsets.UTF_8).size > ProtocolConstants.MAX_NAME_BYTES) {
                trimmed = trimmed.dropLast(1)
            }
            _editorName.value = trimmed
        }
    }

    fun onPhoneChanged(phone: String) {
        val sanitized = if (phone.any { it in " ()-." }) {
            ProtocolV1Codec.sanitizePhoneNumber(phone)
        } else {
            phone
        }
        _editorPhone.value = sanitized
        validatePhone(sanitized)
    }

    fun onContactPicked(name: String, phone: String) {
        val sanitizedPhone = ProtocolV1Codec.sanitizePhoneNumber(phone)
        onNameChanged(name)
        onPhoneChanged(sanitizedPhone)
    }

    private fun validatePhone(phone: String) {
        val isValid = ProtocolV1Codec.validatePhoneNumber(phone)
        _editorPhoneError.value = if (isValid || phone.isBlank()) {
            null
        } else {
            "Invalid phone format. Must be E.164 or 7-15 digits."
        }
    }

    fun swapGuardians() {
        deviceStateRepository.notifySyncStarted()
        viewModelScope.launch {
            val result = repository.swapGuardians()
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to reorder guardians on watch")
            }
        }
    }

    fun saveEditor() {
        val slot = _editorSlot.value ?: return
        val name = _editorName.value.trim()
        val phone = _editorPhone.value.trim()

        if (name.isBlank() || phone.isBlank() || _editorPhoneError.value != null) {
            return
        }

        _isSaving.value = true
        deviceStateRepository.notifySyncStarted()
        viewModelScope.launch {
            val result = repository.saveGuardian(slot, name, phone)
            _isSaving.value = false
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
                closeEditor()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to save guardian to watch")
            }
        }
    }

    fun clearSlot(slotIndex: Int) {
        deviceStateRepository.notifySyncStarted()
        viewModelScope.launch {
            val result = repository.clearGuardian(slotIndex)
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to clear guardian slot on watch")
            }
        }
    }

    fun retrySync() {
        deviceStateRepository.notifySyncStarted()
        viewModelScope.launch {
            val result = repository.syncProfileToWatch()
            if (result.isSuccess) {
                deviceStateRepository.notifySyncCompleted()
            } else {
                deviceStateRepository.notifySyncFailed("Failed to synchronize with watch")
            }
        }
    }

    companion object {
        private fun createFallbackDeviceStateRepo(): DeviceStateRepository = object : DeviceStateRepository {
            override val deviceStatus = MutableStateFlow<DeviceStatus>(DeviceStatus.NotPaired)
            override val syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.UpToDate())
            override val readinessState = MutableStateFlow(ReadinessState.Disconnected)
            override val batteryPercent = MutableStateFlow<Int?>(null)
            override val pairedDeviceAddress = MutableStateFlow<String?>(null)
            override val pairedDeviceName = MutableStateFlow<String?>(null)
            override val lastSyncTimestampMs = MutableStateFlow<Long?>(null)
            override fun notifySyncStarted() {}
            override fun notifySyncCompleted(timestampMs: Long) {}
            override fun notifySyncFailed(reason: String) {}
        }
    }
}
