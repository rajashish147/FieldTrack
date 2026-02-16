package com.fieldtrack.app.data.model

data class Location(
    val id: String,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: String,
    // v1.4 Phase 1 - Session-based location tracking (migration mode - nullable)
    val sessionId: String? = null
)
