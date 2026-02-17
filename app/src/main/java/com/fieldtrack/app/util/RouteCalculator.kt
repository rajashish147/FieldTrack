package com.fieldtrack.app.util

import com.fieldtrack.app.data.model.GeoPoint
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.model.RouteResult

/**
 * Phase 2 Beta: Calculate route from location data.
 * 
 * Pure business logic - converts Location list to RouteResult.
 * Filters by accuracy and calculates total distance.
 * 
 * Uses GeoPoint (pure coordinates) to avoid coupling to Google Maps SDK.
 */
object RouteCalculator {
    
    /**
     * Default accuracy threshold (meters).
     * Locations with accuracy > threshold are excluded from route.
     * 
     * 100m is reasonable for outdoor GPS tracking.
     */
    const val DEFAULT_ACCURACY_THRESHOLD = 100f
    
    /**
     * Calculate route from locations.
     * 
     * Process:
     * 1. Validate input (non-empty, has sessionId)
     * 2. Filter by accuracy threshold
     * 3. Convert to GeoPoint (pure coordinates)
     * 4. Calculate total distance
     * 5. Return RouteResult with original and filtered counts
     * 
     * @param locations Ordered list of locations (by createdAt ASC)
     * @param accuracyThreshold Maximum accuracy (meters) to include
     * @return RouteResult with polyline and distance, or null if invalid
     */
    fun calculateRoute(
        locations: List<Location>,
        accuracyThreshold: Float = DEFAULT_ACCURACY_THRESHOLD
    ): RouteResult? {
        // Validate input
        if (locations.isEmpty()) return null
        
        // Extract sessionId (must be non-null)
        val sessionId = locations.firstOrNull()?.sessionId
        if (sessionId == null) return null  // No session ID - graceful degradation
        
        // Extract userId
        val userId = locations.firstOrNull()?.userId ?: return null
        
        // Track original count before filtering
        val originalCount = locations.size
        
        // Filter by accuracy threshold
        val filtered = locations.filter { it.accuracy <= accuracyThreshold }
        
        if (filtered.isEmpty()) return null  // All points filtered out
        
        // Convert to GeoPoint (pure coordinates, no UI framework dependency)
        val points = filtered.map { GeoPoint(it.latitude, it.longitude) }
        
        // Calculate distance using Haversine formula
        val pointPairs = filtered.map { Pair(it.latitude, it.longitude) }
        val distance = DistanceCalculator.calculateRouteDistance(pointPairs)
        
        // Extract timestamps
        val startTime = filtered.firstOrNull()?.timestamp
        val endTime = filtered.lastOrNull()?.timestamp
        
        return RouteResult(
            sessionId = sessionId,
            userId = userId,
            points = points,
            distanceMeters = distance,
            originalPointCount = originalCount,
            filteredPointCount = filtered.size,
            startTime = startTime,
            endTime = endTime,
            accuracyThreshold = accuracyThreshold
        )
    }
}
