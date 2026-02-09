package com.fieldtrack.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.ui.dashboard.DashboardActivity
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.LocationUtils
import com.fieldtrack.app.util.SecurityUtils
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var unsyncedBatchCount = 0
    private var isTracking = false
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastTimestamp: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isTracking) {
                    startTracking()
                }
            }
            ACTION_STOP -> {
                stopTracking()
            }
            else -> {
                // Service restarted by OS after being killed (START_STICKY)
                if (sessionManager.isCheckedIn) {
                    Log.d(TAG, "Service restarted by OS, resuming tracking")
                    if (!isTracking) {
                        startTracking()
                    }
                } else {
                    Log.d(TAG, "Service restarted but user not checked in, stopping")
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        releaseWakeLock()
        serviceScope.cancel()
        isTracking = false
    }

    private fun startTracking() {
        acquireWakeLock()
        createLocationCallback()
        startForeground(Constants.NOTIFICATION_ID_LOCATION, createNotification("Location tracking is active"))
        startLocationUpdates()
        isTracking = true
        Log.d(TAG, "Location tracking started")
    }

    private fun stopTracking() {
        stopLocationUpdates()
        releaseWakeLock()

        // Final sync of any remaining unsynced locations
        serviceScope.launch {
            try {
                locationRepository.syncUnsyncedLocations()
            } catch (e: Exception) {
                Log.e(TAG, "Error during final sync", e)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isTracking = false
        Log.d(TAG, "Location tracking stopped")
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FieldTrack::LocationTrackingWakeLock"
            ).apply {
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { androidLocation ->
                    // Reject mock locations
                    if (LocationUtils.isLocationFromMockProvider(androidLocation)) {
                        Log.w(TAG, "Mock location detected, skipping")
                        return
                    }

                    // Reject inaccurate fixes
                    if (androidLocation.accuracy > Constants.LOCATION_ACCURACY_THRESHOLD_M) {
                        Log.w(TAG, "Location accuracy ${androidLocation.accuracy}m exceeds threshold, skipping")
                        return
                    }

                    // Velocity check: reject teleportation
                    val now = System.currentTimeMillis()
                    if (lastLat != null && lastLon != null && lastTimestamp > 0L) {
                        if (!SecurityUtils.isVelocityReasonable(
                                lastLat!!, lastLon!!, lastTimestamp,
                                androidLocation.latitude, androidLocation.longitude, now
                            )
                        ) {
                            Log.w(TAG, "Unreasonable velocity detected, skipping location")
                            return
                        }
                    }
                    lastLat = androidLocation.latitude
                    lastLon = androidLocation.longitude
                    lastTimestamp = now

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
                            unsyncedBatchCount++
                            Log.d(TAG, "Location saved: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")

                            // Batch sync: only sync after N locations to reduce network calls
                            if (unsyncedBatchCount >= Constants.LOCATION_SYNC_BATCH_SIZE) {
                                locationRepository.syncUnsyncedLocations()
                                unsyncedBatchCount = 0
                                Log.d(TAG, "Batch sync completed")
                            }

                            // Update notification with last tracked time
                            updateNotification(location)
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

        locationCallback?.let { callback ->
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
        locationCallback = null
    }

    private fun updateNotification(location: Location) {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val lastTime = timeFormat.format(Date())
        val notification = createNotification(
            "Last tracked at $lastTime · Accuracy: ${location.accuracy.toInt()}m"
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID_LOCATION, notification)
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_LOCATION)
            .setContentTitle("FieldTrack")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_location_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
