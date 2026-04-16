package com.fieldtrack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.di.appModule
import com.fieldtrack.app.di.networkModule
import com.fieldtrack.app.di.repositoryModule
import com.fieldtrack.app.di.viewModelModule
import com.fieldtrack.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class FieldTrackApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Lazy inject after Koin initialization
    private val sessionManager: SessionManager by inject()
    private val locationRepository: LocationRepository by inject()

    override fun onCreate() {
        super.onCreate()

        // Set default timezone for entire JVM — ensures all time rendering is IST
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))

        // Log uncaught exceptions for crash diagnostics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FATAL", "Crash in ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        initKoin()
        createNotificationChannels()

        // Detect env misconfiguration early (debug only)
        if (BuildConfig.DEBUG) {
            Log.d("FieldTrack", "Supabase initialized: ${BuildConfig.SUPABASE_URL}")
        }
        
        // CRITICAL: Sync unsynced locations on app start
        // Handles case where app was killed during checkout sync
        syncUnsyncedLocationsOnStartup()
    }

    private fun initKoin() {
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FieldTrackApp)
            modules(
                appModule,
                networkModule,
                repositoryModule,
                viewModelModule
            )
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val locationChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_LOCATION,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when location tracking is active during work hours"
                setShowBadge(false)
            }

            val generalChannel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General app notifications"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(locationChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }
    
    /**
     * Sync unsynced locations on app startup.
     * 
     * CRITICAL: Handles edge case where app was killed during checkout sync.
     * Ensures eventual consistency - unsynced locations will be synced on next app start.
     * 
     * This runs in background scope and does not block app startup.
     */
    private fun syncUnsyncedLocationsOnStartup() {
        applicationScope.launch {
            try {
                // Only sync if user is logged in
                if (sessionManager.userId != null) {
                    val unsyncedCount = locationRepository.getUnsyncedCount()
                    
                    if (unsyncedCount > 0) {
                        Log.i("FieldTrackApp", "Startup sync: found $unsyncedCount unsynced locations")
                        
                        val result = locationRepository.syncUnsyncedLocations()
                        
                        when (result) {
                            is com.fieldtrack.app.data.repository.SyncResult.Success -> {
                                Log.i("FieldTrackApp", "Startup sync: ${result.synced} locations synced")
                            }
                            is com.fieldtrack.app.data.repository.SyncResult.Partial -> {
                                Log.w("FieldTrackApp", "Startup sync: ${result.synced} synced, ${result.failed} failed")
                            }
                            is com.fieldtrack.app.data.repository.SyncResult.Failed -> {
                                Log.e("FieldTrackApp", "Startup sync failed: ${result.reason}")
                            }
                        }
                    } else {
                        Log.d("FieldTrackApp", "Startup sync: no unsynced locations")
                    }
                }
            } catch (e: Exception) {
                Log.e("FieldTrackApp", "Startup sync exception", e)
                // Silent fail - WorkManager will retry later
            }
        }
    }
}
