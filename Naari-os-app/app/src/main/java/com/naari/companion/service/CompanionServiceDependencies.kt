package com.naari.companion.service

import android.content.Context
import com.naari.companion.actions.ActionDispatcher
import com.naari.companion.actions.DefaultActionDispatcher
import com.naari.companion.actions.DefaultCallExecutor
import com.naari.companion.actions.DefaultSmsExecutor
import com.naari.companion.ble.AndroidGattTransport
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.ble.DefaultBleSessionManager
import com.naari.companion.ble.GattOperationQueue
import com.naari.companion.data.AppDatabase
import com.naari.companion.ledger.DefaultRequestLedgerRepository
import com.naari.companion.location.DefaultLocationExecutor
import com.naari.companion.actions.AndroidSmsTransport
import com.naari.companion.permissions.AndroidPermissionsRepository

/**
 * Service dependencies locator providing injectable singletons for [CompanionService].
 */
object CompanionServiceDependencies {

    @Volatile
    var bleSessionManager: BleSessionManager? = null

    @Volatile
    var actionDispatcher: ActionDispatcher? = null

    @Volatile private var guardians: com.naari.companion.guardians.GuardiansRepository? = null
    @Volatile private var permissions: com.naari.companion.permissions.PermissionsRepository? = null
    fun getPermissionsRepository(context: Context): com.naari.companion.permissions.PermissionsRepository = synchronized(this) {
        permissions ?: AndroidPermissionsRepository(context.applicationContext).also { permissions = it }
    }
    fun getGuardiansRepository(context: Context): com.naari.companion.guardians.GuardiansRepository = synchronized(this) {
        guardians ?: com.naari.companion.guardians.DefaultGuardiansRepository(
            AppDatabase.getInstance(context.applicationContext).guardiansDao(), getBleSessionManager(context.applicationContext), context.applicationContext
        ).also { guardians = it }
    }

    fun getBleSessionManager(context: Context): BleSessionManager {
        return bleSessionManager ?: synchronized(this) {
            bleSessionManager ?: run {
                val transport = AndroidGattTransport(context)
                val queue = GattOperationQueue(transport)
                val permissionsRepo = getPermissionsRepository(context)
                DefaultBleSessionManager(
                    transport = transport,
                    queue = queue,
                    permissionsRepository = permissionsRepo
                ).also { bleSessionManager = it }
            }
        }
    }

    fun getActionDispatcher(context: Context): ActionDispatcher {
        return actionDispatcher ?: synchronized(this) {
            actionDispatcher ?: run {
                val ble = getBleSessionManager(context)
                val db = AppDatabase.getInstance(context)
                val ledgerRepo = DefaultRequestLedgerRepository(db.requestLedgerDao())
                val smsExecutor = DefaultSmsExecutor(
                    transport = AndroidSmsTransport(context),
                    ledgerRepository = ledgerRepo,
                    bleSessionManager = ble
                )
                val callExecutor = DefaultCallExecutor(context, ledgerRepo, ble)
                val locationExecutor = DefaultLocationExecutor(context, ledgerRepo, ble)

                val onboarding = com.naari.companion.onboarding.DefaultOnboardingRepository(context)

                DefaultActionDispatcher(
                    bleSessionManager = ble,
                    ledgerRepository = ledgerRepo,
                    smsExecutor = smsExecutor,
                    callExecutor = callExecutor,
                    locationExecutor = locationExecutor,
                    onboardingRepository = onboarding
                ).also { actionDispatcher = it }
            }
        }
    }

    @Volatile
    var deviceStateRepository: com.naari.companion.domain.DeviceStateRepository? = null

    fun getDeviceStateRepository(context: Context): com.naari.companion.domain.DeviceStateRepository {
        return deviceStateRepository ?: synchronized(this) {
            deviceStateRepository ?: run {
                val ble = getBleSessionManager(context)
                val perms = getPermissionsRepository(context)
                val onboarding = com.naari.companion.onboarding.DefaultOnboardingRepository(context)
                val db = AppDatabase.getInstance(context)
                val guardians = getGuardiansRepository(context)
                com.naari.companion.data.DefaultDeviceStateRepository(
                    bleSessionManager = ble,
                    permissionsRepository = perms,
                    onboardingRepository = onboarding,
                    guardiansRepository = guardians
                ).also { deviceStateRepository = it }
            }
        }
    }

    @Volatile
    var antiLossManager: com.naari.companion.antiloss.AntiLossManager? = null

    fun getAntiLossManager(context: Context): com.naari.companion.antiloss.AntiLossManager {
        return antiLossManager ?: synchronized(this) {
            antiLossManager ?: run {
                val locationClient = com.naari.companion.location.AndroidLocationClient(context)
                val locationProvider = com.naari.companion.location.DefaultLocationProvider(locationClient)
                com.naari.companion.antiloss.AntiLossManager(context, locationProvider).also { antiLossManager = it }
            }
        }
    }

    @Volatile
    var safeWalkManager: com.naari.companion.safewalk.SafeWalkManager? = null

    fun getSafeWalkManager(context: Context): com.naari.companion.safewalk.SafeWalkManager {
        return safeWalkManager ?: synchronized(this) {
            safeWalkManager ?: run {
                val ble = getBleSessionManager(context)
                val locationClient = com.naari.companion.location.AndroidLocationClient(context)
                val locationProvider = com.naari.companion.location.DefaultLocationProvider(locationClient)
                val db = AppDatabase.getInstance(context)
                val ledgerRepo = DefaultRequestLedgerRepository(db.requestLedgerDao())
                val smsExecutor = DefaultSmsExecutor(
                    transport = AndroidSmsTransport(context),
                    ledgerRepository = ledgerRepo,
                    bleSessionManager = ble
                )
                val guardians = getGuardiansRepository(context)
                com.naari.companion.safewalk.SafeWalkManager(
                    context = context,
                    locationProvider = locationProvider,
                    smsExecutor = smsExecutor,
                    guardiansRepository = guardians,
                    ledgerRepository = ledgerRepo
                ).also { safeWalkManager = it }
            }
        }
    }

    @Volatile
    var safeCheckInManager: com.naari.companion.checkin.SafeCheckInManager? = null

    fun getSafeCheckInManager(context: Context): com.naari.companion.checkin.SafeCheckInManager {
        return safeCheckInManager ?: synchronized(this) {
            safeCheckInManager ?: run {
                val ble = getBleSessionManager(context)
                val locationClient = com.naari.companion.location.AndroidLocationClient(context)
                val locationProvider = com.naari.companion.location.DefaultLocationProvider(locationClient)
                val db = AppDatabase.getInstance(context)
                val ledgerRepo = DefaultRequestLedgerRepository(db.requestLedgerDao())
                val smsExecutor = DefaultSmsExecutor(
                    transport = AndroidSmsTransport(context),
                    ledgerRepository = ledgerRepo,
                    bleSessionManager = ble
                )
                val guardians = getGuardiansRepository(context)
                com.naari.companion.checkin.SafeCheckInManager(
                    locationProvider = locationProvider,
                    smsExecutor = smsExecutor,
                    guardiansRepository = guardians,
                    ledgerRepository = ledgerRepo
                ).also { safeCheckInManager = it }
            }
        }
    }

    fun reset() {
        guardians = null
        permissions = null
        bleSessionManager = null
        actionDispatcher = null
        deviceStateRepository = null
        antiLossManager = null
        safeWalkManager = null
        safeCheckInManager = null
    }
}
