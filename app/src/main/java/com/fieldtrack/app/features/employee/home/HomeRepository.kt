package com.fieldtrack.app.features.employee.home

import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.EmployeeRequestOwner
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.network.dto.AttendanceSession
import com.fieldtrack.app.core.network.dto.DashboardSummary
import com.fieldtrack.app.core.network.dto.CameraCheckInRequest
import com.fieldtrack.app.core.network.dto.CreateLocationPointRequest
import com.fieldtrack.app.core.network.dto.SelfieChallenge
import com.fieldtrack.app.core.network.dto.FieldReport
import com.fieldtrack.app.core.network.dto.FieldSetup
import com.fieldtrack.app.core.network.dto.ReachedSite
import java.time.LocalDate
import java.time.ZoneId
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.SystemClock
import androidx.core.location.LocationCompat
import com.fieldtrack.app.core.permissions.PermissionHelper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import com.google.gson.JsonParser
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val api: ApiService,
    private val sessions: SessionManager,
    @ApplicationContext private val context: Context,
) {
    // Signed storage uploads must NEVER receive the API bearer token.
    private val uploadClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    suspend fun fieldSetup(): Result<FieldSetup?> = runCatching {
        val settingsResponse = api.getFieldSettings()
        check(settingsResponse.isSuccessful) { "Could not load field settings" }
        val settings = settingsResponse.body()?.data ?: return@runCatching null
        val sitesResponse = api.getFieldSites()
        check(sitesResponse.isSuccessful) { "Could not load preset locations" }
        FieldSetup(settings, checkNotNull(sitesResponse.body()).data.filter { it.isActive })
    }

    suspend fun fieldReport(period: String, timezone: String): Result<FieldReport> = runCatching {
        val reportResponse = api.getMyFieldReport(period, LocalDate.now(ZoneId.of(timezone)).toString())
        check(reportResponse.isSuccessful) { "Could not load earnings report" }
        checkNotNull(reportResponse.body()).data
    }

    suspend fun employeeOwner(): String = checkNotNull(sessions.getSession()?.owner) { "Sign in to continue." }

    suspend fun prepareCamera(owner: String): Result<SelfieChallenge> = runCatching {
        val response = api.getSelfieChallenge(EmployeeRequestOwner(owner))
        if (!response.isSuccessful) error(serverError(response.errorBody()?.string(), "Could not prepare camera check-in"))
        response.body()!!.data
    }

    private fun serverError(body: String?, fallback: String): String = try {
        JsonParser.parseString(body).asJsonObject.get("error")?.asString ?: fallback
    } catch (_: Exception) { fallback }

    suspend fun getDashboardSummary(): Result<DashboardSummary> = runCatching {
        val resp = api.getDashboardSummary()
        if (resp.isSuccessful) resp.body()!!.data
        else error("HTTP ${resp.code()}")
    }

    suspend fun activeShift(owner: String): Result<AttendanceSession?> = runCatching {
        val response = api.getMySessions(limit = 1, status = "active", owner = EmployeeRequestOwner(owner))
        check(response.isSuccessful) { "Could not verify your active shift (${response.code()})." }
        checkNotNull(response.body()).data.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentGpsFix(): Location {
        check(PermissionHelper.hasLocationPermission(context)) { "Grant precise location permission and try again." }
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(20_000)
            .build()
        val fix = withTimeoutOrNull(21_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellation = CancellationTokenSource()
                continuation.invokeOnCancellation { cancellation.cancel() }
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(request, cancellation.token)
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
        } ?: error("GPS unavailable. Move outdoors and try again.")
        check(fix.hasAccuracy() && fix.accuracy in 0f..100f && !LocationCompat.isMock(fix)) {
            "An accurate GPS fix is required. Move outdoors and try again."
        }
        check(SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos in 0..30_000_000_000L) {
            "GPS fix is stale. Try again outdoors."
        }
        return fix
    }

    suspend fun confirmArrival(owner: String, siteId: String): Result<ReachedSite> = runCatching {
        val fix = currentGpsFix()
        val response = api.confirmArrival(
            siteId,
            CreateLocationPointRequest(fix.latitude, fix.longitude, fix.accuracy, Instant.ofEpochMilli(fix.time).toString()),
            EmployeeRequestOwner(owner),
        )
        if (response.isSuccessful) checkNotNull(response.body()).data
        else error(serverError(response.errorBody()?.string(), "Could not confirm arrival (${response.code()})."))
    }

    suspend fun checkIn(owner: String, challenge: SelfieChallenge, photo: File, capturedAt: String): Result<AttendanceSession> = runCatching {
        check(Instant.parse(challenge.expiresAt).isAfter(Instant.now())) { "Camera session expired. Take a new selfie." }
        check(photo.length() in 100L..5_242_880L) { "Selfie must be smaller than 5 MB." }
        val fix = currentGpsFix()
        withContext(Dispatchers.IO) {
            val stamped = SelfieGpsStamp.create(photo, fix, capturedAt)
            try {
                check(stamped.length() in 100L..5_242_880L) { "Stamped selfie must be smaller than 5 MB." }
                uploadClient.newCall(Request.Builder().url(challenge.uploadUrl)
                    .put(stamped.asRequestBody("image/jpeg".toMediaType())).build()).execute().use {
                    check(it.isSuccessful) { "Selfie upload failed. Close the camera and take a new photo." }
                }
            } finally {
                stamped.delete()
            }
        }
        val resp = api.checkIn(CameraCheckInRequest(challenge.challengeId, fix.latitude, fix.longitude, fix.accuracy,
            capturedAt, Instant.ofEpochMilli(fix.time).toString()), EmployeeRequestOwner(owner))
        if (resp.isSuccessful) resp.body()!!.data
        else {
            error(serverError(resp.errorBody()?.string(), "Check-in failed (${resp.code()})"))
        }
    }

    suspend fun checkOut(owner: String): Result<Unit> = runCatching {
        val response = api.checkOut(EmployeeRequestOwner(owner))
        if (response.isSuccessful || response.code() == 409) return@runCatching
        val body = response.errorBody()?.use { it.string() }
        val code = runCatching { JsonParser.parseString(body).asJsonObject["code"]?.asString }.getOrNull()
        val message = serverError(body, "Check-out failed (${response.code()}).")
        val alreadyClosed = code == "NO_ACTIVE_SHIFT" || message == "Cannot check out: no active session found. Check in first."
        if (response.code() != 400 || !alreadyClosed) error(message)
    }
}
