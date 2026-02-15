// P2.1 - Admin Open Sessions
package com.fieldtrack.app.data.model

import java.time.Duration
import java.time.Instant

/**
 * Domain model for an open attendance session (admin view).
 * 
 * Represents a currently active session with user information.
 * Used by admin to monitor who is currently checked in.
 */
data class OpenSession(
    val sessionId: String,
    val userId: String,
    val name: String,
    val email: String?,
    val checkInAt: Instant
) {
    /**
     * Calculate session duration from check-in time to now.
     */
    fun getDuration(): Duration {
        return Duration.between(checkInAt, Instant.now())
    }
    
    /**
     * Format duration as human-readable string.
     * Examples: "2h 15m", "45m", "Just now"
     */
    fun getFormattedDuration(): String {
        val duration = getDuration()
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "Just now"
        }
    }
    
    /**
     * Get check-in time formatted for display.
     * Example: "Today, 8:30 AM"
     */
    fun getFormattedCheckInTime(): String {
        return try {
            val checkInDateTime = java.time.LocalDateTime.ofInstant(
                checkInAt, 
                java.time.ZoneId.systemDefault()
            )
            val now = java.time.LocalDateTime.now()
            
            val checkInDate = checkInDateTime.toLocalDate()
            val today = now.toLocalDate()
            val yesterday = today.minusDays(1)
            
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
            val timeStr = checkInDateTime.format(timeFormatter)
            
            when (checkInDate) {
                today -> "Today, $timeStr"
                yesterday -> "Yesterday, $timeStr"
                else -> {
                    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a")
                    checkInDateTime.format(dateFormatter)
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
