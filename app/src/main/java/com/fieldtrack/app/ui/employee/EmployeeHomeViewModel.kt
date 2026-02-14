package com.fieldtrack.app.ui.employee

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.local.AppDatabase
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.StorageApi
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.remote.dto.UpdateProfileImageDto
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ViewModel for employee home — manages check-in/out state, work timer,
 * sync operations, today's location data, and logout.
 * Replaces the old DashboardViewModel.
 */
class EmployeeHomeViewModel(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val reportRepository: ReportRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
    private val database: AppDatabase,
    private val userApi: UserApi,
    private val storageApi: StorageApi
) : ViewModel() {

    // ── Check-in state ──

    private val _isCheckedIn = MutableLiveData(sessionManager.isCheckedIn)
    val isCheckedIn: LiveData<Boolean> = _isCheckedIn

    private val _checkInTime = MutableLiveData(sessionManager.checkInTime)
    val checkInTime: LiveData<Long> = _checkInTime

    // ── Work duration timer ──

    private val _workDuration = MutableLiveData("00:00:00")
    val workDuration: LiveData<String> = _workDuration

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateWorkDuration()
            handler.postDelayed(this, 1000L)
        }
    }

    // ── Sync state ──

    private val _unsyncedCount = MutableLiveData(0)
    val unsyncedCount: LiveData<Int> = _unsyncedCount

    private val _isSyncing = MutableLiveData(false)
    val isSyncing: LiveData<Boolean> = _isSyncing

    // ── Today's locations (for MyLocationFragment map) ──

    private val _todayLocations = MutableLiveData<List<Location>>(emptyList())
    val todayLocations: LiveData<List<Location>> = _todayLocations

    private var locationCollectionJob: Job? = null

    // ── UI messages ──

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    // ── Profile management ──

    private val _userProfile = MutableLiveData<User?>()
    val userProfile: LiveData<User?> = _userProfile

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _uploadSuccess = MutableLiveData(false)
    val uploadSuccess: LiveData<Boolean> = _uploadSuccess

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    val userName: String get() = sessionManager.userName ?: ""

    init {
        if (sessionManager.isCheckedIn) {
            startTimer()
        }
        loadUnsyncedCount()
    }

    fun checkIn() {
        val now = System.currentTimeMillis()
        sessionManager.isCheckedIn = true
        sessionManager.checkInTime = now
        _isCheckedIn.value = true
        _checkInTime.value = now
        startTimer()

        // Update server-side check-in status
        viewModelScope.launch {
            locationRepository.updateCheckInStatus(true)
        }
    }

    fun checkOut() {
        sessionManager.isCheckedIn = false
        sessionManager.lastCheckOutTime = System.currentTimeMillis()
        _isCheckedIn.value = false
        stopTimer()

        // Update server-side check-in status
        viewModelScope.launch {
            locationRepository.updateCheckInStatus(false)
        }
    }

    /**
     * Collect today's locations from local Room DB as a Flow.
     * Updates in real-time as the LocationTrackingService saves new points.
     * Uses start-of-local-day converted to UTC for correct timezone handling.
     */
    fun loadTodayLocations() {
        val userId = sessionManager.userId ?: return

        // Calculate start of today (local timezone) and format as UTC ISO 8601
        // to match the stored timestamp format
        val cal = Calendar.getInstance() // local timezone
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        utcFormat.timeZone = TimeZone.getTimeZone("UTC")
        val startOfToday = utcFormat.format(cal.time)

        // Cancel any previous collection to avoid duplicates
        locationCollectionJob?.cancel()
        locationCollectionJob = viewModelScope.launch {
            locationRepository.getLocationsSince(userId, startOfToday)
                .collectLatest { locations ->
                    _todayLocations.value = locations
                }
        }
    }

    fun syncData() {
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                locationRepository.syncUnsyncedLocations()
                reportRepository.syncUnsyncedReports()
                expenseRepository.syncUnsyncedExpenses()
                loadUnsyncedCount()
                _toastMessage.value = "Sync completed"
            } catch (e: Exception) {
                _toastMessage.value = "Sync failed: ${e.message}"
            }
            _isSyncing.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            val userId = sessionManager.userId
            // Clean up tracking/report data but preserve expense history
            if (userId != null) {
                database.locationDao().deleteAll(userId)
                database.reportDao().deleteAll(userId)
            }
            authRepository.logout()
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // ── Profile operations ──

    fun loadUserProfile() {
        val userId = sessionManager.userId ?: return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = userApi.getUserById(id = "eq.$userId")
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    val userDto = response.body()!!.first()
                    _userProfile.value = userDto.toModel()
                } else {
                    _errorMessage.value = "Failed to load profile"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Network error"
            }
            _isLoading.value = false
        }
    }

    fun uploadProfileImage(context: Context, uri: Uri) {
        _isLoading.value = true
        _uploadSuccess.value = false
        viewModelScope.launch {
            try {
                val userId = sessionManager.userId
                if (userId.isNullOrBlank()) {
                    _errorMessage.value = "User not logged in"
                    _isLoading.value = false
                    return@launch
                }

                val imageUrl = uploadImageToStorage(context, userId, uri)
                
                if (imageUrl != null) {
                    val updateSuccess = updateUserProfileImage(userId, imageUrl)
                    if (updateSuccess) {
                        sessionManager.profileImageUrl = imageUrl
                        _uploadSuccess.value = true
                    } else {
                        _errorMessage.value = "Failed to update profile"
                    }
                } else {
                    _errorMessage.value = "Failed to upload image"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Upload failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun uploadImageToStorage(context: Context, userId: String, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val compressedFile = ImageUtils.compressImage(context, uri) ?: return@withContext null
                
                val fileName = "${userId}_${System.currentTimeMillis()}.jpg"
                val requestBody = compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", compressedFile.name, requestBody)

                val response = storageApi.uploadFile("profile-images", "$userId/$fileName", part)
                
                if (response.isSuccessful) {
                    "${BuildConfig.SUPABASE_URL}/storage/v1/object/profile-images/$userId/$fileName"
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun updateUserProfileImage(userId: String, imageUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = userApi.updateProfileImage(
                    id = "eq.$userId",
                    body = UpdateProfileImageDto(imageUrl)
                )
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun loadUnsyncedCount() {
        viewModelScope.launch {
            val count = locationRepository.getUnsyncedCount()
            _unsyncedCount.value = count
        }
    }

    private fun startTimer() {
        handler.removeCallbacks(timerRunnable)
        handler.post(timerRunnable)
    }

    private fun stopTimer() {
        handler.removeCallbacks(timerRunnable)
        _workDuration.value = "00:00:00"
    }

    private fun updateWorkDuration() {
        val checkIn = sessionManager.checkInTime
        if (checkIn <= 0L) return
        val elapsed = System.currentTimeMillis() - checkIn
        val hours = (elapsed / 3600000) % 24
        val minutes = (elapsed / 60000) % 60
        val seconds = (elapsed / 1000) % 60
        _workDuration.value = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacks(timerRunnable)
        locationCollectionJob?.cancel()
    }
}

