package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.ReportDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ReportApi {

    @POST("rest/v1/reports")
    suspend fun insertReport(
        @Body report: ReportDto
    ): Response<Unit>

    @GET("rest/v1/reports")
    suspend fun getReportsByUser(
        @Query("user_id") userId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "date.desc"
    ): Response<List<ReportDto>>

    @GET("rest/v1/reports")
    suspend fun getAllReports(
        @Query("select") select: String = "*",
        @Query("order") order: String = "date.desc"
    ): Response<List<ReportDto>>
}
