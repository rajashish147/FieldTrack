// v1.4 - Admin Session History
package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.model.AdminSession
import com.fieldtrack.app.data.model.OpenSession
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.api.UserApi
import android.util.Log
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.model.UserRole
import com.fieldtrack.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Admin-only repository for employee management.
 * All operations are enforced by Supabase RLS — non-admin callers
 * will receive a 403 from the server.
 */
class AdminRepository(
    private val userApi: UserApi,
    // P2.1 - Admin Open Sessions: Inject AttendanceApi for RPC calls
    private val attendanceApi: com.fieldtrack.app.data.remote.api.AttendanceApi
) {

    // ── Employee Management ──────────────────────────────────────────────────

    /** Get all users with role = employee */
    suspend fun getAllEmployees(): NetworkResult<List<User>> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.getUsersByRole(role = "eq.${UserRole.EMPLOYEE.name}")
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!.map { it.toModel() })
            } else {
                NetworkResult.Error("Failed to load employees", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Network error")
        }
    }

    /** Get employees pending approval (is_active = false) */
    suspend fun getPendingEmployees(): NetworkResult<List<User>> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.getUsersByActiveStatus(
                role = "eq.${UserRole.EMPLOYEE.name}",
                isActive = "eq.false"
            )
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!.map { it.toModel() })
            } else {
                NetworkResult.Error("Failed to load pending employees", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Network error")
        }
    }

    /** Approve an employee (set is_active = true) */
    suspend fun approveEmployee(userId: String): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.updateUserStatus(
                id = "eq.$userId",
                body = mapOf("is_active" to true)
            )
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to approve employee", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Network error")
        }
    }

    /** Deactivate an employee (set is_active = false) */
    suspend fun deactivateEmployee(userId: String): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.updateUserStatus(
                id = "eq.$userId",
                body = mapOf("is_active" to false)
            )
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error("Failed to deactivate employee", response.code())
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Network error")
        }
    }

    // ── P2.1 - Admin Open Sessions ───────────────────────────────────────────

    /**
     * Get all currently open attendance sessions (admin-only).
     *
     * Calls admin_list_open_sessions() RPC which validates admin role server-side.
     *
     * Returns:
     * - Success: List of open sessions with user info
     * - Error: SQLSTATE-based error messages
     *   - P0005: Admin access required (403)
     *   - P0004: Authentication required (401)
     */
    suspend fun getOpenSessions(): NetworkResult<List<OpenSession>> = withContext(Dispatchers.IO) {
        try {
            val response = attendanceApi.getAdminOpenSessions()
            if (response.isSuccessful && response.body() != null) {
                val sessions = response.body()!!.sessions.map { it.toModel() }
                NetworkResult.Success(sessions)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                when {
                    errorBody.contains("P0005") ->
                        NetworkResult.Error("Admin access required", 403)

                    errorBody.contains("P0004") ->
                        NetworkResult.Error("Authentication required", 401)

                    else ->
                        NetworkResult.Error("Failed to load open sessions", response.code())
                }
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Network error")
        }
    }

    // ── P2.2 - Admin Force Check-Out ─────────────────────────────────────────

    /**
     * Force check-out a user and close their open session (admin-only).
     *
     * Calls admin_force_check_out() RPC which validates admin role server-side.
     * Atomically closes session, updates user status, and logs action to audit table.
     *
     * @param userId Target user's UUID
     * @param reason Optional reason for force check-out (for audit log)
     *
     * Returns:
     * - Success: Unit (operation completed)
     * - Error: SQLSTATE-based error messages
     *   - P0005: Admin access required (403)
     *   - P0004: Authentication required (401)
     *   - P0002: User not checked in (404)
     */
    suspend fun forceCheckOut(userId: String, reason: String?): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = mutableMapOf<String, Any?>("target_user_id" to userId)
                if (reason != null) {
                    body["reason"] = reason
                }

                val response = attendanceApi.adminForceCheckOut(body)
                if (response.isSuccessful) {
                    NetworkResult.Success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    when {
                        errorBody.contains("P0005") ->
                            NetworkResult.Error("Admin access required", 403)

                        errorBody.contains("P0004") ->
                            NetworkResult.Error("Authentication required", 401)

                        errorBody.contains("P0002") ->
                            NetworkResult.Error("User not checked in", 404)

                        else ->
                            NetworkResult.Error("Failed to force check-out", response.code())
                    }
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Network error")
            }
        }

    // ── v1.4 - Admin Session History ─────────────────────────────────────────

    /**
     * Get full attendance session history (open + closed) for a specific employee.
     *
     * Calls admin_list_sessions_for_user() RPC which validates admin role server-side.
     * Returns sessions ordered newest-first (check_in_at DESC).
     *
     * An employee with no sessions returns Success with an empty list — this is
     * not an error condition. The RPC returns an empty JSON array in that case.
     *
     * @param userId Target employee's UUID. Must be a valid non-empty UUID string.
     *
     * Returns:
     * - Success: List of all sessions for the employee (may be empty)
     * - Error: SQLSTATE-based error messages
     *   - P0004: Authentication required (401) — no valid JWT
     *   - P0005: Admin access required (403)   — caller is not an admin
     *   - P0006: Invalid input (400)           — userId was null at the RPC layer
     */
    suspend fun getSessionsForUser(userId: String): NetworkResult<List<AdminSession>> =
        withContext(Dispatchers.IO) {
            try {
                val request = mapOf("target_user_id" to userId)
                val response = attendanceApi.adminListSessionsForUser(request)

                if (response.isSuccessful && response.body() != null) {
                    val sessions = response.body()!!.map { it.toModel() }
                    NetworkResult.Success(sessions)
                } else {
                    // OkHttp response bodies are one-shot streams — read once
                    // and reuse the string for all checks below.
                    val errorBody = response.errorBody()?.string() ?: ""

                    // Debug-only: log the raw Supabase/PostgREST error payload so
                    // developers can see exactly what the server returned.
                    // This guard ensures the body never reaches production logs.
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "adminListSessionsForUser HTTP ${response.code()}: $errorBody")
                    }

                    when {
                        // ── PostgreSQL SQLSTATE codes raised inside the RPC ───────────
                        // These are emitted by RAISE EXCEPTION ... USING ERRCODE = 'Pxxx'
                        // inside the function body and forwarded by PostgREST as HTTP 400.
                        errorBody.contains("P0005") ->
                            NetworkResult.Error("Admin access required", 403)

                        errorBody.contains("P0004") ->
                            NetworkResult.Error("Authentication required", 401)

                        errorBody.contains("P0006") ->
                            NetworkResult.Error("Invalid user ID", 400)

                        // ── PostgREST-layer errors (occur before the function runs) ───
                        //
                        // PGRST202 — function not found in PostgREST schema cache.
                        //   Causes: SQL not yet deployed to the live Supabase project,
                        //   or the PostgREST schema cache is stale after a recent deploy.
                        //
                        //   Fix:
                        //     1. Open the Supabase SQL editor.
                        //     2. Run supabase/admin_list_sessions_for_user_rpc.sql
                        //        (must include the #variable_conflict use_column directive
                        //        and the STABLE volatility marker added in v1.4).
                        //     3. If the function still isn't found, force a cache reload:
                        //            NOTIFY pgrst, 'reload schema';
                        errorBody.contains("PGRST202") ->
                            NetworkResult.Error(
                                "Session history unavailable — RPC not deployed (PGRST202)",
                                response.code()
                            )

                        // ── PostgreSQL engine errors from inside the function body ────
                        //
                        // 42702 — ambiguous_column: a column reference inside the
                        //   RETURN QUERY SELECT is ambiguous because RETURNS TABLE creates
                        //   output variables (id, user_id, …) with the same names as the
                        //   table columns being SELECTed.
                        //
                        //   Root cause: the deployed SQL is missing the
                        //   #variable_conflict use_column directive at the top of the
                        //   function body.
                        //
                        //   Fix: redeploy supabase/admin_list_sessions_for_user_rpc.sql
                        //   which now contains the directive.
                        errorBody.contains("42702") ->
                            NetworkResult.Error(
                                "Session history unavailable — SQL ambiguity (42702); " +
                                        "redeploy admin_list_sessions_for_user_rpc.sql",
                                response.code()
                            )

                        // 22P02 — PostgreSQL could not cast the userId string to UUID.
                        //   Indicates the userId argument was blank or malformed.
                        errorBody.contains("22P02") ->
                            NetworkResult.Error("Invalid employee ID format", 400)

                        else ->
                            NetworkResult.Error(
                                "Failed to load session history (HTTP ${response.code()})",
                                response.code()
                            )
                    }
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Network error")
            }
        }

    companion object {
        private const val TAG = "AdminRepository"
    }
}
