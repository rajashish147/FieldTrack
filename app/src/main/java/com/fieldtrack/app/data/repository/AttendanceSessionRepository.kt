package com.fieldtrack.app.data.repository

import android.util.Log
import com.fieldtrack.app.data.model.AttendanceSession
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.AttendanceApi
import com.fieldtrack.app.data.remote.dto.AttendanceSessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
     * 
     * P2.2 - Session drift correction:
     * If P0002 (NOT_CHECKED_IN) is received, automatically syncs state from server.
     * If server confirms no open session, returns SessionAlreadyClosedByAdmin.
     */
    suspend fun checkOut(): CheckOutResult = withContext(Dispatchers.IO) {
        val result = executeRpcCall(
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

        // P2.2 - Session drift correction: If P0002, sync state from server
        if (result is CheckOutResult.NotCheckedIn) {
            Log.w(TAG, "checkOut: P0002 detected, syncing state from server")
            val openSession = getOpenSession()
            
            if (openSession == null) {
                // Server confirms no open session - session was closed externally (likely by admin)
                Log.i(TAG, "checkOut: server confirms no open session, clearing local state")
                sessionManager.isCheckedIn = false
                sessionManager.checkInTime = 0L
                return@withContext CheckOutResult.SessionAlreadyClosedByAdmin
            } else {
                // Server has open session - local state was wrong, repair it
                Log.w(TAG, "checkOut: server has open session, repairing local state")
                sessionManager.isCheckedIn = true
                val checkInTimeMillis = parseIso8601Timestamp(openSession.checkInAt)
                sessionManager.checkInTime = checkInTimeMillis
                return@withContext CheckOutResult.NotCheckedIn
            }
        }

        result
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
            
            // Restore checkInTime from server session to fix timer after app data clear
            if (isCheckedIn && openSession != null) {
                val checkInTimeMillis = parseIso8601Timestamp(openSession.checkInAt)
                sessionManager.checkInTime = checkInTimeMillis
                Log.i(TAG, "syncStateFromServer: restored checkInTime=$checkInTimeMillis")
            }
            
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
                    
                    // Restore checkInTime when repairing drift to checked-in state
                    if (serverState && openSession != null) {
                        val checkInTimeMillis = parseIso8601Timestamp(openSession.checkInAt)
                        sessionManager.checkInTime = checkInTimeMillis
                        Log.i(TAG, "detectAndRepairDrift: restored checkInTime=$checkInTimeMillis")
                    }
                    
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
                response.code() in 400..599 -> {
                    // P2.2 - Parse SQLSTATE errors from both 4xx and 5xx responses
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

    /**
     * Parse ISO 8601 timestamp to milliseconds.
     * Handles Supabase format: 2026-02-14T21:00:01.405445+00:00
     * Falls back to current time on parse error.
     */
    private fun parseIso8601Timestamp(timestamp: String): Long {
        return try {
            // Supabase returns timestamps with 6 decimal places (microseconds)
            // SimpleDateFormat only supports 3 decimal places (milliseconds)
            // So we need to truncate: 2026-02-14T21:00:01.405445+00:00 -> 2026-02-14T21:00:01.405+00:00
            val truncated = timestamp.replace(Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\d{3}"), "$1")
            
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(truncated)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "parseIso8601Timestamp: failed to parse '$timestamp', using current time", e)
            System.currentTimeMillis()
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
    // P2.2 - Session drift correction
    data object SessionAlreadyClosedByAdmin : CheckOutResult()
    data class NetworkError(val message: String) : CheckOutResult()
    data class Error(val message: String) : CheckOutResult()
}