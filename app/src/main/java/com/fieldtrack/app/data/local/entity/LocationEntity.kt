package com.fieldtrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fieldtrack.app.data.model.Location
import java.util.UUID

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: String,
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
        fun fromModel(model: Location, synced: Boolean = false): LocationEntity =
            LocationEntity(
                id = model.id,
                userId = model.userId,
                latitude = model.latitude,
                longitude = model.longitude,
                accuracy = model.accuracy,
                timestamp = model.timestamp,
                isSynced = synced
            )
    }
}
