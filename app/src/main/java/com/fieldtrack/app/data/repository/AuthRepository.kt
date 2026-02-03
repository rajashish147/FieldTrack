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
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val sessionManager: SessionManager
) {

    private val gson = Gson()

    suspend fun login(email: String, password: String): NetworkResult<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(request = LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    sessionManager.accessToken = authResponse.accessToken
                    sessionManager.refreshToken = authResponse.refreshToken
                    sessionManager.userId = authResponse.user.id

                    fetchAndCacheUserProfile(authResponse.user.id)
                } else {
                    NetworkResult.Error(parseError(response.errorBody()?.string(), "Login failed"), response.code())
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Network error. Check your connection.")
            }
        }

    suspend fun signUp(email: String, password: String, name: String): NetworkResult<User> =
        withContext(Dispatchers.IO) {
            try {
                val request = SignUpRequest(
                    email = email,
                    password = password,
                    data = mapOf("name" to name)
                )
                val response = authApi.signUp(request)
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    sessionManager.accessToken = authResponse.accessToken
                    sessionManager.refreshToken = authResponse.refreshToken
                    sessionManager.userId = authResponse.user.id
                    sessionManager.userName = name
                    sessionManager.userRole = "employee"

                    val user = User(
                        id = authResponse.user.id,
                        name = name,
                        role = "employee",
                        isCheckedIn = false
                    )
                    NetworkResult.Success(user)
                } else {
                    NetworkResult.Error(parseError(response.errorBody()?.string(), "Sign up failed"), response.code())
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Network error. Check your connection.")
            }
        }

    suspend fun validateSession(): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn) return@withContext false
        try {
            val userId = sessionManager.userId ?: return@withContext false
            val response = userApi.getUserById(id = "eq.$userId")
            if (response.isSuccessful) {
                true
            } else if (response.code() == 401) {
                refreshToken()
            } else {
                false
            }
        } catch (e: Exception) {
            refreshToken()
        }
    }

    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentRefreshToken = sessionManager.refreshToken ?: return@withContext false
            val response = authApi.refreshToken(
                request = RefreshTokenRequest(currentRefreshToken)
            )
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                sessionManager.accessToken = authResponse.accessToken
                sessionManager.refreshToken = authResponse.refreshToken
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
            } catch (_: Exception) {
            } finally {
                sessionManager.clearSession()
            }
        }
    }

    private suspend fun fetchAndCacheUserProfile(userId: String): NetworkResult<User> {
        return try {
            val userResponse = userApi.getUserById(id = "eq.$userId")
            if (userResponse.isSuccessful && !userResponse.body().isNullOrEmpty()) {
                val userDto = userResponse.body()!!.first()
                sessionManager.userRole = userDto.role
                sessionManager.userName = userDto.name
                NetworkResult.Success(userDto.toModel())
            } else {
                sessionManager.userRole = "employee"
                sessionManager.userName = ""
                val fallbackUser = User(
                    id = userId,
                    name = "",
                    role = "employee",
                    isCheckedIn = false
                )
                NetworkResult.Success(fallbackUser)
            }
        } catch (e: Exception) {
            NetworkResult.Error("Logged in but failed to load profile: ${e.message}")
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

    fun getCurrentUserId(): String? = sessionManager.userId

    fun getCurrentUserName(): String? = sessionManager.userName
}
