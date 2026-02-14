package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.AttendanceSession
import com.google.gson.annotations.SerializedName

/**
 * DTO for attendance session from Supabase RPC responses.
 * 
 * CRITICAL: All timestamps are server-generated.
 * App NEVER sends timestamps - RPC functions handle everything.
 */
data class AttendanceSessionDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("check_in_at")
    val checkInAt: String,
    
    @SerializedName("check_out_at")
    val checkOutAt: String?,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("updated_at")
    val updatedAt: String
) {
    fun toModel(): AttendanceSession = AttendanceSession(
        id = id,
        userId = userId,
        checkInAt = checkInAt,
        checkOutAt = checkOutAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
