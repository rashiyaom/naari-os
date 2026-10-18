package com.naari.companion.di

import android.content.Context
import com.naari.companion.actions.ActionDispatcher
import com.naari.companion.actions.AndroidCallLauncher
import com.naari.companion.actions.AndroidSmsTransport
import com.naari.companion.actions.CallExecutor
import com.naari.companion.actions.CallLauncher
import com.naari.companion.actions.DefaultCallExecutor
import com.naari.companion.actions.DefaultEmergencyNumberDetector
import com.naari.companion.actions.DefaultSmsExecutor
import com.naari.companion.actions.EmergencyNumberDetector
import com.naari.companion.actions.SmsExecutor
import com.naari.companion.actions.SmsTransport
import com.naari.companion.ble.BleSessionManager
import com.naari.companion.data.AppDatabase
import com.naari.companion.data.GuardiansDao
import com.naari.companion.data.IncidentLogDao
import com.naari.companion.data.RecordingDao
import com.naari.companion.guardians.DefaultGuardiansRepository
import com.naari.companion.guardians.GuardiansRepository
import com.naari.companion.ledger.DefaultRequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerDao
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.location.AndroidLocationClient
import com.naari.companion.location.DefaultLocationExecutor
import com.naari.companion.location.DefaultLocationProvider
import com.naari.companion.location.LocationClient
import com.naari.companion.location.LocationExecutor
import com.naari.companion.location.LocationProvider
import com.naari.companion.onboarding.DefaultOnboardingRepository
import com.naari.companion.onboarding.OnboardingRepository
import com.naari.companion.permissions.AndroidPermissionsRepository
import com.naari.companion.permissions.PermissionsRepository
import com.naari.companion.recordings.DefaultRecordingsRepository
import com.naari.companion.recordings.RecordingsRepository
import com.naari.companion.recordings.RecordingsStorageManager
import com.naari.companion.service.CompanionServiceDependencies
import com.naari.companion.watch.DefaultWatchSettingsRepository
import com.naari.companion.watch.WatchSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Hilt module declaring application-wide singletons and repository bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideGuardiansDao(database: AppDatabase): GuardiansDao {
        return database.guardiansDao()
    }

    @Provides
    fun provideRequestLedgerDao(database: AppDatabase): RequestLedgerDao {
        return database.requestLedgerDao()
    }

    @Provides
    fun provideIncidentLogDao(database: AppDatabase): IncidentLogDao {
        return database.incidentLogDao()
    }

    @Provides
    fun provideRecordingDao(database: AppDatabase): RecordingDao {
        return database.recordingDao()
    }

    @Provides
    @Singleton
    fun providePermissionsRepository(@ApplicationContext context: Context): PermissionsRepository {
        return CompanionServiceDependencies.getPermissionsRepository(context)
    }

    @Provides
    @Singleton
    fun provideBleSessionManager(@ApplicationContext context: Context): BleSessionManager {
        return CompanionServiceDependencies.getBleSessionManager(context)
    }

    @Provides
    @Singleton
    fun provideGuardiansRepository(
        @ApplicationContext context: Context,
        guardiansDao: GuardiansDao,
        bleSessionManager: BleSessionManager
    ): GuardiansRepository {
        return CompanionServiceDependencies.getGuardiansRepository(context)
    }

    @Provides
    @Singleton
    fun provideRequestLedgerRepository(requestLedgerDao: RequestLedgerDao): RequestLedgerRepository {
        return DefaultRequestLedgerRepository(requestLedgerDao)
    }

    @Provides
    @Singleton
    fun provideOnboardingRepository(@ApplicationContext context: Context): OnboardingRepository {
        return DefaultOnboardingRepository(context)
    }

    @Provides
    @Singleton
    fun provideDeviceStateRepository(
        @ApplicationContext context: Context
    ): com.naari.companion.domain.DeviceStateRepository {
        return CompanionServiceDependencies.getDeviceStateRepository(context)
    }

    @Provides
    @Singleton
    fun provideEmergencyNumberDetector(@ApplicationContext context: Context): EmergencyNumberDetector {
        return DefaultEmergencyNumberDetector(context)
    }

    @Provides
    @Singleton
    fun provideCallLauncher(@ApplicationContext context: Context): CallLauncher {
        return AndroidCallLauncher(context)
    }

    @Provides
    @Singleton
    fun provideCallExecutor(
        launcher: CallLauncher,
        emergencyDetector: EmergencyNumberDetector,
        ledgerRepository: RequestLedgerRepository,
        bleSessionManager: BleSessionManager
    ): CallExecutor {
        return DefaultCallExecutor(
            launcher = launcher,
            emergencyDetector = emergencyDetector,
            ledgerRepository = ledgerRepository,
            bleSessionManager = bleSessionManager
        )
    }

    @Provides
    @Singleton
    fun provideSmsTransport(@ApplicationContext context: Context): SmsTransport {
        return AndroidSmsTransport(context)
    }

    @Provides
    @Singleton
    fun provideSmsExecutor(
        transport: SmsTransport,
        ledgerRepository: RequestLedgerRepository,
        bleSessionManager: BleSessionManager
    ): SmsExecutor {
        return DefaultSmsExecutor(
            transport = transport,
            ledgerRepository = ledgerRepository,
            bleSessionManager = bleSessionManager
        )
    }

    @Provides
    @Singleton
    fun provideLocationClient(@ApplicationContext context: Context): LocationClient {
        return AndroidLocationClient(context)
    }

    @Provides
    @Singleton
    fun provideLocationProvider(locationClient: LocationClient): LocationProvider {
        return DefaultLocationProvider(locationClient)
    }

    @Provides
    @Singleton
    fun provideLocationExecutor(
        @ApplicationContext context: Context,
        ledgerRepository: RequestLedgerRepository,
        bleSessionManager: BleSessionManager
    ): LocationExecutor {
        return DefaultLocationExecutor(
            context = context,
            ledgerRepository = ledgerRepository,
            bleSessionManager = bleSessionManager
        )
    }

    @Provides
    @Singleton
    fun provideActionDispatcher(@ApplicationContext context: Context): ActionDispatcher {
        return CompanionServiceDependencies.getActionDispatcher(context)
    }

    @Provides
    @Singleton
    fun provideWatchSettingsRepository(bleSessionManager: BleSessionManager): WatchSettingsRepository {
        return DefaultWatchSettingsRepository(bleSessionManager)
    }

    @Provides
    @Singleton
    fun provideRecordingsStorageManager(@ApplicationContext context: Context): RecordingsStorageManager {
        return RecordingsStorageManager(File(context.filesDir, "recordings"))
    }

    @Provides
    @Singleton
    fun provideRecordingsRepository(
        recordingDao: RecordingDao,
        storageManager: RecordingsStorageManager
    ): RecordingsRepository {
        return DefaultRecordingsRepository(recordingDao, storageManager)
    }

    @Provides
    @Singleton
    fun provideIncidentsRepository(
        incidentLogDao: IncidentLogDao,
        bleSessionManager: BleSessionManager
    ): com.naari.companion.incidents.IncidentsRepository {
        return com.naari.companion.incidents.DefaultIncidentsRepository(incidentLogDao, bleSessionManager)
    }

    @Provides
    @Singleton
    fun provideCompanionDeviceManagerHelper(
        @ApplicationContext context: Context
    ): com.naari.companion.ble.CompanionDeviceManagerHelper {
        return com.naari.companion.ble.CompanionDeviceManagerHelper(context)
    }
}
