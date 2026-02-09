package com.fieldtrack.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.fieldtrack.app.di.appModule
import com.fieldtrack.app.di.networkModule
import com.fieldtrack.app.di.repositoryModule
import com.fieldtrack.app.di.viewModelModule
import com.fieldtrack.app.util.Constants
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class FieldTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()

        initKoin()
        createNotificationChannels()
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
}
