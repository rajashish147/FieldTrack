package com.fieldtrack.app.data.model

/**
 * Phase 2 Beta: Pure coordinate representation (no UI framework dependencies).
 * 
 * Represents a GPS coordinate without coupling to Google Maps SDK.
 * This allows:
 * - Pure domain logic testing
 * - Reuse in analytics, export, admin tools
 * - Framework-agnostic route calculations
 * 
 * Convert to LatLng in UI layer only.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    /**
     * Validate coordinate ranges.
     */
    fun isValid(): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
    
    /**
     * Convert to Google Maps LatLng (UI layer only).
     */
    fun toLatLng(): com.google.android.gms.maps.model.LatLng {
        return com.google.android.gms.maps.model.LatLng(latitude, longitude)
    }
}
