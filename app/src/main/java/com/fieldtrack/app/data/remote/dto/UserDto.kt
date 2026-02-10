package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.User
import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("role")
    val role: String,
    @SerializedName("is_checked_in")
    val isCheckedIn: Boolean,
    @SerializedName("is_active")
    val isActive: Boolean = false
) {
    fun toModel(): User = User(
        id = id,
        name = name,
        role = role,
        isCheckedIn = isCheckedIn,
        isActive = isActive
    )
}

data class UpdateCheckInDto(
    @SerializedName("is_checked_in")
    val isCheckedIn: Boolean
)

/** DTO for admin to update a user's activation or role status */
data class UpdateUserStatusDto(
    @SerializedName("is_active")
    val isActive: Boolean? = null,
    @SerializedName("role")
    val role: String? = null
)
