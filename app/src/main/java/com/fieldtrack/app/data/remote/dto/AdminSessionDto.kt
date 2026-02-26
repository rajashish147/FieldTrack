// v1.4 - Admin Session History
package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.AdminSession
import com.google.gson.annotations.SerializedName

/**
 * DTO for a single row returned by the admin_list_sessions_for_user() RPC.
 *
 * The RPC uses RETURNS TABLE, so PostgREST serialises the result as a
 * plain JSON array — one object per session row. Retrofit maps that array
 * directly to List<AdminSessionDto>; no wrapper object is needed.
 *
 * JSON shape (one element):
 * {
 *   "id":               "550e8400-e29b-41d4-a716-446655440000",
 *   "user_id":          "123e4567-e89b-12d3-a456-426614174000",
 *   "check_in_at":      "2024-02-15T08:30:00+00:00",
 *   "check_out_at":     "2024-02-15T17:15:00+00:00",   // null when OPEN
 *   "duration_minutes": 525,
 *   "status":           "CLOSED"                        // "OPEN" | "CLOSED"
 * }
 *
 * CRITICAL: All timestamps are server-generated UTC strings.
 * The app never sends or modifies timestamps.
 *
 * Nullability contract:
 *   [checkOutAt] is null  → session is still open  (status == "OPEN")
 *   [checkOutAt] non-null → session is closed       (status == "CLOSED")
 *   The two fields are always consistent because the server sets them
 *   atomically inside the check_out() / admin_force_check_out() RPCs.
 */
data class AdminSessionDto(

    /** Session primary key. */
    @SerializedName("id")
    val id: String,

    /** The employee this session belongs to. */
    @SerializedName("user_id")
    val userId: String,

    /**
     * Server-generated check-in timestamp (ISO 8601 UTC).
     * Example: "2024-02-15T08:30:00+00:00"
     */
    @SerializedName("check_in_at")
    val checkInAt: String,

    /**
     * Server-generated check-out timestamp (ISO 8601 UTC).
     * Null when the session is still open.
     * Example: "2024-02-15T17:15:00+00:00"
     */
    @SerializedName("check_out_at")
    val checkOutAt: String?,

    /**
     * Session duration in whole minutes, pre-computed by the server.
     * - CLOSED: (check_out_at - check_in_at), truncated to whole minutes.
     * - OPEN:   (NOW() - check_in_at) at the moment the RPC was called,
     *           truncated to whole minutes.
     * Always >= 0. Never negative.
     */
    @SerializedName("duration_minutes")
    val durationMinutes: Int,

    /**
     * Explicit session state as returned by the server.
     * Exactly one of: "OPEN", "CLOSED".
     */
    @SerializedName("status")
    val status: String

) {
    /**
     * Maps this DTO to the clean [AdminSession] domain model.
     *
     * All fields are passed through without transformation. Timestamp
     * parsing and display formatting are intentionally deferred to the
     * ViewModel / UI layer so that the domain model stays free of both
     * Gson annotations and display concerns.
     */
    fun toModel(): AdminSession = AdminSession(
        id              = id,
        userId          = userId,
        checkInAt       = checkInAt,
        checkOutAt      = checkOutAt,
        durationMinutes = durationMinutes,
        status          = status
    )
}
