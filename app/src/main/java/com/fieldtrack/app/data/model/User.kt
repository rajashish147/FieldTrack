package com.fieldtrack.app.data.model

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
    
    fun matchesSearch(query: String): Boolean {
        if (query.isBlank()) return true
        val lowerQuery = query.lowercase()
        return name.lowercase().contains(lowerQuery) ||
                email?.lowercase()?.contains(lowerQuery) == true ||
                phone?.contains(query) == true ||
                id.lowercase().contains(lowerQuery)
    }
}
