package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.AuthApi
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.remote.dto.LoginRequest
import com.fieldtrack.app.data.remote.dto.RefreshTokenRequest
import com.fieldtrack.app.data.remote.dto.SignUpRequest
import com.fieldtrack.app.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val sessionManager: SessionManager
) {

    suspend fun login(email: String, password: String): NetworkResult<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(request = LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    sessionManager.accessToken = authResponse.accessToken
                    sessionManager.refreshToken = authResponse.refreshToken
                    sessionManager.userId = authResponse.user.id

                    val userResponse = userApi.getUserById(
                        id = "eq.${authResponse.user.id}"
                    )
                    if (userResponse.isSuccessful && !userResponse.body().isNullOrEmpty()) {
                        val userDto = userResponse.body()!!.first()
                        sessionManager.userRole = userDto.role
                        sessionManager.userName = userDto.name
                        NetworkResult.Success(userDto.toModel())
                    } else {
                        NetworkResult.Error("Failed to fetch user profile")
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Login failed"
                    NetworkResult.Error(errorBody, response.code())
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error occurred")
            }
        }

    suspend fun signUp(email: String, password: String, name: String): NetworkResult<User> =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.signUp(SignUpRequest(email, password))
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
                    val errorBody = response.errorBody()?.string() ?: "Sign up failed"
                    NetworkResult.Error(errorBody, response.code())
                }
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "Unknown error occurred")
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

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn

    fun isAdmin(): Boolean = sessionManager.isAdmin

    fun getCurrentUserId(): String? = sessionManager.userId

    fun getCurrentUserName(): String? = sessionManager.userName
}
