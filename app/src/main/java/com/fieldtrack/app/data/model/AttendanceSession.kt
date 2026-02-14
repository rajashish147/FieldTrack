package com.fieldtrack.app.data.model

/**
 * Domain model for attendance session.
 * Represents server-side session state (source of truth).
 * 
 * CRITICAL: This is READ-ONLY from app perspective.
 * Sessions are created/closed ONLY via RPC functions.
 */
data class AttendanceSession(
    val id: String,
    val userId: String,
    val checkInAt: String,      // ISO 8601 UTC timestamp
    val checkOutAt: String?,    // null = session is open
    val createdAt: String,
    val updatedAt: String
) {
    /**
     * Check if session is currently open (not checked out yet).
     */
    val isOpen: Boolean get() = checkOutAt == null
}
