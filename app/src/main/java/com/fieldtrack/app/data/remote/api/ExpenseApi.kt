package com.fieldtrack.app.data.remote.api

import com.fieldtrack.app.data.remote.dto.ExpenseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface ExpenseApi {

    @POST("rest/v1/expenses")
    suspend fun insertExpense(
        @Body expense: ExpenseDto
    ): Response<Unit>

    @GET("rest/v1/expenses")
    suspend fun getExpensesByUser(
        @Query("user_id") userId: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.desc"
    ): Response<List<ExpenseDto>>

    @GET("rest/v1/expenses")
    suspend fun getAllExpenses(
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.desc"
    ): Response<List<ExpenseDto>>

    @PATCH("rest/v1/expenses")
    suspend fun updateExpenseStatus(
        @Query("id") id: String,
        @Body body: Map<String, String>
    ): Response<Unit>
}
