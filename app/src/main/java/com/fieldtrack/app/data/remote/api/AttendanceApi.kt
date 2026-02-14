package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.AttendanceSessionDto
import retrofit2.Response
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
 * RPC functions return 0 or 1 row (never multiple).
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
}
