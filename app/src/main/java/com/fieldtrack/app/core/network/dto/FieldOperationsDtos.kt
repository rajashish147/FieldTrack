package com.fieldtrack.app.core.network.dto

import com.google.gson.annotations.SerializedName

data class SelfieChallengeResponse(val success: Boolean, val data: SelfieChallenge)
data class SelfieChallenge(
    @SerializedName("challenge_id") val challengeId: String,
    @SerializedName("upload_url") val uploadUrl: String,
    @SerializedName("expires_at") val expiresAt: String,
)
data class CameraCheckInRequest(
    @SerializedName("challenge_id") val challengeId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    @SerializedName("captured_at") val capturedAt: String,
    @SerializedName("location_at") val locationAt: String,
)

data class FieldSettingsResponse(val success: Boolean, val data: FieldSettings?)
data class FieldSettings(val currency: String, val timezone: String)
data class FieldSitesResponse(val success: Boolean, val data: List<FieldSite>)
data class ReachSiteResponse(val success: Boolean, val data: ReachedSite)
data class ReachedSite(
    @SerializedName("visit_id") val visitId: String?,
    @SerializedName("site_id") val siteId: String,
    @SerializedName("arrived_at") val arrivedAt: String,
    @SerializedName("amount_minor") val amountMinor: Long,
    val currency: String,
    val payable: Boolean?,
)
data class FieldSite(
    val id: String, val name: String, val address: String,
    val latitude: Double, val longitude: Double,
    @SerializedName("arrival_radius_m") val arrivalRadiusM: Int,
    @SerializedName("departure_radius_m") val departureRadiusM: Int,
    @SerializedName("visit_rate_minor") val visitRateMinor: Long,
    @SerializedName("is_active") val isActive: Boolean,
)
data class FieldReportResponse(val success: Boolean, val data: FieldReport)
data class FieldReport(val currency: String, val timezone: String, val from: String,
    @SerializedName("to_exclusive") val toExclusive: String, val employees: List<EmployeeEarnings>)
data class EmployeeEarnings(
    @SerializedName("attendance_days") val attendanceDays: Int,
    @SerializedName("attendance_count") val attendanceCount: Int?,
    @SerializedName("worked_seconds") val workedSeconds: Long?,
    @SerializedName("has_open_shift") val hasOpenShift: Boolean,
    @SerializedName("visit_count") val visitCount: Int,
    @SerializedName("basic_pay_minor") val basicPayMinor: Long?,
    @SerializedName("visit_pay_minor") val visitPayMinor: Long,
    @SerializedName("total_pay_minor") val totalPayMinor: Long?,
    @SerializedName("salary_configured") val salaryConfigured: Boolean,
    val visits: List<PaidVisit>,
    val days: List<FieldReportDay>?,
)
data class FieldReportDay(
    val date: String,
    @SerializedName("attendance_count") val attendanceCount: Int,
    @SerializedName("worked_seconds") val workedSeconds: Long,
    @SerializedName("has_open_shift") val hasOpenShift: Boolean,
    @SerializedName("visit_count") val visitCount: Int,
    @SerializedName("visit_pay_minor") val visitPayMinor: Long,
)
data class PaidVisit(val id: String, @SerializedName("site_name") val siteName: String,
    @SerializedName("arrived_at") val arrivedAt: String, @SerializedName("rate_minor") val rateMinor: Long)
data class FieldSetup(val settings: FieldSettings, val sites: List<FieldSite>)
