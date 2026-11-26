package com.fieldtrack.app.core.network.dto

import com.google.gson.annotations.SerializedName

// ─── Auth DTOs ───────────────────────────────────────────────────────────────

data class LoginRequest(
    val email: String,
    val password: String,
)

data class LoginResponse(
    val success: Boolean,
    val data: LoginData,
)

data class LoginData(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("token_type")    val tokenType: String,
    @SerializedName("expires_in")    val expiresIn: Int,
    @SerializedName("expires_at")    val expiresAt: Long?,
)

data class MeResponse(
    val success: Boolean,
    val data: MeData,
)

data class MeData(
    val id: String,
    val email: String?,
    val role: String,
    @SerializedName("orgId") val orgId: String,
)

data class CrashReportRequest(
    @SerializedName("file_name") val fileName: String,
    val platform: String = "android",
    @SerializedName("raw_report") val rawReport: String,
)

data class CrashReportResponse(
    val success: Boolean,
    val data: CrashReportData,
)

data class CrashReportData(
    val id: String,
)

// ─── Attendance DTOs ─────────────────────────────────────────────────────────

data class PaginationMeta(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int,
)

data class AttendanceSession(
    val id: String,
    @SerializedName("employee_id")       val employeeId: String,
    @SerializedName("organization_id")   val organizationId: String,
    @SerializedName("checkin_at")        val checkinAt: String,
    @SerializedName("checkout_at")       val checkoutAt: String?,
    @SerializedName("total_distance_km") val totalDistanceKm: Double?,
    @SerializedName("total_duration_seconds") val totalDurationSeconds: Long?,
    @SerializedName("created_at")        val createdAt: String,
    @SerializedName("updated_at")        val updatedAt: String,
)

data class SessionResponse(
    val success: Boolean,
    val data: AttendanceSession,
)

data class PaginatedSessionsResponse(
    val success: Boolean,
    val data: List<AttendanceSession>,
    val pagination: PaginationMeta,
)

// ─── Dashboard DTOs ──────────────────────────────────────────────────────────

data class DashboardSummaryResponse(
    val success: Boolean,
    val data: DashboardSummary,
)

data class DashboardSummary(
    val sessionsThisWeek: Int,
    val distanceThisWeek: Double,
    val hoursThisWeek: Double,
)

// ─── Location DTOs ───────────────────────────────────────────────────────────

data class CreateLocationRequest(
    @SerializedName("session_id")  val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    @SerializedName("recorded_at") val recordedAt: String,
)

data class CreateLocationPointRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    @SerializedName("recorded_at") val recordedAt: String,
)

data class CreateLocationBatchRequest(
    @SerializedName("session_id") val sessionId: String,
    val points: List<CreateLocationPointRequest>,
)

data class LocationItem(
    val id: String,
    @SerializedName("session_id")    val sessionId: String,
    @SerializedName("organization_id") val organizationId: String,
    @SerializedName("employee_id")   val employeeId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    @SerializedName("recorded_at")   val recordedAt: String,
    @SerializedName("sequence_number") val sequenceNumber: Int?,
)

data class BatchLocationResponse(
    val success: Boolean,
    val inserted: Int,
    @SerializedName("discarded_recorded_at") val discardedRecordedAt: List<String>?,
    @SerializedName("session_closed") val sessionClosed: Boolean,
)

data class LocationListResponse(
    val success: Boolean,
    val data: List<LocationItem>,
)

// ─── Expense DTOs ────────────────────────────────────────────────────────────

data class Expense(
    val id: String,
    @SerializedName("employee_id")     val employeeId: String?,
    @SerializedName("organization_id") val organizationId: String,
    val amount: Double,
    val description: String,
    val status: String,
    @SerializedName("receipt_url")     val receiptUrl: String?,
    @SerializedName("rejection_comment") val rejectionComment: String?,
    @SerializedName("submitted_at")    val submittedAt: String,
    @SerializedName("reviewed_at")     val reviewedAt: String?,
    @SerializedName("employee_name")   val employeeName: String?,
    @SerializedName("employee_code")   val employeeCode: String?,
)

data class CreateExpenseRequest(
    val amount: Double,
    val description: String,
    @SerializedName("receipt_url") val receiptUrl: String? = null,
    val category: String? = null,
    @SerializedName("idempotency_key") val idempotencyKey: String,
)

data class UpdateExpenseStatusRequest(
    val status: String,
    @SerializedName("rejection_comment") val rejectionComment: String? = null,
)

data class PaginatedExpensesResponse(
    val success: Boolean,
    val data: List<Expense>,
    val pagination: PaginationMeta,
)

data class ReceiptUploadUrlRequest(
    val extension: String,
    val mimeType: String? = null,
)

data class ReceiptUploadUrlResponse(
    val success: Boolean,
    val data: ReceiptUploadUrlData,
)

data class ReceiptUploadUrlData(
    val uploadUrl: String,
    val receiptUrl: String,
)

// ─── Profile DTOs ────────────────────────────────────────────────────────────

data class ProfileResponse(
    val success: Boolean,
    val data: ProfileData?,
    val meta: ProfileMeta?,
)

data class ProfileMeta(val hasProfile: Boolean)

data class ProfileData(
    val id: String,
    val name: String,
    @SerializedName("employee_code") val employeeCode: String?,
    val phone: String?,
    @SerializedName("is_active")     val isActive: Boolean,
    val activityStatus: String,
    @SerializedName("last_activity_at") val lastActivityAt: String?,
    @SerializedName("created_at")    val createdAt: String,
    val stats: ProfileStats,
)

data class ProfileStats(
    val totalSessions: Int,
    val totalDistanceKm: Double,
    val totalDurationSeconds: Long,
    val expensesSubmitted: Int,
    val expensesApproved: Int,
)

// ─── Employee DTOs (Admin) ───────────────────────────────────────────────────

data class Employee(
    val id: String,
    @SerializedName("organization_id") val organizationId: String,
    @SerializedName("user_id")         val userId: String?,
    val name: String,
    @SerializedName("employee_code")   val employeeCode: String,
    val phone: String?,
    @SerializedName("is_active")       val isActive: Boolean,
    @SerializedName("created_at")      val createdAt: String,
    @SerializedName("updated_at")      val updatedAt: String,
)

data class PaginatedEmployeesResponse(
    val success: Boolean,
    val data: List<Employee>,
    val pagination: PaginationMeta,
)

data class CreateEmployeeRequest(
    val name: String,
    val phone: String? = null,
    @SerializedName("employee_code") val employeeCode: String? = null,
    val email: String? = null,
    val password: String? = null,
)

data class UpdateEmployeeRequest(
    val name: String? = null,
    val phone: String? = null,
)

// ─── Admin Sessions ───────────────────────────────────────────────────────────

data class AdminSession(
    val id: String?,
    @SerializedName("employee_id")       val employeeId: String,
    @SerializedName("employee_name")     val employeeName: String?,
    @SerializedName("employee_code")     val employeeCode: String?,
    @SerializedName("checkin_at")        val checkinAt: String,
    @SerializedName("checkout_at")       val checkoutAt: String?,
    @SerializedName("total_distance_km") val totalDistanceKm: Double?,
    @SerializedName("total_duration_seconds") val totalDurationSeconds: Long?,
    @SerializedName("activityStatus") val activityStatus: String,
)

data class PaginatedAdminSessionsResponse(
    val success: Boolean,
    val data: List<AdminSession>,
    val pagination: PaginationMeta,
)

data class EmployeeMapMarker(
    val employeeId: String,
    val employeeName: String,
    val employeeCode: String?,
    val status: String,
    val sessionId: String?,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String,
)

data class MapMarkersResponse(
    val success: Boolean,
    val data: List<EmployeeMapMarker>,
)

// ─── Analytics DTOs ──────────────────────────────────────────────────────────

data class OrgSummaryResponse(
    val success: Boolean,
    val data: OrgSummaryData,
)

data class OrgSummaryData(
    val totalEmployees: Int,
    val activeToday: Int,
    val totalSessionsThisPeriod: Int,
    val totalDistanceKm: Double,
    val totalExpensesPending: Int,
    val totalExpensesAmount: Double,
)

data class TopPerformer(
    val employeeId: String,
    val employeeName: String,
    val employeeCode: String,
    val totalDistanceKm: Double,
    val totalSessions: Int,
    val rank: Int,
)

data class TopPerformersResponse(
    val success: Boolean,
    val data: List<TopPerformer>,
)

// ─── Generic API Response ─────────────────────────────────────────────────────

data class ApiError(
    val success: Boolean,
    val error: String,
    val requestId: String,
    val code: String?,
)
