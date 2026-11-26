package com.fieldtrack.app.features.auth

import android.content.Context
import android.util.Log
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.LoginRequest
import com.fieldtrack.app.service.ExpenseRetryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// ─── AuthResult ───────────────────────────────────────────────────────────────

sealed class AuthResult {
    data class Success(val role: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

// ─── AuthRepository ───────────────────────────────────────────────────────────
// Flow: POST /auth/login → temp save JWT → GET /auth/me → full session save.
// On success: triggers immediate session recovery check + re-schedules workers.
// ─────────────────────────────────────────────────────────────────────────────

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionManager,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            // ── Step 1: authenticate ──────────────────────────────────────────
            val loginResp = api.login(LoginRequest(email, password))
            if (!loginResp.isSuccessful || loginResp.body() == null) {
                val code = loginResp.code()
                return AuthResult.Error(
                    when (code) {
                        401  -> "Invalid email or password"
                        429  -> "Too many attempts. Please wait."
                        else -> "Login failed ($code)"
                    }
                )
            }

            val credentials = loginResp.body()!!.data
            val token = credentials.accessToken

            // ── Step 2: temp-save token so interceptor can attach it for /auth/me
            session.saveSession(token = token, role = "", userId = "", orgId = "", email = null,
                refreshToken = credentials.refreshToken)

            // ── Step 3: fetch identity ────────────────────────────────────────
            val meResp = api.getMe()
            if (!meResp.isSuccessful || meResp.body() == null) {
                session.clearSession()
                return AuthResult.Error("Failed to fetch user identity")
            }

            val me = meResp.body()!!.data

            // ── Step 4: persist full session ──────────────────────────────────
            session.saveSession(
                token  = token,
                role   = me.role,
                userId = me.id,
                orgId  = me.orgId,
                email  = me.email,
                refreshToken = credentials.refreshToken,
            )

            Log.i(TAG, "Login success — role=${me.role}")

            // ── Step 5: re-schedule non-tracking workers (NO immediate recovery) ─
            // Reschedule periodic workers that may have been cancelled at logout.
            // DO NOT trigger immediate SessionRecoveryWorker here — it would run
            // before navigation completes and potentially stop tracking.
            // Recovery will happen naturally via:
            //   1. MainActivity.onResume() when user returns to app
            //   2. Periodic worker (every 15 min)
            //   3. BootReceiver on device restart
            schedulePostLoginWorkers()

            AuthResult.Success(role = me.role)

        } catch (e: Exception) {
            Log.w(TAG, "Login exception: ${e.message}")
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    private fun schedulePostLoginWorkers() {
        // Reschedule persistent periodic workers (in case they were cancelled at logout)
        // DO NOT enqueue immediate SessionRecoveryWorker — let MainActivity.onResume() handle it
        // after navigation completes to avoid premature "No active session" detection.
        ExpenseRetryWorker.schedule(context)
    }

    suspend fun logout() {
        session.clearSession()
    }

    suspend fun getStoredRole(): String? = session.getRole()
    suspend fun isLoggedIn(): Boolean = session.isLoggedIn()
}
