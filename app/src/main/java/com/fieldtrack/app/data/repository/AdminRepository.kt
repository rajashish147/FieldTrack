package com.fieldtrack.app.data.repository

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
    private val userApi: UserApi
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
}
