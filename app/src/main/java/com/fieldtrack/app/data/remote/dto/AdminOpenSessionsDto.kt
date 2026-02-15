// P2.1 - Admin Open Sessions
package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.OpenSession
import com.google.gson.annotations.SerializedName
import java.time.Instant

/**
 * DTO for admin_list_open_sessions() RPC response.
 * 
 * Backend returns JSONB:
 * {
 *   "sessions": [
 *     {
 *       "session_id": "uuid",
 *       "user_id": "uuid",
 *       "name": "John Doe",
 *       "email": "john@example.com",
 *       "check_in_at": "2024-02-15T08:30:00Z",
 *       "created_at": "2024-02-15T08:30:00Z"
 *     }
 *   ]
 * }
 */
data class AdminOpenSessionsResponse(
    @SerializedName("sessions")
    val sessions: List<OpenSessionDto>
)

data class OpenSessionDto(
    @SerializedName("session_id")
    val sessionId: String,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("email")
    val email: String?,
    
    @SerializedName("check_in_at")
    val checkInAt: String,
    
    @SerializedName("created_at")
    val createdAt: String
) {
    fun toModel(): OpenSession = OpenSession(
        sessionId = sessionId,
        userId = userId,
        name = name,
        email = email,
        checkInAt = try {
            Instant.parse(checkInAt)
        } catch (e: Exception) {
            Instant.now() // Fallback to current time on parse error
        }
    )
}
