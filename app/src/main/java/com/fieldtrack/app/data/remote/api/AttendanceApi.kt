package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.AdminSessionDto
import com.fieldtrack.app.data.remote.dto.AttendanceSessionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API interface for attendance sessions.
 *
 * CRITICAL RULES:
 * - ALL operations use RPC (not REST)
 * - RPC functions handle atomic transactions
 * - App NEVER writes to attendance_sessions table directly
 * - App NEVER updates users.is_checked_in directly
 *
 * RPC functions that use RETURNS TABLE → Response<List<T>>  (plain JSON array)
 * RPC functions that use RETURNS JSONB → Response<WrapperDto> (single JSON object)
 */
interface AttendanceApi {

    /**
     * Get user's open session (if any).
     *
     * RPC: get_open_session()
     * Returns: Empty list if no open session, single-item list if open session exists
     *
     * SQLSTATE Errors:
     * - P0004 (NOT_AUTHENTICATED): No auth token
     *
     * Used for:
     * - App startup sync
     * - Multi-device recovery
     * - Drift detection
     */
    @POST("rest/v1/rpc/get_open_session")
    suspend fun getOpenSession(): Response<List<AttendanceSessionDto>>

    /**
     * Check-in: Atomically create session and update is_checked_in flag.
     *
     * RPC: check_in()
     *
     * Atomic operations (server-side):
     * 1. Verify user is active employee
     * 2. Insert session (check_in_at = NOW())
     * 3. Update users.is_checked_in = true
     * 4. Return created session
     *
     * Returns: Single-item list with created session
     *
     * SQLSTATE Errors:
     * - P0001 (ALREADY_CHECKED_IN): User already has open session
     * - P0003 (ACCOUNT_INACTIVE): User not active or not employee
     * - P0004 (NOT_AUTHENTICATED): No auth token
     *
     * CRITICAL: No request body. RPC handles everything.
     */
    @POST("rest/v1/rpc/check_in")
    suspend fun checkIn(): Response<List<AttendanceSessionDto>>

    /**
     * Check-out: Atomically close session and update is_checked_in flag.
     *
     * RPC: check_out()
     *
     * Atomic operations (server-side):
     * 1. Verify user is active employee
     * 2. Update session (check_out_at = NOW())
     * 3. Update users.is_checked_in = false
     * 4. Return closed session
     *
     * Returns: Single-item list with closed session
     *
     * SQLSTATE Errors:
     * - P0002 (NOT_CHECKED_IN): No open session found
     * - P0003 (ACCOUNT_INACTIVE): User not active or not employee
     * - P0004 (NOT_AUTHENTICATED): No auth token
     *
     * CRITICAL: No request body. RPC handles everything.
     */
    @POST("rest/v1/rpc/check_out")
    suspend fun checkOut(): Response<List<AttendanceSessionDto>>

    // P2.1 - Admin Open Sessions
    /**
     * Admin-only: List all currently open attendance sessions.
     *
     * RPC: admin_list_open_sessions()
     *
     * Returns: JSONB with sessions array containing user info + session details
     *
     * SQLSTATE Errors:
     * - P0005 (ADMIN_REQUIRED): Caller is not admin
     * - P0004 (NOT_AUTHENTICATED): No auth token
     *
     * Security: SECURITY DEFINER function validates admin role server-side.
     */
    @POST("rest/v1/rpc/admin_list_open_sessions")
    suspend fun getAdminOpenSessions(): Response<com.fieldtrack.app.data.remote.dto.AdminOpenSessionsResponse>

    // P2.2 - Admin Force Check-Out
    /**
     * Admin-only: Force check-out a user and close their open session.
     *
     * RPC: admin_force_check_out(target_user_id UUID, reason TEXT DEFAULT NULL)
     *
     * Request body:
     * {
     *   "target_user_id": "uuid",
     *   "reason": "optional text"
     * }
     *
     * Returns: JSON with session details
     *
     * SQLSTATE Errors:
     * - P0005 (ADMIN_REQUIRED): Caller is not admin
     * - P0004 (NOT_AUTHENTICATED): No auth token
     * - P0002 (NOT_CHECKED_IN): User has no open session
     *
     * Security: SECURITY DEFINER function validates admin role server-side.
     * Atomically closes session, updates user status, and logs action.
     */
    @POST("rest/v1/rpc/admin_force_check_out")
    suspend fun adminForceCheckOut(
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<com.fieldtrack.app.data.remote.dto.AdminForceCheckOutResponse>

    // v1.4 - Admin Session History
    /**
     * Admin-only: List ALL sessions (open + closed) for a specific employee.
     *
     * RPC: admin_list_sessions_for_user(target_user_id UUID)
     *
     * Uses RETURNS TABLE — PostgREST serialises the result as a plain JSON
     * array, one object per session row. No wrapper object is needed.
     *
     * Request body:
     * {
     *   "target_user_id": "123e4567-e89b-12d3-a456-426614174000"
     * }
     *
     * Returns: JSON array of session rows, ordered by check_in_at DESC.
     * Returns an empty array (not an error) when the employee has no sessions.
     *
     * Response row shape:
     * {
     *   "id":               "uuid",
     *   "user_id":          "uuid",
     *   "check_in_at":      "2024-02-15T08:30:00+00:00",
     *   "check_out_at":     "2024-02-15T17:15:00+00:00",  // null when OPEN
     *   "duration_minutes": 525,
     *   "status":           "CLOSED"                       // "OPEN" | "CLOSED"
     * }
     *
     * SQLSTATE Errors:
     * - P0004 (NOT_AUTHENTICATED): No valid JWT / auth token
     * - P0005 (ADMIN_REQUIRED):    Caller does not have role = 'admin'
     * - P0006 (INVALID_INPUT):     target_user_id was passed as NULL
     *
     * Security: SECURITY DEFINER function validates admin role server-side.
     * A non-admin authenticated user receives a 400 with SQLSTATE P0005 in
     * the error body — identical behaviour to admin_list_open_sessions().
     */
    @POST("rest/v1/rpc/admin_list_sessions_for_user")
    suspend fun adminListSessionsForUser(
        @Body request: Map<String, String>
    ): Response<List<AdminSessionDto>>
}
