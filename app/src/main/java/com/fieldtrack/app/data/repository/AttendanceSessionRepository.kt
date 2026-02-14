package com.fieldtrack.app.data.repository

import android.util.Log
import com.fieldtrack.app.data.model.AttendanceSession
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.AttendanceApi
import com.fieldtrack.app.data.remote.dto.AttendanceSessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Repository for attendance session operations.
 * 
 * CRITICAL ARCHITECTURE:
 * - Uses Supabase RPC functions ONLY
 * - Session existence = source of truth
 * - is_checked_in is DERIVED from session state
 * - App NEVER writes flags or sessions directly
 * - Drift repair is SILENT (no user notification)
 */
class AttendanceSessionRepository(
    private val attendanceApi: AttendanceApi,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "AttendanceSessionRepo"
    }

    /**
     * Get user's open session from server via RPC.
     * Returns null if no session exists or on error.
     */
    suspend fun getOpenSession(): AttendanceSession? = withContext(Dispatchers.IO) {
        try {
            val response = attendanceApi.getOpenSession()
            if (!response.isSuccessful) {
                logError("getOpenSession", response.code(), response.errorBody()?.string())
                return@withContext null
            }

            val sessions = response.body()
            when {
                sessions.isNullOrEmpty() -> {
                    Log.d(TAG, "getOpenSession: no open session")
                    null
                }
                sessions.size == 1 -> {
                    val session = sessions.first().toModel()
                    Log.d(TAG, "getOpenSession: found session ${session.id}")
                    session
                }
                else -> {
                    Log.e(TAG, "getOpenSession: multiple sessions returned (${sessions.size})")
                    sessions.first().toModel()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getOpenSession: exception", e)
            null
        }
    }

    /**
     * Check-in via RPC. Returns CheckInResult with outcome.
     */
    suspend fun checkIn(): CheckInResult = withContext(Dispatchers.IO) {
        executeRpcCall(
            apiCall = { attendanceApi.checkIn() },
            operationName = "checkIn",
            onSuccess = { session ->
                Log.i(TAG, "checkIn: success, sessionId=${session.id}")
                CheckInResult.Success(session)
            },
            errorMapping = mapOf(
                "P0001" to { CheckInResult.AlreadyCheckedIn },
                "P0003" to { CheckInResult.AccountInactive },
                "P0004" to { CheckInResult.Error("Not authenticated") }
            ),
            onNetworkError = { CheckInResult.NetworkError(it) },
            onUnknownError = { CheckInResult.Error(it) }
        )
    }

    /**
     * Check-out via RPC. Returns CheckOutResult with outcome.
     */
    suspend fun checkOut(): CheckOutResult = withContext(Dispatchers.IO) {
        executeRpcCall(
            apiCall = { attendanceApi.checkOut() },
            operationName = "checkOut",
            onSuccess = { session ->
                Log.i(TAG, "checkOut: success, sessionId=${session.id}")
                CheckOutResult.Success(session)
            },
            errorMapping = mapOf(
                "P0002" to { CheckOutResult.NotCheckedIn },
                "P0003" to { CheckOutResult.AccountInactive },
                "P0004" to { CheckOutResult.NotAuthenticated }
            ),
            onNetworkError = { CheckOutResult.NetworkError(it) },
            onUnknownError = { CheckOutResult.Error(it) }
        )
    }

    /**
     * Sync local state from server (READ-ONLY).
     * Updates SessionManager to match server truth.
     */
    suspend fun syncStateFromServer(): AttendanceSyncResult = withContext(Dispatchers.IO) {
        try {
            val openSession = getOpenSession()
            val isCheckedIn = openSession != null

            sessionManager.isCheckedIn = isCheckedIn
            Log.i(TAG, "syncStateFromServer: isCheckedIn=$isCheckedIn, session=${openSession?.id}")

            AttendanceSyncResult.Success(isCheckedIn, openSession)
        } catch (e: Exception) {
            Log.e(TAG, "syncStateFromServer: failed", e)
            AttendanceSyncResult.Failed(e.message ?: "Sync failed")
        }
    }

    /**
     * Detect and repair drift between local and server state.
     * SILENT operation - no user notification.
     */
    suspend fun detectAndRepairDrift(): DriftResult {
        val userId = sessionManager.userId ?: return DriftResult.Failed("Not logged in")

        return withContext(Dispatchers.IO) {
            try {
                val openSession = getOpenSession()
                val serverState = openSession != null
                val localState = sessionManager.isCheckedIn

                if (localState != serverState) {
                    Log.w(TAG, "DRIFT DETECTED: local=$localState, server=$serverState, userId=$userId")
                    sessionManager.isCheckedIn = serverState
                    DriftResult.Repaired(localState, serverState, openSession)
                } else {
                    Log.d(TAG, "detectAndRepairDrift: consistent (isCheckedIn=$serverState)")
                    DriftResult.Consistent(serverState, openSession)
                }
            } catch (e: Exception) {
                Log.e(TAG, "detectAndRepairDrift: failed", e)
                DriftResult.Failed(e.message ?: "Drift detection failed")
            }
        }
    }

    // ── Private Helper Methods ──

    /**
     * Generic RPC call executor with SQLSTATE error mapping.
     * Eliminates code duplication between checkIn/checkOut.
     */
    private suspend fun <T> executeRpcCall(
        apiCall: suspend () -> Response<List<AttendanceSessionDto>>,
        operationName: String,
        onSuccess: (AttendanceSession) -> T,
        errorMapping: Map<String, () -> T>,
        onNetworkError: (String) -> T,
        onUnknownError: (String) -> T
    ): T {
        return try {
            val response = apiCall()

            when {
                response.isSuccessful -> {
                    val sessions = response.body()
                    when {
                        sessions.isNullOrEmpty() -> {
                            Log.e(TAG, "$operationName: empty response")
                            onUnknownError("Empty response from server")
                        }
                        else -> onSuccess(sessions.first().toModel())
                    }
                }
                response.code() in 400..499 -> {
                    val errorBody = response.errorBody()?.string() ?: ""
                    parseSqlStateError(operationName, errorBody, errorMapping, onUnknownError)
                }
                else -> {
                    Log.e(TAG, "$operationName: server error ${response.code()}")
                    onUnknownError("Server error (${response.code()})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$operationName: exception", e)
            onNetworkError(e.message ?: "Network error")
        }
    }

    /**
     * Parse SQLSTATE error codes from error body.
     */
    private fun <T> parseSqlStateError(
        operationName: String,
        errorBody: String,
        errorMapping: Map<String, () -> T>,
        onUnknownError: (String) -> T
    ): T {
        for ((sqlState, handler) in errorMapping) {
            if (errorBody.contains(sqlState)) {
                Log.w(TAG, "$operationName: $sqlState")
                return handler()
            }
        }
        Log.e(TAG, "$operationName: unknown error - $errorBody")
        return onUnknownError(errorBody.ifBlank { "$operationName failed" })
    }

    /**
     * Log error responses consistently.
     */
    private fun logError(operation: String, code: Int, errorBody: String?) {
        when (code) {
            in 400..499 -> {
                if (errorBody?.contains("P0004") == true) {
                    Log.e(TAG, "$operation: not authenticated")
                } else {
                    Log.e(TAG, "$operation: client error $code - $errorBody")
                }
            }
            else -> Log.e(TAG, "$operation: server error $code")
        }
    }
}

// ── Result Classes ──

sealed class AttendanceSyncResult {
    data class Success(val isCheckedIn: Boolean, val session: AttendanceSession?) : AttendanceSyncResult()
    data class Failed(val message: String) : AttendanceSyncResult()
}

sealed class DriftResult {
    data class Repaired(val wasCheckedIn: Boolean, val nowCheckedIn: Boolean, val session: AttendanceSession?) : DriftResult()
    data class Consistent(val isCheckedIn: Boolean, val session: AttendanceSession?) : DriftResult()
    data class Failed(val message: String) : DriftResult()
}

sealed class CheckInResult {
    data class Success(val session: AttendanceSession) : CheckInResult()
    data object AlreadyCheckedIn : CheckInResult()
    data object AccountInactive : CheckInResult()
    data class NetworkError(val message: String) : CheckInResult()
    data class Error(val message: String) : CheckInResult()
}

sealed class CheckOutResult {
    data class Success(val session: AttendanceSession) : CheckOutResult()
    data object NotCheckedIn : CheckOutResult()
    data object AccountInactive : CheckOutResult()
    data object NotAuthenticated : CheckOutResult()
    data class NetworkError(val message: String) : CheckOutResult()
    data class Error(val message: String) : CheckOutResult()
}