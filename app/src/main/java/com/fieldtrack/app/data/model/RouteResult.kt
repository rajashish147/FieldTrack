package com.fieldtrack.app.data.model

/**
 * Phase 2 Beta: Result of session route calculation.
 * 
 * Immutable data class representing a calculated route.
 * Contains polyline points, distance, and metadata.
 * 
 * Uses GeoPoint (pure coordinates) instead of LatLng to avoid
 * coupling domain model to Google Maps SDK.
 */
data class RouteResult(
    val sessionId: String,
    val userId: String,
    val points: List<GeoPoint>,  // Pure coordinates (no UI framework dependency)
    val distanceMeters: Double,  // Total distance calculated
    val originalPointCount: Int,  // Total GPS points captured
    val filteredPointCount: Int,  // Points included after accuracy filter
    val startTime: String?,  // ISO 8601 timestamp of first point
    val endTime: String?,  // ISO 8601 timestamp of last point
    val accuracyThreshold: Float  // Accuracy filter used (meters)
) {
    /**
     * Distance in kilometers (for display).
     */
    val distanceKm: Double
        get() = distanceMeters / 1000.0
    
    /**
     * Formatted distance string for UI display.
     * 
     * Examples:
     * - 450 m
     * - 1.25 km
     * - 12.50 km
     */
    fun formatDistance(): String {
        return if (distanceMeters < 1000) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format("%.2f km", distanceKm)
        }
    }
    
    /**
     * Check if route is empty (no points).
     */
    val isEmpty: Boolean
        get() = points.isEmpty()
    
    /**
     * Check if route has sufficient data for visualization.
     */
    val isValid: Boolean
        get() = points.size >= 2
    
    /**
     * Number of points discarded due to poor GPS accuracy.
     */
    val discardedPointCount: Int
        get() = originalPointCount - filteredPointCount
    
    /**
     * Percentage of points retained after filtering.
     */
    val retentionRate: Double
        get() = if (originalPointCount > 0) {
            (filteredPointCount.toDouble() / originalPointCount) * 100
        } else {
            0.0
        }
}
