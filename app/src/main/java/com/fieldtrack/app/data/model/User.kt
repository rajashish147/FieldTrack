package com.fieldtrack.app.data.model

data class User(
    val id: String,
    val name: String,
    val role: String,
    val isCheckedIn: Boolean,
    val isActive: Boolean = false
)
