package com.fieldtrack.app.service

/**
 * Represents the current state of the LocationTrackingService.
 * 
 * This sealed class enables deterministic state management and ensures
 * the service always has an observable, well-defined state.
 * 
 * State transitions:
 * - Active → PermissionRevoked (permission revoked during tracking)
 * - Active → LocationDisabled (GPS disabled during tracking)
 * - Active → SessionInvalid (session expired/cleared during tracking)
 * - Active → Stopped (user checkout or explicit stop)
 * - PermissionRevoked → Stopped (immediate)
 * - LocationDisabled → Stopped (immediate)
 * - SessionInvalid → Stopped (immediate)
 * 
 * @see StopReason for detailed stop reasons
 */
sealed class ServiceState {
    /**
     * Service is actively tracking location.
     * 
     * In this state:
     * - Location updates are being received
     * - Monitors are registered and active
     * - WakeLock is held
     * - Foreground notification is shown
     * 
     * @property lastLocationTime Timestamp of the last successful location update (milliseconds since epoch)
     */
    data class Active(val lastLocationTime: Long) : ServiceState()
    
    /**
     * Location permission was revoked while service was running.
     * 
     * This is a terminal error state. The service will stop immediately
     * and clear the check-in flag to prevent auto-restart.
     * 
     * Detection method: Polling every 30 seconds
     * Time to stop: < 30 seconds
     * 
     * @property timestamp When the permission revocation was detected (milliseconds since epoch)
     */
    data class PermissionRevoked(val timestamp: Long) : ServiceState()
    
    /**
     * GPS provider was disabled while service was running.
     * 
     * This is a terminal error state. The service will stop immediately
     * and clear the check-in flag to prevent auto-restart.
     * 
     * Detection method: PROVIDERS_CHANGED_ACTION broadcast
     * Time to stop: < 5 seconds
     * 
     * @property timestamp When the GPS disable was detected (milliseconds since epoch)
     */
    data class LocationDisabled(val timestamp: Long) : ServiceState()
    
    /**
     * Authentication session became invalid (expired, cleared, or user logged out).
     * 
     * This is a terminal error state. The service will stop immediately.
     * The check-in flag is NOT cleared to allow UI to handle logout flow.
     * 
     * Detection method: SharedPreferences listener on SessionManager
     * Time to stop: < 60 seconds
     * 
     * @property reason Detailed reason for session invalidation (e.g., "Token expired", "User ID cleared")
     * @property timestamp When the session invalidation was detected (milliseconds since epoch)
     */
    data class SessionInvalid(val reason: String, val timestamp: Long) : ServiceState()
    
    /**
     * Service has stopped.
     * 
     * This is the terminal state. The service is no longer running.
     * All resources have been released and monitors unregistered.
     * 
     * @property reason Why the service stopped (see StopReason enum)
     * @property timestamp When the service stopped (milliseconds since epoch)
     */
    data class Stopped(val reason: StopReason, val timestamp: Long) : ServiceState()
}

/**
 * Enumeration of all possible reasons for service termination.
 * 
 * This enables analytics, debugging, and user feedback about why tracking stopped.
 * Each reason maps to a specific failure mode or user action.
 * 
 * Usage:
 * ```kotlin
 * stopTrackingWithReason(StopReason.PERMISSION_REVOKED)
 * ```
 * 
 * The stop reason is persisted to SharedPreferences for UI observation:
 * ```kotlin
 * val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
 * val reason = prefs.getString("stop_reason", null)
 * ```
 */
enum class StopReason {
    /**
     * User explicitly checked out via the UI.
     * 
     * This is a normal, user-initiated stop.
     * The check-in flag is cleared by the UI, not the service.
     */
    USER_CHECKOUT,
    
    /**
     * User logged out via the UI.
     * 
     * This is a normal, user-initiated stop.
     * The check-in flag is NOT cleared to allow UI to handle logout flow.
     */
    USER_LOGOUT,
    
    /**
     * Location permission was revoked while service was running.
     * 
     * This is an error condition requiring user action.
     * The check-in flag is cleared to prevent auto-restart.
     * 
     * User action: Grant location permission in Settings
     */
    PERMISSION_REVOKED,
    
    /**
     * GPS provider was disabled while service was running.
     * 
     * This is an error condition requiring user action.
     * The check-in flag is cleared to prevent auto-restart.
     * 
     * User action: Enable GPS in Settings
     */
    LOCATION_DISABLED,
    
    /**
     * JWT token expired while service was running.
     * 
     * This is an error condition requiring user action.
     * The check-in flag is NOT cleared to allow UI to handle token refresh.
     * 
     * User action: Log in again
     */
    SESSION_EXPIRED,
    
    /**
     * Session was cleared (logout or token refresh failure).
     * 
     * This is an error condition, usually due to logout or auth failure.
     * The check-in flag is NOT cleared to allow UI to handle session clearing.
     * 
     * User action: Log in again
     */
    SESSION_CLEARED,
    
    /**
     * Service was killed by the Android system.
     * 
     * This should not occur in production due to START_NOT_STICKY on validation failure.
     * If this occurs, it indicates a bug or unexpected system behavior.
     * 
     * User action: None (service should not restart)
     */
    SYSTEM_KILLED,
    
    /**
     * Unknown reason (should not occur in production).
     * 
     * This is a fallback value that should never be used.
     * If this occurs, it indicates a bug in the service implementation.
     * 
     * User action: Report bug
     */
    UNKNOWN
}
