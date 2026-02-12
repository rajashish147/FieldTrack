package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.UpdateCheckInDto
import com.fieldtrack.app.data.remote.dto.UpdateUserStatusDto
import com.fieldtrack.app.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface UserApi {

    @GET("rest/v1/users")
    suspend fun getUserById(
        @Query("id") id: String,
        @Header("Range") range: String = "0-0"
    ): Response<List<UserDto>>

    @GET("rest/v1/users")
    suspend fun getAllUsers(
        @Query("select") select: String = "*"
    ): Response<List<UserDto>>

    @GET("rest/v1/users")
    suspend fun getUsersByRole(
        @Query("role") role: String,
        @Query("select") select: String = "*"
    ): Response<List<UserDto>>

    /** Fetch users filtered by is_active status (admin-only, enforced by RLS) */
    @GET("rest/v1/users")
    suspend fun getUsersByActiveStatus(
        @Query("role") role: String,
        @Query("is_active") isActive: String,
        @Query("select") select: String = "*"
    ): Response<List<UserDto>>

    @POST("rest/v1/users")
    suspend fun insertUser(
        @Body body: UserDto
    ): Response<List<UserDto>>

    @PATCH("rest/v1/users")
    suspend fun updateCheckInStatus(
        @Query("id") id: String,
        @Body body: UpdateCheckInDto
    ): Response<Unit>

    /** Admin-only: update a user's is_active or role (RLS enforced) */
    @PATCH("rest/v1/users")
    suspend fun updateUserStatus(
        @Query("id") id: String,
        @Body body: UpdateUserStatusDto
    ): Response<Unit>
    
    /** Update user's last_synced_at timestamp */
    @PATCH("rest/v1/users")
    suspend fun updateUserLastSync(
        @Query("id") id: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}
