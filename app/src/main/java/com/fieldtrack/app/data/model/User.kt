package com.fieldtrack.app.data.model

import android.graphics.Color

data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: String,
    val isCheckedIn: Boolean,
    val isActive: Boolean = false,
    val lastSyncedAt: String? = null
) {
    fun getFormattedLastSync(): String {
        if (lastSyncedAt.isNullOrBlank()) return "Never synced"
        
        return try {
            val syncTime = java.time.Instant.parse(lastSyncedAt)
            val syncDateTime = java.time.LocalDateTime.ofInstant(syncTime, java.time.ZoneId.systemDefault())
            val now = java.time.LocalDateTime.now()
            
            val syncDate = syncDateTime.toLocalDate()
            val today = now.toLocalDate()
            val yesterday = today.minusDays(1)
            
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
            val timeStr = syncDateTime.format(timeFormatter)
            
            when (syncDate) {
                today -> "Today, $timeStr"
                yesterday -> "Yesterday, $timeStr"
                else -> {
                    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
                    syncDateTime.format(dateFormatter)
                }
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get sync freshness indicator for admin trust signals.
     * Shows how recently the employee synced their location.
     */
    fun getSyncFreshnessIndicator(): String {
        if (lastSyncedAt.isNullOrBlank()) return "Never synced"
        
        return try {
            val syncTime = java.time.Instant.parse(lastSyncedAt)
            val now = java.time.Instant.now()
            val minutesAgo = java.time.Duration.between(syncTime, now).toMinutes()
            
            when {
                minutesAgo < 5 -> "Just now"
                minutesAgo < 60 -> "$minutesAgo mins ago"
                minutesAgo < 1440 -> "${minutesAgo / 60} hours ago"
                else -> "${minutesAgo / 1440} days ago"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get sync health status for admin monitoring.
     * Categorizes sync freshness for quick health assessment.
     */
    fun getSyncHealthStatus(): SyncHealthStatus {
        if (lastSyncedAt.isNullOrBlank()) return SyncHealthStatus.NEVER_SYNCED
        
        return try {
            val syncTime = java.time.Instant.parse(lastSyncedAt)
            val now = java.time.Instant.now()
            val hoursAgo = java.time.Duration.between(syncTime, now).toHours()
            
            when {
                hoursAgo < 1 -> SyncHealthStatus.HEALTHY
                hoursAgo < 24 -> SyncHealthStatus.STALE
                else -> SyncHealthStatus.VERY_STALE
            }
        } catch (e: Exception) {
            SyncHealthStatus.UNKNOWN
        }
    }
    
    /**
     * Get color for sync health indicator.
     * Visual feedback for admin to quickly identify sync issues.
     */
    fun getSyncHealthColor(): Int {
        return when (getSyncHealthStatus()) {
            SyncHealthStatus.HEALTHY -> Color.parseColor("#4CAF50")      // Green
            SyncHealthStatus.STALE -> Color.parseColor("#FF9800")        // Orange
            SyncHealthStatus.VERY_STALE -> Color.parseColor("#F44336")   // Red
            SyncHealthStatus.NEVER_SYNCED -> Color.parseColor("#9E9E9E") // Gray
            SyncHealthStatus.UNKNOWN -> Color.parseColor("#9E9E9E")      // Gray
        }
    }
    
    fun matchesSearch(query: String): Boolean {
        if (query.isBlank()) return true
        val lowerQuery = query.lowercase()
        return name.lowercase().contains(lowerQuery) ||
                email?.lowercase()?.contains(lowerQuery) == true ||
                phone?.contains(query) == true ||
                id.lowercase().contains(lowerQuery)
    }
}

/**
 * Sync health status categories for admin monitoring.
 */
enum class SyncHealthStatus {
    HEALTHY,        // Synced within last hour
    STALE,          // Synced 1-24 hours ago
    VERY_STALE,     // Synced >24 hours ago
    NEVER_SYNCED,   // No sync yet
    UNKNOWN         // Error parsing timestamp
}
