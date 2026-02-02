package com.fieldtrack.app.data.model

data class Report(
    val id: String,
    val userId: String,
    val date: String,
    val summary: String,
    val optionalImageUrl: String?
)
