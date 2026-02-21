package com.fieldtrack.app.data.repository

import android.util.Log
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.local.dao.LocationDao
import com.fieldtrack.app.data.model.RouteResult
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.util.RouteCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 2 Beta: Session-based route visualization repository.
 * 
 * **READ-ONLY REPOSITORY** - No writes to database or network.
 * 
 * Responsibilities:
 * - Fetch locations by sessionId (read-only)
 * - Map LocationEntity → Location domain model
 * - Delegate route calculation to RouteCalculator
 * - Return RouteResult with polyline and distance
 * 
 * Guarantees:
 * ✅ No database writes
 * ✅ No network calls
 * ✅ No caching or state management
 * ✅ Graceful null handling (null sessionId, empty results)
 * ✅ Thread-safe (uses Dispatchers.IO)
 * 
 * Architecture:
 * - Data Layer: Orchestrates DAO → Domain mapping → Calculation
 * - Pure orchestration: No business logic beyond delegation
 * - Null-safe: Returns null for invalid input or empty results
 * 
 * @param locationDao Read-only access to locations table
 * @param sessionManager User authentication context
 */
class SessionRouteRepository(
    private val locationDao: LocationDao,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "SessionRouteRepository"
    }

    /**
     * Get calculated route for a session.
     * 
     * **READ-ONLY OPERATION** - No database writes.
     * 
     * Process:
     * 1. Validate sessionId (non-null, non-blank)
     * 2. Get userId (from parameter or SessionManager)
     * 3. Query locations by sessionId + userId (RLS-compliant)
     * 4. Map LocationEntity → Location domain model
     * 5. Delegate to RouteCalculator for distance calculation
     * 6. Return RouteResult or null
     * 
     * Graceful degradation:
     * - Returns null if sessionId is null/blank
     * - Returns null if userId is unavailable
     * - Returns null if no locations found
     * - Returns null if RouteCalculator returns null (all points filtered)
     * 
     * Performance:
     * - Uses sessionId index (Phase 2 migration)
     * - Ordered by createdAt ASC (numeric, monotonic)
     * - Expected: <10ms for typical session (50-200 locations)
     * 
     * @param sessionId The attendance session ID to fetch route for
     * @param userId The user ID who owns the locations (for admin viewing employee routes, null = current user)
     * @param accuracyThreshold Maximum GPS accuracy (meters) to include in route
     * @return RouteResult with polyline and distance, or null if unavailable
     */
    suspend fun getSessionRoute(
        sessionId: String?,
        userId: String? = null,
        accuracyThreshold: Float = RouteCalculator.DEFAULT_ACCURACY_THRESHOLD
    ): RouteResult? = withContext(Dispatchers.IO) {
        // Validate sessionId
        if (sessionId.isNullOrBlank()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "getSessionRoute: sessionId is null or blank")
            }
            return@withContext null
        }

        // Get userId (from parameter or SessionManager)
        // Admin fix: Use provided userId (employee's ID) instead of always using SessionManager
        val targetUserId = userId ?: sessionManager.userId
        if (targetUserId == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "getSessionRoute: userId unavailable")
            }
            return@withContext null
        }

        try {
            // Fetch locations (READ-ONLY query)
            // Admin fix: Query with target user's ID (employee's ID for admin)
            val entities = locationDao.getLocationsBySessionId(
                sessionId = sessionId,
                userId = targetUserId
            )

            if (entities.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "getSessionRoute: no locations found for session $sessionId, userId $targetUserId")
                }
                return@withContext null
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "getSessionRoute: found ${entities.size} locations for session $sessionId, userId $targetUserId")
            }

            // Map to domain model
            val locations = entities.map { it.toModel() }

            // Calculate route (pure business logic)
            val result = RouteCalculator.calculateRoute(
                locations = locations,
                accuracyThreshold = accuracyThreshold
            )

            if (result == null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "getSessionRoute: RouteCalculator returned null (all points filtered)")
                }
                return@withContext null
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "getSessionRoute: calculated route with ${result.filteredPointCount} points, " +
                        "distance=${result.formatDistance()}")
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "getSessionRoute: exception for session $sessionId, userId $targetUserId", e)
            null
        }
    }

    /**
     * Check if route data is available for a session.
     * 
     * **READ-ONLY OPERATION** - No database writes.
     * 
     * Lightweight check to determine if a session has location data
     * without performing full route calculation.
     * 
     * Use case: UI can check availability before showing "View Route" button.
     * 
     * @param sessionId The attendance session ID to check
     * @return true if session has at least one location, false otherwise
     */
    suspend fun hasRouteData(sessionId: String?): Boolean = withContext(Dispatchers.IO) {
        if (sessionId.isNullOrBlank()) return@withContext false

        val userId = sessionManager.userId ?: return@withContext false

        try {
            val entities = locationDao.getLocationsBySessionId(
                sessionId = sessionId,
                userId = userId
            )
            entities.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "hasRouteData: exception for session $sessionId", e)
            false
        }
    }
}
