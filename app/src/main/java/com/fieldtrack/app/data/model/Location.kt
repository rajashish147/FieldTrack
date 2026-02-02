package com.fieldtrack.app.data.model

data class Location(
    val id: String,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: String
)
