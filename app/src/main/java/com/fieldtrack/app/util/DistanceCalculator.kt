package com.fieldtrack.app.util

import kotlin.math.*

/**
 * Pure Kotlin distance calculator using Haversine formula.
 * 
 * Phase 2 Beta: Session route distance calculation.
 * 
 * NO Android dependencies - fully testable.
 * Deterministic and thread-safe.
 */
object DistanceCalculator {
    
    private const val EARTH_RADIUS_METERS = 6371000.0  // Earth's radius in meters
    
    /**
     * Calculate distance between two GPS points using Haversine formula.
     * 
     * Formula: https://en.wikipedia.org/wiki/Haversine_formula
     * Accuracy: ±0.5% for distances <1000km
     * 
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Distance in meters
     */
    fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return EARTH_RADIUS_METERS * c
    }
    
    /**
     * Calculate total distance for a route (ordered points).
     * 
     * Sums distances between consecutive points.
     * Returns 0.0 if less than 2 points.
     * 
     * @param points List of GPS points (lat, lon pairs)
     * @return Total distance in meters
     */
    fun calculateRouteDistance(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            val (lat1, lon1) = points[i]
            val (lat2, lon2) = points[i + 1]
            totalDistance += haversineDistance(lat1, lon1, lat2, lon2)
        }
        
        return totalDistance
    }
}
