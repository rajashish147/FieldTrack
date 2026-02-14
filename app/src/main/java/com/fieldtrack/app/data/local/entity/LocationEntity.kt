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
        Index(value = ["userId", "syncState"])
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
    
    // Enhanced sync tracking (replaces isSynced)
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val syncAttempts: Int = 0,
    val lastSyncAttemptAt: Long? = null,
    val syncErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    
    // Keep old field for migration compatibility (will be removed in future version)
    val isSynced: Boolean = false
) {
    fun toModel(): Location = Location(
        id = id,
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp
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
                syncState = SyncState.LOCAL_ONLY,
                syncAttempts = 0,
                lastSyncAttemptAt = null,
                syncErrorMessage = null,
                createdAt = System.currentTimeMillis(),
                isSynced = false
            )
    }
}
