package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.Location
import com.google.gson.annotations.SerializedName

data class LocationDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("accuracy")
    val accuracy: Float,
    @SerializedName("timestamp")
    val timestamp: String
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
        fun fromModel(model: Location): LocationDto = LocationDto(
            id = model.id,
            userId = model.userId,
            latitude = model.latitude,
            longitude = model.longitude,
            accuracy = model.accuracy,
            timestamp = model.timestamp
        )
    }
}
