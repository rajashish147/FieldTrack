package com.fieldtrack.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.SyncResult
import com.fieldtrack.app.ui.dashboard.DashboardActivity
import com.fieldtrack.app.ui.employee.EmployeeHomeActivity
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

/**
 * Hardened foreground service for continuous location tracking.
 * 
 * This service implements defensive mechanisms for:
 * - Runtime permission revocation
 * - GPS provider state changes
 * - Authentication session expiry
 * - Service lifecycle edge cases
 * 
 * The service uses a state machine to ensure deterministic behavior
 * and always stops with a clearly defined reason.
 */
class LocationTrackingService : Service() {

    companion object {
        const val TAG = "LocationTrackingService"
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        
        private const val PREFS_SERVICE_STATE = "service_state"
        private const val KEY_STATE = "state"
        private const val KEY_STOP_REASON = "stop_reason"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_LAST_LOCATION_TIME = "last_location_time"
        private const val KEY_SESSION_INVALID_REASON = "session_invalid_reason"
        
        private const val PERMISSION_CHECK_INTERVAL_MS = 30_000L // 30 seconds
        private const val WAKELOCK_TIMEOUT_MS = 8 * 60 * 60 * 1000L // 8 hours
        private const val WAKELOCK_RENEWAL_BEFORE_MS = 30 * 60 * 1000L // 30 minutes before expiry
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

    // State management
    private var currentState: ServiceState = ServiceState.Stopped(
        StopReason.UNKNOWN,
        System.currentTimeMillis()
    )
    
    // Monitors
    private lateinit var permissionMonitor: PermissionMonitor
    private lateinit var locationProviderMonitor: LocationProviderMonitor
    private lateinit var sessionMonitor: SessionMonitor
    
    // Handlers
    private val permissionCheckHandler = Handler(Looper.getMainLooper())
    private val wakeLockRenewalHandler = Handler(Looper.getMainLooper())
    
    // Flags
    private var monitorsRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initializeMonitors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                // CRITICAL: Must call startForeground() IMMEDIATELY for foreground services
                // Android requires this within 5 seconds
                startForeground(Constants.NOTIFICATION_ID_LOCATION, createNotification("Initializing location tracking..."))
                
                if (!isTracking) {
                    if (validatePreconditions()) {
                        startTracking()
                        return START_STICKY
                    } else {
                        Log.w(TAG, "Preconditions not met, stopping service")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
                return START_STICKY
            }
            ACTION_STOP -> {
                // Don't call startForeground() when stopping - just stop immediately
                stopTrackingWithReason(StopReason.USER_CHECKOUT)
                return START_NOT_STICKY
            }
            else -> {
                // Service restarted by OS after being killed (START_STICKY)
                Log.d(TAG, "Service restarted by OS")
                
                // Call startForeground() for OS restart
                startForeground(Constants.NOTIFICATION_ID_LOCATION, createNotification("Initializing location tracking..."))
                
                if (validatePreconditions()) {
                    Log.d(TAG, "Preconditions valid, resuming tracking")
                    if (!isTracking) {
                        startTracking()
                    }
                    return START_STICKY
                } else {
                    Log.w(TAG, "Service restarted but preconditions not met, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
    }
    
    /**
     * Validates all preconditions required for location tracking.
     * 
     * @return true if all preconditions are met, false otherwise
     */
    private fun validatePreconditions(): Boolean {
        // Check 1: Location permission
        if (!LocationUtils.hasLocationPermission(this)) {
            Log.w(TAG, "Precondition failed: Location permission not granted")
            return false
        }
        
        // Check 2: Background location permission (API 29+)
        if (!LocationUtils.hasBackgroundLocationPermission(this)) {
            Log.w(TAG, "Precondition failed: Background location permission not granted")
            return false
        }
        
        // Check 3: GPS enabled
        if (!LocationUtils.isGpsEnabled(this)) {
            Log.w(TAG, "Precondition failed: GPS provider disabled")
            return false
        }
        
        // Check 4: Valid session
        if (!isSessionValid()) {
            Log.w(TAG, "Precondition failed: Invalid session")
            return false
        }
        
        // Check 5: User checked in
        if (!sessionManager.isCheckedIn) {
            Log.w(TAG, "Precondition failed: User not checked in")
            return false
        }
        
        Log.d(TAG, "All preconditions validated successfully")
        return true
    }
    
    /**
     * Validates the current authentication session.
     * 
     * @return true if session is valid, false otherwise
     */
    private fun isSessionValid(): Boolean {
        return sessionManager.userId != null &&
               sessionManager.accessToken != null &&
               !sessionManager.isTokenExpired &&
               sessionManager.isCheckedIn
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        stopLocationUpdates()
        releaseWakeLock()
        unregisterMonitors()
        serviceScope.cancel()
        isTracking = false
    }
    
    /**
     * Initializes all health monitors.
     */
    private fun initializeMonitors() {
        permissionMonitor = PermissionMonitor()
        locationProviderMonitor = LocationProviderMonitor()
        sessionMonitor = SessionMonitor()
    }
    
    /**
     * Registers all health monitors to detect runtime failures.
     */
    private fun registerMonitors() {
        if (monitorsRegistered) {
            Log.d(TAG, "Monitors already registered")
            return
        }
        
        try {
            // Permission monitor - use polling for all API levels
            // Android doesn't provide a reliable broadcast for permission changes
            startPermissionPolling()
            Log.d(TAG, "Permission monitor registered (polling)")
            
            // Location provider monitor
            val providerFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            registerReceiver(locationProviderMonitor, providerFilter)
            Log.d(TAG, "Location provider monitor registered")
            
            // Session monitor
            sessionManager.prefs.registerOnSharedPreferenceChangeListener(sessionMonitor)
            Log.d(TAG, "Session monitor registered")
            
            monitorsRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Error registering monitors", e)
        }
    }
    
    /**
     * Unregisters all health monitors.
     */
    private fun unregisterMonitors() {
        if (!monitorsRegistered) {
            return
        }
        
        try {
            unregisterReceiver(locationProviderMonitor)
            Log.d(TAG, "Location provider monitor unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering location provider monitor", e)
        }
        
        try {
            sessionManager.prefs.unregisterOnSharedPreferenceChangeListener(sessionMonitor)
            Log.d(TAG, "Session monitor unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering session monitor", e)
        }
        
        // Stop permission polling
        permissionCheckHandler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Permission polling stopped")
        
        monitorsRegistered = false
    }
    
    /**
     * Starts periodic permission checking for API < 33.
     */
    private fun startPermissionPolling() {
        val runnable = object : Runnable {
            override fun run() {
                if (isTracking) {
                    permissionMonitor.checkPermissions()
                    permissionCheckHandler.postDelayed(this, PERMISSION_CHECK_INTERVAL_MS)
                }
            }
        }
        permissionCheckHandler.post(runnable)
    }

    private fun startTracking() {
        registerMonitors()
        acquireWakeLock()
        createLocationCallback()
        startForeground(Constants.NOTIFICATION_ID_LOCATION, createNotification("Location tracking is active"))
        startLocationUpdates()
        updateState(ServiceState.Active(System.currentTimeMillis()))
        isTracking = true
        Log.d(TAG, "Location tracking started")
    }

    /**
     * Stops tracking with a specific reason.
     * This method ensures clean shutdown and persists the stop reason.
     */
    private fun stopTrackingWithReason(reason: StopReason) {
        Log.i(TAG, "Stopping service with reason: $reason")
        
        // Mark as not tracking immediately to prevent race conditions
        isTracking = false
        
        // Update state before stopping
        updateState(ServiceState.Stopped(reason, System.currentTimeMillis()))
        
        // Persist stop reason for observability
        persistStopReason(reason)
        
        // Stop location updates
        stopLocationUpdates()
        
        // Release WakeLock
        releaseWakeLock()
        
        // Unregister monitors
        unregisterMonitors()
        
        // Clear check-in flag if stopped due to error
        if (reason != StopReason.USER_CHECKOUT && reason != StopReason.USER_LOGOUT) {
            sessionManager.isCheckedIn = false
        }
        
        // Final sync (best effort)
        serviceScope.launch {
            try {
                val result = locationRepository.syncUnsyncedLocations()
                when (result) {
                    is SyncResult.Success -> Log.d(TAG, "Final sync: ${result.synced} synced")
                    is SyncResult.Partial -> Log.w(TAG, "Final sync partial: ${result.synced} synced, ${result.failed} failed")
                    is SyncResult.Failed -> Log.e(TAG, "Final sync failed: ${result.reason}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during final sync", e)
            } finally {
                // Only stop service if tracking hasn't restarted
                if (!isTracking) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    Log.d(TAG, "Service restarted during shutdown, skipping stopSelf()")
                }
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null || wakeLock?.isHeld == false) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FieldTrack::LocationTrackingWakeLock"
            ).apply {
                acquire(WAKELOCK_TIMEOUT_MS)
            }
            Log.d(TAG, "WakeLock acquired with ${WAKELOCK_TIMEOUT_MS / 1000 / 60} minute timeout")
            
            // Schedule renewal before timeout
            scheduleWakeLockRenewal()
        }
    }
    
    /**
     * Schedules WakeLock renewal before it expires.
     */
    private fun scheduleWakeLockRenewal() {
        val renewalDelay = WAKELOCK_TIMEOUT_MS - WAKELOCK_RENEWAL_BEFORE_MS
        wakeLockRenewalHandler.postDelayed({
            if (isTracking) {
                Log.d(TAG, "Renewing WakeLock")
                releaseWakeLock()
                acquireWakeLock()
            }
        }, renewalDelay)
    }

    private fun releaseWakeLock() {
        wakeLockRenewalHandler.removeCallbacksAndMessages(null)
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { androidLocation ->
                    // Check session validity before processing location
                    if (!isSessionValid()) {
                        Log.w(TAG, "Session invalid during location update, stopping service")
                        handleSessionInvalid()
                        return
                    }
                    
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
                                val syncResult = locationRepository.syncUnsyncedLocations()
                                unsyncedBatchCount = 0
                                when (syncResult) {
                                    is SyncResult.Success -> Log.d(TAG, "Batch sync: ${syncResult.synced} synced")
                                    is SyncResult.Partial -> Log.w(TAG, "Batch sync partial: ${syncResult.synced} synced, ${syncResult.failed} failed")
                                    is SyncResult.Failed -> Log.e(TAG, "Batch sync failed: ${syncResult.reason}")
                                }
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
        
        // Update state with last location time
        updateState(ServiceState.Active(System.currentTimeMillis()))
    }
    
    /**
     * Updates the current service state and persists it.
     */
    private fun updateState(newState: ServiceState) {
        currentState = newState
        persistState(newState)
        updateNotificationForState(newState)
        Log.d(TAG, "State transition: $newState")
    }
    
    /**
     * Persists the current service state to SharedPreferences.
     */
    private fun persistState(state: ServiceState) {
        val prefs = getSharedPreferences(PREFS_SERVICE_STATE, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        when (state) {
            is ServiceState.Active -> {
                editor.putString(KEY_STATE, "active")
                editor.putLong(KEY_LAST_LOCATION_TIME, state.lastLocationTime)
            }
            is ServiceState.PermissionRevoked -> {
                editor.putString(KEY_STATE, "permission_revoked")
                editor.putLong(KEY_TIMESTAMP, state.timestamp)
            }
            is ServiceState.LocationDisabled -> {
                editor.putString(KEY_STATE, "location_disabled")
                editor.putLong(KEY_TIMESTAMP, state.timestamp)
            }
            is ServiceState.SessionInvalid -> {
                editor.putString(KEY_STATE, "session_invalid")
                editor.putString(KEY_SESSION_INVALID_REASON, state.reason)
                editor.putLong(KEY_TIMESTAMP, state.timestamp)
            }
            is ServiceState.Stopped -> {
                editor.putString(KEY_STATE, "stopped")
                editor.putString(KEY_STOP_REASON, state.reason.name)
                editor.putLong(KEY_TIMESTAMP, state.timestamp)
            }
        }
        
        editor.apply()
    }
    
    /**
     * Persists the stop reason to SharedPreferences.
     */
    private fun persistStopReason(reason: StopReason) {
        val prefs = getSharedPreferences(PREFS_SERVICE_STATE, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_STOP_REASON, reason.name)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Handles permission revocation by stopping the service.
     */
    private fun handlePermissionRevoked() {
        Log.w(TAG, "Location permission revoked, stopping service")
        updateState(ServiceState.PermissionRevoked(System.currentTimeMillis()))
        stopTrackingWithReason(StopReason.PERMISSION_REVOKED)
    }
    
    /**
     * Handles GPS provider being disabled by stopping the service.
     */
    private fun handleLocationDisabled() {
        Log.w(TAG, "GPS provider disabled, stopping service")
        updateState(ServiceState.LocationDisabled(System.currentTimeMillis()))
        stopTrackingWithReason(StopReason.LOCATION_DISABLED)
    }
    
    /**
     * Handles session invalidation by stopping the service.
     */
    private fun handleSessionInvalid() {
        val reason = when {
            sessionManager.userId == null -> "User ID cleared (logout)"
            sessionManager.accessToken == null -> "Access token cleared"
            sessionManager.isTokenExpired -> "Token expired"
            !sessionManager.isCheckedIn -> "User checked out"
            else -> "Unknown"
        }
        
        Log.w(TAG, "Session invalid: $reason, stopping service")
        updateState(ServiceState.SessionInvalid(reason, System.currentTimeMillis()))
        
        val stopReason = when {
            !sessionManager.isCheckedIn -> StopReason.USER_CHECKOUT
            sessionManager.userId == null -> StopReason.USER_LOGOUT
            sessionManager.isTokenExpired -> StopReason.SESSION_EXPIRED
            else -> StopReason.SESSION_CLEARED
        }
        
        stopTrackingWithReason(stopReason)
    }
    
    /**
     * Monitor for runtime permission changes.
     * 
     * Note: Android doesn't provide a reliable broadcast for permission changes.
     * We use polling as the most reliable approach across all API levels.
     */
    private inner class PermissionMonitor : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // This receiver is registered but not used on API 33+
            // Kept for potential future Android API improvements
            checkPermissions()
        }
        
        fun checkPermissions() {
            if (!LocationUtils.hasLocationPermission(this@LocationTrackingService) ||
                !LocationUtils.hasBackgroundLocationPermission(this@LocationTrackingService)) {
                handlePermissionRevoked()
            }
        }
    }
    
    /**
     * Monitor for GPS provider state changes.
     */
    private inner class LocationProviderMonitor : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkLocationProvider()
            }
        }
        
        private fun checkLocationProvider() {
            if (!LocationUtils.isGpsEnabled(this@LocationTrackingService)) {
                handleLocationDisabled()
            }
        }
    }
    
    /**
     * Monitor for authentication session changes.
     */
    private inner class SessionMonitor : SharedPreferences.OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            when (key) {
                Constants.PREF_ACCESS_TOKEN,
                Constants.PREF_USER_ID,
                Constants.PREF_IS_CHECKED_IN -> {
                    checkSession()
                }
            }
        }
        
        private fun checkSession() {
            if (!isSessionValid()) {
                handleSessionInvalid()
            }
        }
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
    
    /**
     * Updates notification based on current service state.
     */
    private fun updateNotificationForState(state: ServiceState) {
        val notification = when (state) {
            is ServiceState.Active -> {
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val lastTime = timeFormat.format(Date(state.lastLocationTime))
                createActiveNotification(lastTime)
            }
            is ServiceState.PermissionRevoked -> {
                createPermissionRevokedNotification()
            }
            is ServiceState.LocationDisabled -> {
                createLocationDisabledNotification()
            }
            is ServiceState.SessionInvalid -> {
                createSessionInvalidNotification(state.reason)
            }
            is ServiceState.Stopped -> {
                // Notification will be removed by stopForeground()
                return
            }
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID_LOCATION, notification)
    }
    
    /**
     * Creates notification for active tracking state.
     */
    private fun createActiveNotification(lastTime: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, EmployeeHomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val workDuration = formatWorkDuration()
        val contentText = "Working for $workDuration — Tap to review"
        val bigText = "Active Work Session\nWorking for $workDuration\nLast update: $lastTime"

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_LOCATION)
            .setContentTitle("Active Work Session")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(getColor(R.color.session_active_accent))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .addAction(createStopAction())
            .build()
    }
    
    /**
     * Formats work duration from check-in time.
     */
    private fun formatWorkDuration(): String {
        val checkInTime = sessionManager.checkInTime
        if (checkInTime <= 0L) {
            return "00:00:00"
        }
        
        val elapsed = System.currentTimeMillis() - checkInTime
        val totalSeconds = elapsed / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Creates notification for permission revoked state.
     */
    private fun createPermissionRevokedNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_LOCATION)
            .setContentTitle("FieldTrack - Permission Required")
            .setContentText("Location permission was revoked. Tap to fix.")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(getColor(R.color.status_rejected))
            .addAction(createFixPermissionAction())
            .addAction(createStopAction())
            .build()
    }
    
    /**
     * Creates notification for GPS disabled state.
     */
    private fun createLocationDisabledNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_LOCATION)
            .setContentTitle("FieldTrack - GPS Disabled")
            .setContentText("Please enable GPS to continue tracking.")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(getColor(R.color.status_rejected))
            .addAction(createEnableGpsAction())
            .addAction(createStopAction())
            .build()
    }
    
    /**
     * Creates notification for session invalid state.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun createSessionInvalidNotification(reason: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, DashboardActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_LOCATION)
            .setContentTitle("FieldTrack - Session Expired")
            .setContentText("Please log in again to continue tracking.")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(getColor(R.color.status_rejected))
            .addAction(createStopAction())
            .build()
    }
    
    /**
     * Creates stop tracking action button.
     */
    private fun createStopAction(): NotificationCompat.Action {
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stop,
            "Stop",
            stopPendingIntent
        ).build()
    }
    
    /**
     * Creates fix permission action button.
     */
    private fun createFixPermissionAction(): NotificationCompat.Action {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_settings,
            "Fix",
            pendingIntent
        ).build()
    }
    
    /**
     * Creates enable GPS action button.
     */
    private fun createEnableGpsAction(): NotificationCompat.Action {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_settings,
            "Enable GPS",
            pendingIntent
        ).build()
    }
}
