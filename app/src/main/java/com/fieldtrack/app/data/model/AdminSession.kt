// v1.4 - Admin Session History
package com.fieldtrack.app.data.model

/**
 * Domain model for an employee attendance session (admin history view).
 *
 * Represents a single attendance session — open or closed — as returned
 * by the admin_list_sessions_for_user() RPC.
 *
 * CRITICAL: This is READ-ONLY from the app's perspective.
 * Sessions are created and closed ONLY via server-side RPC functions.
 * This model is never written back to the network or local database.
 *
 * Timestamps:
 *   Stored as raw ISO 8601 strings (server-generated, UTC).
 *   Parsing to Instant/LocalDateTime is deferred to the ViewModel or UI
 *   layer to keep the domain model free of display concerns.
 *
 * Duration:
 *   [durationMinutes] is pre-computed by the server.
 *   - CLOSED sessions: (check_out_at - check_in_at) in whole minutes.
 *   - OPEN sessions:   (NOW() - check_in_at) in whole minutes at query time.
 *   The value is always >= 0. The app does NOT recompute it.
 *
 * Status:
 *   The server returns exactly "OPEN" or "CLOSED" as a TEXT column.
 *   [isOpen] is the canonical boolean derived from [status] — prefer
 *   this over comparing [checkOutAt] to null, since [status] is the
 *   explicit server contract.
 */
data class AdminSession(
    /** Session primary key (UUID). */
    val id: String,

    /** The employee this session belongs to. Always equals the queried user. */
    val userId: String,

    /**
     * Server-generated check-in timestamp (ISO 8601 UTC).
     * Example: "2024-02-15T08:30:00+00:00"
     */
    val checkInAt: String,

    /**
     * Server-generated check-out timestamp (ISO 8601 UTC).
     * Null when the session is still open (employee is currently checked in).
     */
    val checkOutAt: String?,

    /**
     * Pre-computed session duration in whole minutes (truncated, never rounded up).
     * For OPEN sessions this reflects elapsed time at the moment the RPC was called.
     * Always >= 0.
     */
    val durationMinutes: Int,

    /**
     * Explicit session state string as returned by the server.
     * One of exactly two values: "OPEN" or "CLOSED".
     * Use [isOpen] for boolean checks.
     */
    val status: String
) {
    // ── Computed properties ──────────────────────────────────────────────────

    /**
     * True when the session is still active (employee has not checked out).
     * Derived from [status] — the explicit server contract — rather than
     * null-checking [checkOutAt], so it remains correct even if the server
     * adds additional terminal states in the future.
     */
    val isOpen: Boolean
        get() = status == "OPEN"

    /**
     * True when the session has been closed (employee has checked out).
     * Inverse of [isOpen].
     */
    val isClosed: Boolean
        get() = status == "CLOSED"

    /**
     * Duration formatted as a human-readable string for UI display.
     *
     * Examples:
     *   0  minutes → "< 1 min"
     *   1  minute  → "1 min"
     *   45 minutes → "45 min"
     *   60 minutes → "1 h 0 min"
     *   90 minutes → "1 h 30 min"
     *   125 minutes → "2 h 5 min"
     */
    fun formatDuration(): String {
        if (durationMinutes < 1) return "< 1 min"
        val hours = durationMinutes / 60
        val minutes = durationMinutes % 60
        return when {
            hours == 0 -> "$minutes min"
            minutes == 0 -> "$hours h"
            else -> "$hours h $minutes min"
        }
    }
}
