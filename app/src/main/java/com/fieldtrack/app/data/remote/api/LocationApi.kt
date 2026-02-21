package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.LocationDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LocationApi {

    @POST("rest/v1/locations")
    suspend fun insertLocations(
        @Body locations: List<LocationDto>
    ): Response<Unit>

    @GET("rest/v1/locations")
    suspend fun getLocationsByUser(
        @Query("user_id") userId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.desc"
    ): Response<List<LocationDto>>

    @GET("rest/v1/locations")
    suspend fun getLocationsByUserAndDate(
        @Query("user_id") userId: String,
        @Query("timestamp") timestampFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.asc"
    ): Response<List<LocationDto>>

    @GET("rest/v1/locations")
    suspend fun getLocationsBySession(
        @Query("session_id") sessionId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.asc"
    ): Response<List<LocationDto>>
}
