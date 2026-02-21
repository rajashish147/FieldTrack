package com.fieldtrack.app.ui.route

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.RouteResult
import com.fieldtrack.app.data.repository.SessionRouteRepository
import kotlinx.coroutines.launch

/**
 * Phase 2 Beta: ViewModel for session route visualization.
 * 
 * **READ-ONLY PRESENTATION LAYER** - No writes, no side effects.
 * 
 * Responsibilities:
 * - Accept sessionId as input
 * - Fetch route data via SessionRouteRepository
 * - Expose immutable UI state (Loading, NoRouteData, RouteAvailable, Error)
 * - Handle null RouteResult gracefully
 * 
 * Architecture:
 * - MVVM pattern with LiveData
 * - Lifecycle-safe (uses viewModelScope)
 * - Configuration-safe (survives rotation)
 * - Thread-safe (coroutines on IO dispatcher via repository)
 * 
 * UI State Flow:
 * Initial → Loading → (RouteAvailable | NoRouteData | Error)
 * 
 * @param sessionRouteRepository Read-only repository for route data
 */
class RouteViewModel(
    private val sessionRouteRepository: SessionRouteRepository
) : ViewModel() {

    // ── UI State ──

    private val _routeState = MutableLiveData<RouteState>(RouteState.Initial)
    val routeState: LiveData<RouteState> = _routeState

    // ── Public API ──

    /**
     * Load route for a session.
     * 
     * State transitions:
     * - Initial → Loading
     * - Loading → RouteAvailable (if route data exists)
     * - Loading → NoRouteData (if no locations or all filtered)
     * - Loading → Error (if exception occurs)
     * 
     * Idempotent: Can be called multiple times (e.g., retry after error).
     * 
     * @param sessionId The attendance session ID to load route for
     * @param userId The user ID who owns the locations (for admin viewing employee routes, null = current user)
     * @param accuracyThreshold Maximum GPS accuracy (meters) to include
     */
    fun loadRoute(
        sessionId: String?,
        userId: String? = null,
        accuracyThreshold: Float = com.fieldtrack.app.util.RouteCalculator.DEFAULT_ACCURACY_THRESHOLD
    ) {
        // Validate input
        if (sessionId.isNullOrBlank()) {
            _routeState.value = RouteState.NoRouteData("Invalid session ID")
            return
        }

        // Transition to loading
        _routeState.value = RouteState.Loading

        viewModelScope.launch {
            try {
                // Fetch route (repository handles threading)
                // Admin fix: Pass userId to repository (employee's ID for admin)
                val result = sessionRouteRepository.getSessionRoute(
                    sessionId = sessionId,
                    userId = userId,
                    accuracyThreshold = accuracyThreshold
                )

                // Handle result
                _routeState.value = if (result != null) {
                    RouteState.RouteAvailable(result)
                } else {
                    RouteState.NoRouteData("No route data available for this session")
                }

            } catch (e: Exception) {
                // Production logging: Structured error for crash monitoring
                // Security: Don't interpolate exception messages (may contain sensitive data)
                android.util.Log.e(
                    "RouteViewModel",
                    "Failed to load route for session: $sessionId, userId: $userId",
                    e
                )
                
                // Recoverable error (user can retry)
                _routeState.value = RouteState.Error(
                    message = e.message ?: "Failed to load route",
                    isRecoverable = true
                )
            }
        }
    }

    /**
     * Retry loading route after error.
     * 
     * Convenience method that re-invokes loadRoute with last parameters.
     * UI should store sessionId and userId, then call this on retry button click.
     * 
     * @param sessionId The attendance session ID to retry
     * @param userId The user ID who owns the locations (for admin viewing employee routes, null = current user)
     * @param accuracyThreshold Maximum GPS accuracy (meters) to include
     */
    fun retry(
        sessionId: String?,
        userId: String? = null,
        accuracyThreshold: Float = com.fieldtrack.app.util.RouteCalculator.DEFAULT_ACCURACY_THRESHOLD
    ) {
        loadRoute(sessionId, userId, accuracyThreshold)
    }

    /**
     * Reset state to initial.
     * 
     * Use case: User navigates away from route view.
     */
    fun reset() {
        _routeState.value = RouteState.Initial
    }
}

/**
 * UI state for route visualization.
 * 
 * Sealed class ensures exhaustive when() handling in UI.
 * Immutable data classes prevent accidental mutation.
 */
sealed class RouteState {
    
    /**
     * Initial state before any load attempt.
     */
    data object Initial : RouteState()
    
    /**
     * Loading route data from repository.
     * 
     * UI should show progress indicator.
     */
    data object Loading : RouteState()
    
    /**
     * Route data available and ready for visualization.
     * 
     * UI should render map with polyline and distance summary.
     * 
     * @param route Calculated route with polyline points and distance
     */
    data class RouteAvailable(val route: RouteResult) : RouteState()
    
    /**
     * No route data available for session.
     * 
     * Reasons:
     * - Session has no locations (pre-Phase 3 session)
     * - All locations filtered out (poor GPS accuracy)
     * - Invalid sessionId
     * 
     * UI should show empty state with explanation.
     * 
     * @param message User-friendly explanation
     */
    data class NoRouteData(val message: String) : RouteState()
    
    /**
     * Error occurred while loading route.
     * 
     * UI should show error message with optional retry button.
     * 
     * @param message User-friendly error message
     * @param isRecoverable Whether user can retry (always true for network errors)
     */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true
    ) : RouteState()
}
