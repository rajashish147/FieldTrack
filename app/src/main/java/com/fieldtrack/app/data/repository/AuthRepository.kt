package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.AuthApi
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.remote.dto.LoginRequest
import com.fieldtrack.app.data.remote.dto.RefreshTokenRequest
import com.fieldtrack.app.data.remote.dto.SignUpRequest
import com.fieldtrack.app.data.remote.dto.SupabaseError
import com.fieldtrack.app.data.remote.dto.InsertUserDto
import com.fieldtrack.app.data.remote.dto.UserDto
import com.fieldtrack.app.data.model.UserRole
import com.fieldtrack.app.util.Constants
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Distinct result for signup since it doesn't produce a session
sealed class SignUpResult {
    data object NeedsEmailVerification : SignUpResult()
    data class Error(val message: String) : SignUpResult()
}

class AuthRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val sessionManager: SessionManager
) {

    private val gson = Gson()

    /**
     * Login flow returning [LoginResult]:
     * - Success -> user is authenticated AND is_active = true
     * - PendingApproval -> user is authenticated but is_active = false
     * - Error -> authentication or profile fetch failed
     */
    suspend fun login(email: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(request = LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!

                    if (!authResponse.hasSession || authResponse.user == null) {
                        return@withContext LoginResult.Error("Login failed: no session returned")
                }
                // Store session tokens
                    sessionManager.saveSession(
                        accessToken = authResponse.accessToken!!,
                        refreshToken = authResponse.refreshToken!!,
                        userId = authResponse.user.id,
                        expiresIn = authResponse.expiresIn ?: 3600L
                    )

                    // Fetch or create profile, then return result based on is_active
                    ensureProfileAndReturn(authResponse.user.id)
            } else {
                    LoginResult.Error(parseError(response.errorBody()?.string(),
                "Login failed"))
            }
        } catch (e: Exception) {
                LoginResult.Error(e.message ?: "Network error. Check your connection.")
        }
    }
    // Signup ONLY creates the user in Supabase Auth.
    // Does NOT store tokens or navigate to dashboard.
    // User must verify email first, then login.
    suspend fun signUp(email: String, password: String, name: String): SignUpResult =
        withContext(Dispatchers.IO) {
            try {
                val request = SignUpRequest(
                    email = email,
                    password = password,
                    data = mapOf("name" to name)
                )
                val response = authApi.signUp(request)
                if (response.isSuccessful) {
                // Supabase returns 200 with user but no tokens when
                // email confirmation is enabled. Either way, we do NOT auto-login.
                    SignUpResult.NeedsEmailVerification
            } else {
                    SignUpResult.Error(parseError(response.errorBody()?.string(),
                "Sign up failed"))
            }
        } catch (e: Exception) {
                SignUpResult.Error(e.message ?: "Network error. Check your connection.")
        }
    }
    /**
     * Validates the current session and refreshes the cached user profile.
     * Returns a [LoginResult] so the caller can re-evaluate routing
     * (e.g. role changed server-side, or user deactivated).
     */
    suspend fun validateSession(): LoginResult = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn) return@withContext LoginResult.Error("Not logged in")
        try {
            val userId = sessionManager.userId ?: return@withContext LoginResult.Error("No user ID")
            val response = userApi.getUserById(id = "eq.$userId")
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                val userDto = response.body()!!.first()
                // Re-cache profile data — reflects any server-side role/active changes
                sessionManager.userRole = UserRole.fromString(userDto.role)
                sessionManager.userName = userDto.name
                sessionManager.isActive = userDto.isActive
                sessionManager.profileImageUrl = userDto.profileImageUrl

                if (!userDto.isActive) {
                    LoginResult.PendingApproval
                } else {
                    LoginResult.Success(userDto.toModel())
                }
            } else if (response.code() == 401) {
                // Token expired — try refresh
                val refreshed = refreshToken()
                if (refreshed) validateSession() else LoginResult.Error("Session expired")
            } else {
                LoginResult.Error("Session validation failed")
            }
        } catch (e: Exception) {
            // Network error — try refresh as fallback
            val refreshed = refreshToken()
            if (refreshed) {
                try { validateSession()
                } catch (_: Exception) {
                    LoginResult.Error("Session validation failed")
                }
            } else {
                LoginResult.Error("Session validation failed")
            }
        }
    }

    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRefreshToken = sessionManager.refreshToken ?: return@withContext false
            val response = authApi.refreshToken(
                request = RefreshTokenRequest(currentRefreshToken)
            )
            if (response.isSuccessful && response.body()?.hasSession == true) {
                val authResponse = response.body()!!
                sessionManager.saveSession(
                    accessToken = authResponse.accessToken!!,
                    refreshToken = authResponse.refreshToken!!,
                    userId = sessionManager.userId ?: authResponse.user?.id ?: "",
                    expiresIn = authResponse.expiresIn ?: 3600L
                )
                
                true
            } else {
                sessionManager.clearSession()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                authApi.logout()
            } catch (_: Exception) {} finally {
                sessionManager.clearSession()
            }
        }
    }
    /**
     * Fetches profile from Supabase. If it genuinely doesn't exist
     * (e.g. trigger failed), inserts a default employee profile.
     *
     * CRITICAL — 3-STATE HANDLING:
     *
     *   STATE 1  response.isSuccessful && body.isNotEmpty()
     *            → Profile exists. Use it.
     *
     *   STATE 2  response.isSuccessful && body.isEmpty()
     *            → Profile genuinely missing. Safe to create.
     *
     *   STATE 3  !response.isSuccessful  OR  exception thrown
     *            → HTTP error / network failure.
     *            → NEVER create profile.
     *            → NEVER modify database.
     *            → Return LoginResult.Error.
     *
     * PROHIBITION: createDefaultProfile() must NEVER be called on
     * 401, 403, 500, network failure, or JSON parse error.
     */
    private suspend fun ensureProfileAndReturn(userId: String): LoginResult {
        return try {
            val response = userApi.getUserById(id = "eq.$userId")
            val body = response.body()

            when {
                // ── STATE 1: SUCCESS + profile exists ──
                response.isSuccessful && !body.isNullOrEmpty() -> {
                    val userDto = body.first()
                    sessionManager.userRole = UserRole.fromString(userDto.role)
                    sessionManager.userName = userDto.name
                    sessionManager.isActive = userDto.isActive
                    sessionManager.profileImageUrl = userDto.profileImageUrl

                    if (!userDto.isActive) {
                        LoginResult.PendingApproval
                    } else {
                        LoginResult.Success(userDto.toModel())
                    }
                }
                // ── STATE 2: SUCCESS + profile truly missing ──
                response.isSuccessful && body.isNullOrEmpty() -> {
                    if (com.fieldtrack.app.BuildConfig.DEBUG) {
                        android.util.Log.d("AuthRepo",
                        "User $userId not in public.users — creating default profile")
                    }
                    createDefaultProfile(userId)
                }
                // ── STATE 3: ANY FAILURE (401 / 403 / 500 / etc.) ──
                else -> {
                    if (com.fieldtrack.app.BuildConfig.DEBUG) {
                        val errorBody = response.errorBody()?.string()
                        android.util.Log.e("AuthRepo",
                        "GET user HTTP ${response.code()}: $errorBody")
                    }
                    LoginResult.Error(
                        "Failed to fetch profile (HTTP ${response.code()})"
                    )
                }
            }
        } catch (e: Exception) {
            // ── STATE 3 (exception path): network timeout / JSON parse / etc. ──
            if (com.fieldtrack.app.BuildConfig.DEBUG) {
                android.util.Log.e("AuthRepo",
                "ensureProfile exception", e)
            }
            // NEVER create profile, NEVER modify database.
            LoginResult.Error(e.message ?: "Network error fetching profile")
        }
    }
    /**
     * Creates a default EMPLOYEE profile for a new user.
     *
     * Uses [ignore-duplicates] on the INSERT so an existing profile is NEVER
     * overwritten. If a conflict occurs (profile was created concurrently by
     * a database trigger), a recovery GET fetches the real profile instead
     * of hardcoding EMPLOYEE / is_active=false into the session.
     */
    private suspend fun createDefaultProfile(userId: String): LoginResult {
        return try {
            val name = sessionManager.userName ?: ""
            val dto = InsertUserDto(
                id = userId,
                name = name.ifBlank {
                "Employee"
            },
                role = UserRole.EMPLOYEE.name,
                isActive = false // new users need admin approval
            )
            val insertResponse = userApi.insertUser(dto)
            if (insertResponse.isSuccessful && !insertResponse.body().isNullOrEmpty()) {
                // INSERT succeeded — new profile created
                val created = insertResponse.body()!!.first()
                sessionManager.userRole = UserRole.fromString(created.role)
                sessionManager.userName = created.name
                sessionManager.isActive = created.isActive
                sessionManager.profileImageUrl = created.profileImageUrl
                // New profile is always pending admin approval
                LoginResult.PendingApproval
            } else {
                // INSERT returned empty body — conflict (user already exists
                // due to database trigger race). Perform a recovery GET to
                // fetch the REAL profile instead of assuming EMPLOYEE/inactive.
                if (com.fieldtrack.app.BuildConfig.DEBUG) {
                    android.util.Log.d("AuthRepo",
                    "INSERT conflict for $userId — performing recovery GET")
                }
                recoverProfileAfterConflict(userId)
            }
        } catch (e: Exception) {
            if (com.fieldtrack.app.BuildConfig.DEBUG) {
                android.util.Log.e("AuthRepo",
                "createDefaultProfile exception", e)
            }
            LoginResult.Error(e.message ?: "Failed to create profile")
        }
    }
    /**
     * Recovery path: INSERT was silently skipped (ignore-duplicates conflict),
     * meaning the user already exists. Fetch the real profile and return the
     * correct LoginResult based on actual role and is_active.
     */
    private suspend fun recoverProfileAfterConflict(userId: String): LoginResult {
        return try {
            val response = userApi.getUserById(id = "eq.$userId")
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                val userDto = response.body()!!.first()
                sessionManager.userRole = UserRole.fromString(userDto.role)
                sessionManager.userName = userDto.name
                sessionManager.isActive = userDto.isActive
                sessionManager.profileImageUrl = userDto.profileImageUrl

                if (!userDto.isActive) {
                    LoginResult.PendingApproval
                } else {
                    LoginResult.Success(userDto.toModel())
                }
            } else {
                // Recovery GET also failed — propagate error
                LoginResult.Error("Profile conflict recovery failed (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Profile conflict recovery failed")
        }
    }

    private fun parseError(errorBody: String?, fallback: String): String {
        if (errorBody.isNullOrBlank()) return fallback
        return try {
            val error = gson.fromJson(errorBody, SupabaseError::class.java)
            error.readableMessage()
        } catch (e: Exception) {
            fallback
        }
    }

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn

    fun isAdmin(): Boolean = sessionManager.isAdmin

    fun getUserRole(): UserRole = sessionManager.userRole

    fun isUserActive(): Boolean = sessionManager.isActive

    fun getCurrentUserId(): String? = sessionManager.userId

    fun getCurrentUserName(): String? = sessionManager.userName
    
    fun needsProfileSetup(): Boolean = sessionManager.profileImageUrl.isNullOrBlank()
}