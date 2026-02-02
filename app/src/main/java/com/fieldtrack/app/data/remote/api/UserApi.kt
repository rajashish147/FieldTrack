package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.UpdateCheckInDto
import com.fieldtrack.app.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
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

    @PATCH("rest/v1/users")
    suspend fun updateCheckInStatus(
        @Query("id") id: String,
        @Body body: UpdateCheckInDto
    ): Response<Unit>
}
