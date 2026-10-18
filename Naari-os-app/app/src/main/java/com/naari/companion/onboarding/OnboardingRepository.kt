package com.naari.companion.onboarding

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository interface for managing onboarding completion status and paired watch identity.
 * Ref: PRD Section 1 & Master Prompt Task 14.
 */
interface OnboardingRepository {
    val isOnboardingCompleted: Flow<Boolean>
    val pairedDeviceAddress: Flow<String?>
    val pairedDeviceName: Flow<String?>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun savePairedDevice(address: String, name: String)
    suspend fun clearPairedDevice()
    suspend fun resetOnboarding()
}

/**
 * Production implementation of [OnboardingRepository] backed by private [SharedPreferences].
 * Thread-safe and exposes immediate reactive StateFlows.
 */
class DefaultOnboardingRepository(context: Context) : OnboardingRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isCompletedSync: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    val pairedAddressSync: String?
        get() = prefs.getString(KEY_PAIRED_ADDRESS, null)

    private val _isOnboardingCompleted = MutableStateFlow(
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    )
    override val isOnboardingCompleted: Flow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _pairedDeviceAddress = MutableStateFlow(
        prefs.getString(KEY_PAIRED_ADDRESS, null)
    )
    override val pairedDeviceAddress: Flow<String?> = _pairedDeviceAddress.asStateFlow()

    private val _pairedDeviceName = MutableStateFlow(
        prefs.getString(KEY_PAIRED_NAME, null)
    )
    override val pairedDeviceName: Flow<String?> = _pairedDeviceName.asStateFlow()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _isOnboardingCompleted.value = completed
    }

    override suspend fun savePairedDevice(address: String, name: String) {
        prefs.edit()
            .putString(KEY_PAIRED_ADDRESS, address)
            .putString(KEY_PAIRED_NAME, name)
            .apply()
        _pairedDeviceAddress.value = address
        _pairedDeviceName.value = name
    }

    override suspend fun clearPairedDevice() {
        prefs.edit()
            .remove(KEY_PAIRED_ADDRESS)
            .remove(KEY_PAIRED_NAME)
            .apply()
        _pairedDeviceAddress.value = null
        _pairedDeviceName.value = null
    }

    override suspend fun resetOnboarding() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .remove(KEY_PAIRED_ADDRESS)
            .remove(KEY_PAIRED_NAME)
            .apply()
        _isOnboardingCompleted.value = false
        _pairedDeviceAddress.value = null
        _pairedDeviceName.value = null
    }

    companion object {
        private const val PREFS_NAME = "naari_companion_onboarding_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
        private const val KEY_PAIRED_ADDRESS = "key_paired_address"
        private const val KEY_PAIRED_NAME = "key_paired_name"
    }
}
