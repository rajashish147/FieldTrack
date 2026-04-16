package com.fieldtrack.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Nested DTO for Supabase foreign key relation expansion.
 * Used when queries include `users(name,email)` in the select clause.
 */
data class UserRelationDto(
    @SerializedName("name")
    val name: String?,
    @SerializedName("email")
    val email: String?
)
