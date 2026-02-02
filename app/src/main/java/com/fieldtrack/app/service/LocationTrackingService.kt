package com.fieldtrack.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.ui.dashboard.DashboardActivity
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.LocationUtils
import com.fieldtrack.app.util.currentIso8601
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.UUID

class LocationTrackingService : Service() {

    companion object {
        const val TAG = "LocationTrackingService"
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
    }

    private val locationRepository: LocationRepository by inject()
    private val sessionManager: SessionManager by inject()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(Constants.NOTIFICATION_ID_LOCATION, createNotification())
                startLocationUpdates()
                Log.d(TAG, "Location tracking started")
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "Location tracking stopped")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.cancel()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { androidLocation ->
                    if (LocationUtils.isLocationFromMockProvider(androidLocation)) {
                        Log.w(TAG, "Mock location detected, skipping")
                        return
                    }

                    val userId = sessionManager.userId ?: return

                    val location = Location(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        latitude = androidLocation.latitude,
                        longitude = androidLocation.longitude,
                        accuracy = androidLocation.accuracy,
                        timestamp = currentIso8601()
                    )

                    serviceScope.launch {
                        try {
                            locationRepository.saveLocationLocally(location)
                            locationRepository.syncUnsyncedLocations()
                            Log.d(TAG, "Location saved: ${location.latitude}, ${location.longitude}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving location", e)
                        }
                    }
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            Constants.LOCATION_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(Constants.LOCATION_INTERVAL_MS)
            setMaxUpdateDelayMillis(Constants.LOCATION_MAX_INTERVAL_MS)
            setMinUpdateDistanceMeters(Constants.LOCATION_MIN_DISPLACEMENT_M)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_LOCATION)
            .setContentTitle("FieldTrack")
            .setContentText("Location tracking is active")
            .setSmallIcon(R.drawable.ic_location_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
