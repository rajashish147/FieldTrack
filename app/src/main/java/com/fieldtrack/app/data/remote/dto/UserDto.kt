package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.User
import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("profile_image_url")
    val profileImageUrl: String? = null,
    @SerializedName("role")
    val role: String,
    @SerializedName("is_checked_in")
    val isCheckedIn: Boolean,
    @SerializedName("is_active")
    val isActive: Boolean = false,
    @SerializedName("last_synced_at")
    val lastSyncedAt: String? = null
) {
    fun toModel(): User = User(
        id = id,
        name = name,
        email = email,
        phone = phone,
        profileImageUrl = profileImageUrl,
        role = role,
        isCheckedIn = isCheckedIn,
        isActive = isActive,
        lastSyncedAt = lastSyncedAt
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

/** DTO for updating user's profile image URL */
data class UpdateProfileImageDto(
    @SerializedName("profile_image_url")
    val profileImageUrl: String
)
