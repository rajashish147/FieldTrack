package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.model.OpenSession
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.remote.dto.UpdateUserStatusDto
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

    /** Get all users with role = employee */
    suspend fun getAllEmployees(): NetworkResult<List<User>> = withContext(Dispatchers.IO) {
        try {
            val response = userApi.getUsersByRole(role = "eq.${Constants.ROLE_EMPLOYEE}")
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
                role = "eq.${Constants.ROLE_EMPLOYEE}",
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
                body = UpdateUserStatusDto(isActive = true)
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
                body = UpdateUserStatusDto(isActive = false)
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

    // P2.1 - Admin Open Sessions
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
                // Parse SQLSTATE errors from response body
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

    // P2.2 - Admin Force Check-Out
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
    suspend fun forceCheckOut(userId: String, reason: String?): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            // Build request body
            val body = mutableMapOf<String, Any?>(
                "target_user_id" to userId
            )
            if (reason != null) {
                body["reason"] = reason
            }
            
            val response = attendanceApi.adminForceCheckOut(body)
            if (response.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                // Parse SQLSTATE errors from response body
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
}
