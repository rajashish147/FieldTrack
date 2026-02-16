package com.fieldtrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fieldtrack.app.data.model.Location
import java.util.UUID

@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["timestamp"]),
        Index(value = ["syncState"]),
        Index(value = ["userId", "syncState"]),
        Index(value = ["sessionId"])  // v1.4 Phase 2: Index for session-based queries
    ]
)
data class LocationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: String,
    
    // v1.4 - Session-based location tracking (nullable during migration mode)
    // Room migration completed in Phase 2 (database version 5)
    val sessionId: String? = null,
    
    // Enhanced sync tracking (replaces isSynced)
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val syncAttempts: Int = 0,
    val lastSyncAttemptAt: Long? = null,
    val syncErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    
    // Legacy field kept for Room migration compatibility (v1.0 → v1.2)
    // DO NOT USE: Use syncState instead
    // TODO(v1.5): Remove after confirming all users migrated from v1.0/v1.1
    @Deprecated(
        message = "Use syncState instead. This field is kept for migration compatibility only.",
        replaceWith = ReplaceWith("syncState"),
        level = DeprecationLevel.WARNING
    )
    val isSynced: Boolean = false
) {
    fun toModel(): Location = Location(
        id = id,
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp,
        sessionId = sessionId
    )

    companion object {
        fun fromModel(model: Location): LocationEntity =
            LocationEntity(
                id = model.id,
                userId = model.userId,
                latitude = model.latitude,
                longitude = model.longitude,
                accuracy = model.accuracy,
                timestamp = model.timestamp,
                sessionId = model.sessionId,
                syncState = SyncState.LOCAL_ONLY,
                syncAttempts = 0,
                lastSyncAttemptAt = null,
                syncErrorMessage = null,
                createdAt = System.currentTimeMillis(),
                isSynced = false
            )
    }
}
