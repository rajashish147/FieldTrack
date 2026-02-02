package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.AuthResponse
import com.fieldtrack.app.data.remote.dto.LoginRequest
import com.fieldtrack.app.data.remote.dto.RefreshTokenRequest
import com.fieldtrack.app.data.remote.dto.SignUpRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {

    @POST("auth/v1/token")
    suspend fun login(
        @Query("grant_type") grantType: String = "password",
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SignUpRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun refreshToken(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: RefreshTokenRequest
    ): Response<AuthResponse>

    @POST("auth/v1/logout")
    suspend fun logout(): Response<Unit>
}
