package com.fieldtrack.app.core.network

import com.fieldtrack.app.core.network.dto.*
import retrofit2.Response
import retrofit2.http.*

// ─── FieldTrack API Service ───────────────────────────────────────────────────
// Single Retrofit interface for ALL backend endpoints.
// ALL endpoints require Bearer JWT (injected by AuthInterceptor) except /auth/login.
// ─────────────────────────────────────────────────────────────────────────────

interface ApiService {

    // ── Auth ─────────────────────────────────────────────────────────────────

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<MeResponse>

    @POST("crashes")
    suspend fun uploadCrashReport(@Body request: CrashReportRequest): Response<CrashReportResponse>

    // ── Attendance (Employee) ─────────────────────────────────────────────────

    @POST("attendance/check-in")
    suspend fun checkIn(@Body request: CameraCheckInRequest, @Tag owner: EmployeeRequestOwner): Response<SessionResponse>

    @POST("attendance/selfie-challenge")
    suspend fun getSelfieChallenge(@Tag owner: EmployeeRequestOwner): Response<SelfieChallengeResponse>

    @GET("field/settings")
    suspend fun getFieldSettings(): Response<FieldSettingsResponse>

    @GET("field/sites")
    suspend fun getFieldSites(): Response<FieldSitesResponse>

    @POST("field/sites/{id}/reach")
    suspend fun confirmArrival(
        @Path("id") siteId: String,
        @Body request: CreateLocationPointRequest,
        @Tag owner: EmployeeRequestOwner,
    ): Response<ReachSiteResponse>

    @GET("field/my-report")
    suspend fun getMyFieldReport(
        @Query("period") period: String,
        @Query("date") date: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): Response<FieldReportResponse>

    @POST("attendance/check-out")
    suspend fun checkOut(@Tag owner: EmployeeRequestOwner): Response<SessionResponse>

    @GET("attendance/my-sessions")
    suspend fun getMySessions(
        @Query("page")  page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("status") status: String = "all",  // Filter: "all", "active", "recent", "inactive" (lowercase)
        @Tag owner: EmployeeRequestOwner? = null,
    ): Response<PaginatedSessionsResponse>

    // ── Dashboard (Employee) ──────────────────────────────────────────────────

    @GET("dashboard/my-summary")
    suspend fun getDashboardSummary(@Tag owner: EmployeeRequestOwner? = null): Response<DashboardSummaryResponse>

    // ── Locations (Employee) ──────────────────────────────────────────────────

    @POST("locations")
    suspend fun recordLocation(@Body request: CreateLocationRequest): Response<*>

    @POST("locations/batch")
    suspend fun recordLocationBatch(
        @Body request: CreateLocationBatchRequest,
        @Tag owner: EmployeeRequestOwner,
    ): Response<BatchLocationResponse>

    @GET("locations/my-route")
    suspend fun getMyRoute(@Query("session_id") sessionId: String): Response<LocationListResponse>

    // ── Expenses (Employee) ───────────────────────────────────────────────────

    @POST("expenses/receipt-upload-url")
    suspend fun getReceiptUploadUrl(@Body request: ReceiptUploadUrlRequest): Response<ReceiptUploadUrlResponse>

    @POST("expenses")
    suspend fun createExpense(@Body request: CreateExpenseRequest, @Tag owner: EmployeeRequestOwner): Response<*>

    @GET("expenses/my")
    suspend fun getMyExpenses(
        @Query("page")   page: Int = 1,
        @Query("limit")  limit: Int = 20,
        @Query("status") status: String = "all",
    ): Response<PaginatedExpensesResponse>

    // ── Profile ───────────────────────────────────────────────────────────────

    @GET("profile/me")
    suspend fun getMyProfile(): Response<ProfileResponse>

    // ── Leaderboard (all roles) ───────────────────────────────────────────────

    @GET("leaderboard")
    suspend fun getLeaderboard(
        @Query("period") period: String = "week",
        @Query("limit")  limit: Int = 10,
    ): Response<TopPerformersResponse>

    // ── Admin: Sessions ───────────────────────────────────────────────────────

    @GET("admin/sessions")
    suspend fun getAdminSessions(
        @Query("page")        page: Int = 1,
        @Query("limit")       limit: Int = 20,
        @Query("status")      status: String = "all",  // Valid: "all", "active", "recent", "inactive" (lowercase)
        @Query("employee_id") employeeId: String? = null,
    ): Response<PaginatedAdminSessionsResponse>

    @GET("admin/sessions/{id}/locations")
    suspend fun getSessionLocations(
        @Path("id") sessionId: String,
    ): Response<LocationListResponse>

    @GET("admin/monitoring/map")
    suspend fun getAdminMapMarkers(): Response<MapMarkersResponse>

    // ── Admin: Employees ──────────────────────────────────────────────────────

    @GET("admin/employees")
    suspend fun getEmployees(
        @Query("page")   page: Int = 1,
        @Query("limit")  limit: Int = 20,
        @Query("search") search: String? = null,
    ): Response<PaginatedEmployeesResponse>

    @POST("admin/employees")
    suspend fun createEmployee(@Body request: CreateEmployeeRequest): Response<*>

    @GET("admin/employees/{id}")
    suspend fun getEmployee(@Path("id") id: String): Response<*>

    @PATCH("admin/employees/{id}")
    suspend fun updateEmployee(
        @Path("id") id: String,
        @Body request: UpdateEmployeeRequest,
    ): Response<*>

    @PATCH("admin/employees/{id}/status")
    suspend fun setEmployeeStatus(
        @Path("id") id: String,
        @Body body: Map<String, Boolean>,
    ): Response<*>

    @GET("admin/employees/{id}/profile")
    suspend fun getEmployeeProfile(@Path("id") employeeId: String): Response<ProfileResponse>

    // ── Admin: Expenses ───────────────────────────────────────────────────────

    @GET("admin/expenses")
    suspend fun getAdminExpenses(
        @Query("page")   page: Int = 1,
        @Query("limit")  limit: Int = 20,
        @Query("status") status: String = "all",  // Valid: "all", "PENDING", "APPROVED", "REJECTED", "processed"
    ): Response<PaginatedExpensesResponse>

    @PATCH("admin/expenses/{id}")
    suspend fun updateExpenseStatus(
        @Path("id") id: String,
        @Body request: UpdateExpenseStatusRequest,
    ): Response<*>

    @GET("admin/expenses/summary")
    suspend fun getExpenseSummary(
        @Query("page")  page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<*>

    // ── Admin: Analytics ──────────────────────────────────────────────────────

    @GET("admin/org-summary")
    suspend fun getOrgSummary(
        @Query("period") period: String = "week",
    ): Response<OrgSummaryResponse>

    @GET("admin/top-performers")
    suspend fun getTopPerformers(
        @Query("period") period: String = "week",
        @Query("limit")  limit: Int = 10,
    ): Response<TopPerformersResponse>

    @GET("admin/session-trend")
    suspend fun getSessionTrend(
        @Query("period") period: String = "week",
    ): Response<*>

    @GET("admin/leaderboard")
    suspend fun getAdminLeaderboard(
        @Query("period") period: String = "week",
        @Query("limit")  limit: Int = 10,
    ): Response<TopPerformersResponse>

    // ── Admin: Search ─────────────────────────────────────────────────────────

    @GET("admin/search")
    suspend fun search(
        @Query("q")     query: String,
        @Query("limit") limit: Int = 10,
    ): Response<*>
}
