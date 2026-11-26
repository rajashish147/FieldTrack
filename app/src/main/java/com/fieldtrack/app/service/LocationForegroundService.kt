package com.fieldtrack.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.data.local.LocationQueueDao
import com.fieldtrack.app.core.data.local.LocationQueueEntity
import com.fieldtrack.app.core.network.dto.AttendanceSession
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject

data class TrackingShift(val sessionId: String, val owner: String, val checkinAt: Instant)

@AndroidEntryPoint
class LocationForegroundService : Service() {
    @Inject lateinit var queue: LocationQueueDao
    @Inject lateinit var uploader: LocationUploader
    @Inject lateinit var sessions: SessionManager

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var connectivity: ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val syncSignal = Channel<Unit>(Channel.CONFLATED)
    private var trackingJob: Job? = null
    private var locationCallback: LocationCallback? = null
    private var networkCallbackRegistered = false
    private var shift: TrackingShift? = null
    private var lastRecordedAt: Instant? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            syncSignal.trySend(Unit)
        }
    }

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "ft_location_channel"
        private const val NOTIF_ID = 1001
        private const val MAX_ACCURACY_M = 100f
        private const val INTERVAL_MS = 15_000L
        private const val MIN_INTERVAL_MS = 10_000L
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_OWNER = "owner"
        private const val EXTRA_START = "checkin_at"

        private val _activeShift = MutableStateFlow<TrackingShift?>(null)
        val activeShift = _activeShift.asStateFlow()

        fun start(context: Context, owner: String, session: AttendanceSession): Boolean {
            if (_activeShift.value?.let { it.owner == owner && it.sessionId == session.id } == true) return true
            val intent = Intent(context, LocationForegroundService::class.java)
                .putExtra(EXTRA_SESSION_ID, session.id)
                .putExtra(EXTRA_OWNER, owner)
                .putExtra(EXTRA_START, session.checkinAt)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: SecurityException) {
                Log.w(TAG, "Location foreground service permission denied", e)
                false
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Location foreground service must start in the foreground", e)
                false
            }
        }

        fun stop(context: Context, expectedShift: TrackingShift? = null) {
            if (expectedShift == null || _activeShift.value == expectedShift) {
                context.stopService(Intent(context, LocationForegroundService::class.java))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Location tracking", NotificationManager.IMPORTANCE_LOW),
        )
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.trackingShift()
        if (requested == null || !hasFineLocation()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: SecurityException) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (shift != requested) {
            stopTracking()
            shift = requested
            trackingJob = scope.launch {
                val auth = sessions.getSession()
                if (auth?.owner != requested.owner || auth.role != "EMPLOYEE") {
                    stopSelf(startId)
                    return@launch
                }
                queue.assignVerifiedSession(requested.owner, requested.sessionId)
                LocationSyncWorker.schedule(this@LocationForegroundService)
                startTracking(requested)
                launch { syncWhileConnected() }
                launch {
                    sessions.sessions.first { it?.owner != requested.owner || it.role != "EMPLOYEE" }
                    stopSelf()
                }
                launch {
                    uploader.closedSessions.first { it == requested.sessionId }
                    stopSelf()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun Intent.trackingShift(): TrackingShift? {
        val id = getStringExtra(EXTRA_SESSION_ID) ?: return null
        val owner = getStringExtra(EXTRA_OWNER) ?: return null
        val rawStart = getStringExtra(EXTRA_START) ?: return null
        val start = runCatching { OffsetDateTime.parse(rawStart).toInstant() }.getOrNull() ?: return null
        return TrackingShift(id, owner, start)
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startTracking(current: TrackingShift) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setMaxUpdateAgeMillis(0)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (shift != current || locationCallback != this) return
                result.locations.forEach { enqueueLocation(current, it) }
            }
        }
        locationCallback = callback
        try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Using timed GPS upload retries", e)
        }
        try {
            fusedClient.requestLocationUpdates(request, callback, mainLooper)
                .addOnSuccessListener {
                    if (shift == current && locationCallback == callback) _activeShift.value = current
                }
                .addOnFailureListener {
                    if (shift == current && locationCallback == callback) stopSelf()
                }
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun enqueueLocation(current: TrackingShift, location: Location) {
        if (!location.hasAccuracy() || location.accuracy !in 0f..MAX_ACCURACY_M || LocationCompat.isMock(location)) return
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return
        val recordedAt = Instant.ofEpochMilli(location.time)
        if (recordedAt.isBefore(current.checkinAt) || lastRecordedAt?.let { recordedAt <= it } == true) return
        lastRecordedAt = recordedAt
        scope.launch {
            withContext(NonCancellable) {
                queue.insert(
                    LocationQueueEntity(
                        sessionId = current.sessionId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        recordedAt = recordedAt.toString(),
                        owner = current.owner,
                    ),
                )
            }
            syncSignal.trySend(Unit)
        }
    }

    private suspend fun syncWhileConnected() {
        while (true) {
            if (connectivity.activeNetwork != null) uploader.drain()
            withTimeoutOrNull(INTERVAL_MS) { syncSignal.receive() }
        }
    }

    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        if (networkCallbackRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback)
            networkCallbackRegistered = false
        }
        if (_activeShift.value == shift) _activeShift.value = null
        shift = null
        lastRecordedAt = null
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FieldTrack is tracking your shift")
            .setContentText("Your company can see your location while you are checked in.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (shift != null) {
            LocationSyncWorker.stopPeriodic(this)
            LocationSyncWorker.flush(this)
        }
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }
}
