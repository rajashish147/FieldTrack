// P2.2 - Admin Force Check-Out
package com.fieldtrack.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for admin_force_check_out() RPC response.
 * 
 * Backend returns JSON:
 * {
 *   "session_id": "uuid",
 *   "closed_at": "timestamp",
 *   "target_user_id": "uuid",
 *   "target_user_name": "name",
 *   "check_in_at": "timestamp"
 * }
 */
data class AdminForceCheckOutResponse(
    @SerializedName("session_id")
    val sessionId: String,
    
    @SerializedName("closed_at")
    val closedAt: String,
    
    @SerializedName("target_user_id")
    val targetUserId: String,
    
    @SerializedName("target_user_name")
    val targetUserName: String,
    
    @SerializedName("check_in_at")
    val checkInAt: String
)
