package com.fieldtrack.app.ui.employee

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
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
import com.fieldtrack.app.data.repository.SyncResult
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.CheckOutResult
import com.fieldtrack.app.data.repository.CoordinatedCheckOutResult
import com.fieldtrack.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val storageApi: StorageApi,
    private val authApi: com.fieldtrack.app.data.remote.api.AuthApi,
    private val attendanceRepository: com.fieldtrack.app.data.repository.AttendanceSessionRepository
) : ViewModel() {

    companion object {
        // Time conversion constants
        private const val MILLIS_PER_SECOND = 1000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val HOURS_PER_DAY = 24L
        
        // Timer update interval
        private const val TIMER_UPDATE_INTERVAL_MS = 1000L
    }

    // ── Check-in state ──

    private val _isCheckedIn = MutableLiveData(sessionManager.isCheckedIn)
    val isCheckedIn: LiveData<Boolean> = _isCheckedIn

    private val _checkInTime = MutableLiveData(sessionManager.checkInTime)
    val checkInTime: LiveData<Long> = _checkInTime

    // ── Work duration timer ──

    private val _workDuration = MutableLiveData("00:00:00")
    val workDuration: LiveData<String> = _workDuration

    private var timerJob: Job? = null

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

    // v1.4 Phase 0: Recovery completion flag
    // CRITICAL: Service must NOT start until this is true
    private val _isRecoveryComplete = MutableLiveData(false)
    val isRecoveryComplete: LiveData<Boolean> = _isRecoveryComplete

    // v1.4 Phase 0: Derived state for service start
    // Combines isCheckedIn AND isRecoveryComplete
    // Service should ONLY start when BOTH are true
    val shouldStartService: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        var checkedIn = false
        var recoveryDone = false
        
        fun update() {
            value = checkedIn && recoveryDone
        }
        
        addSource(isCheckedIn) {
            checkedIn = it
            update()
        }
        
        addSource(isRecoveryComplete) {
            recoveryDone = it
            update()
        }
    }

    init {
        if (sessionManager.isCheckedIn) {
            startTimer()
        }
        loadUnsyncedCount()
        
        // v1.4 Phase 0: Ensure session recovery completes BEFORE any sync
        // This guarantees currentSessionId is correct before service can start
        syncStateOnStartup()
    }

    /**
     * v1.4 Phase 0 — Upgrade Recovery (Production Safe)
     * 
     * Guarantees that currentSessionId is consistent before
     * any location tracking can begin.
     * 
     * This function is suspend and MUST complete before
     * LocationTrackingService starts.
     */
    suspend fun ensureSessionRecoveredIfNeeded() {
        // Detect upgrade scenario
        if (sessionManager.isCheckedIn && sessionManager.currentSessionId == null) {
            if (BuildConfig.DEBUG) {
                android.util.Log.i("EmployeeHomeVM", "Upgrade recovery triggered")
            }
            
            try {
                val openSession = attendanceRepository.getOpenSession()
                
                if (openSession != null) {
                    val checkInTimeMillis = if (sessionManager.checkInTime == 0L) {
                        parseIso8601Timestamp(openSession.checkInAt)
                    } else {
                        sessionManager.checkInTime
                    }
                    
                    sessionManager.setCheckedIn(openSession.id, checkInTimeMillis)
                    _checkInTime.value = checkInTimeMillis
                    
                    if (BuildConfig.DEBUG) {
                        android.util.Log.i("EmployeeHomeVM", "Session ID restored")
                    }
                } else {
                    clearLocalSessionState()
                }
            } catch (e: Exception) {
                // Deterministic failure handling
                clearLocalSessionState()
                
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("EmployeeHomeVM", "Recovery failed — state cleared", e)
                }
            }
        }
    }
    
    /**
     * Clear local session state when recovery fails or no open session exists.
     */
    private fun clearLocalSessionState() {
        sessionManager.setCheckedOut(System.currentTimeMillis())
        
        _isCheckedIn.value = false
        stopTimer()
    }

    /**
     * STEP 1: Sync state from server on app startup.
     * Silently repairs drift without user notification.
     * 
     * CRITICAL: Runs AFTER ensureSessionRecoveredIfNeeded() completes.
     */
    private fun syncStateOnStartup() {
        viewModelScope.launch {
            // Phase 0: Ensure session recovery completes FIRST
            ensureSessionRecoveredIfNeeded()
            
            // Only after recovery completes, sync state
            try {
                attendanceRepository.detectAndRepairDrift()
                updateUIFromSessionManager()
            } catch (e: Exception) {
                // Silent fail - app continues with local state
            }
            
            // CRITICAL: Signal that recovery is complete
            // Service can now start safely
            _isRecoveryComplete.value = true
        }
    }

    /**
     * STEP 1: Manual sync trigger (e.g., pull-to-refresh).
     */
    fun syncStateFromServer() {
        viewModelScope.launch {
            try {
                attendanceRepository.syncStateFromServer()
                updateUIFromSessionManager()
                _toastMessage.value = "State synced"
            } catch (e: Exception) {
                _toastMessage.value = "Sync failed: ${e.message}"
            }
        }
    }

    /**
     * STEP 2: Check-in via RPC.
     * Uses SQLSTATE-based error handling and recovery via STEP 1 methods.
     */
    fun checkIn() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = attendanceRepository.checkIn()) {
                    is com.fieldtrack.app.data.repository.CheckInResult.Success -> {
                        val checkInTime = parseIso8601Timestamp(result.session.checkInAt)
                        sessionManager.setCheckedIn(result.session.id, checkInTime)
                        _isCheckedIn.value = true
                        _checkInTime.value = checkInTime
                        startTimer()
                        _toastMessage.value = "Checked in successfully"
                    }
                    is com.fieldtrack.app.data.repository.CheckInResult.AlreadyCheckedIn -> {
                        attendanceRepository.syncStateFromServer()
                        updateUIFromSessionManager()
                        _toastMessage.value = "Already checked in"
                    }
                    is com.fieldtrack.app.data.repository.CheckInResult.AccountInactive -> {
                        _toastMessage.value = "Account inactive or not authorized"
                    }
                    is com.fieldtrack.app.data.repository.CheckInResult.NetworkError -> {
                        attendanceRepository.syncStateFromServer()
                        updateUIFromSessionManager()
                        _toastMessage.value = "Network error: ${result.message}"
                    }
                    is com.fieldtrack.app.data.repository.CheckInResult.Error -> {
                        _toastMessage.value = "Check-in failed: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Check-in failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * STEP 3: Check-out via RPC with coordinated final sync.
     * Uses SQLSTATE-based error handling and recovery via STEP 1 methods.
     * 
     * CRITICAL: Uses coordinatedCheckOut to prevent race condition where
     * auth is cleared before final sync completes.
     * 
     * P2.2 - Session drift correction:
     * Handles SessionAlreadyClosedByAdmin when admin force-closes session.
     * 
     * @param context Android context for service control
     */
    fun checkOut(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = attendanceRepository.coordinatedCheckOut(context)) {
                    is CoordinatedCheckOutResult.Success -> {
                        // Session already cleared by repository
                        _isCheckedIn.value = false
                        stopTimer()
                        
                        // Build sync message
                        val syncMsg = when (result.syncResult) {
                            is SyncResult.Success -> "${result.syncResult.synced} locations synced"
                            is SyncResult.Partial -> "${result.syncResult.synced} synced, ${result.syncResult.failed} failed"
                            is SyncResult.Failed -> "sync failed: ${result.syncResult.reason}"
                        }
                        
                        _toastMessage.value = "Checked out successfully ($syncMsg)"
                    }
                    
                    is CoordinatedCheckOutResult.BackendFailed -> {
                        // Handle backend checkout failure
                        handleCheckOutResult(result.checkOutResult)
                    }
                    
                    is CoordinatedCheckOutResult.AlreadyInProgress -> {
                        _toastMessage.value = "Checkout already in progress"
                    }
                }
            } catch (e: Exception) {
                _toastMessage.value = "Check-out failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Handle individual checkout result types.
     */
    private fun handleCheckOutResult(result: CheckOutResult) {
        when (result) {
            is CheckOutResult.Success -> {
                // Should not reach here in BackendFailed path
                sessionManager.setCheckedOut(System.currentTimeMillis())
                _isCheckedIn.value = false
                stopTimer()
                _toastMessage.value = "Checked out successfully"
            }
            is CheckOutResult.SessionAlreadyClosedByAdmin -> {
                // State already synced in repository, just update UI
                _isCheckedIn.value = false
                stopTimer()
                _toastMessage.value = "Your session was closed by an administrator"
            }
            is CheckOutResult.NotCheckedIn -> {
                viewModelScope.launch {
                    attendanceRepository.syncStateFromServer()
                    updateUIFromSessionManager()
                }
                _toastMessage.value = "Not checked in"
            }
            is CheckOutResult.AccountInactive -> {
                _toastMessage.value = "Account inactive or not authorized"
            }
            is CheckOutResult.NotAuthenticated -> {
                _toastMessage.value = "Not authenticated"
            }
            is CheckOutResult.NetworkError -> {
                viewModelScope.launch {
                    attendanceRepository.syncStateFromServer()
                    updateUIFromSessionManager()
                }
                _toastMessage.value = "Network error: ${result.message}"
            }
            is CheckOutResult.Error -> {
                _toastMessage.value = "Check-out failed: ${result.message}"
            }
        }
    }

    // ── Private Helper Methods ──

    /**
     * Update UI state from SessionManager after sync operations.
     */
    private fun updateUIFromSessionManager() {
        _isCheckedIn.value = sessionManager.isCheckedIn
        if (sessionManager.isCheckedIn) {
            _checkInTime.value = sessionManager.checkInTime
            startTimer()
        } else {
            stopTimer()
        }
    }

    /**
     * Parse ISO 8601 timestamp to milliseconds.
     * Falls back to current time on parse error.
     */
    private fun parseIso8601Timestamp(timestamp: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(timestamp)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
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
                val locationResult = locationRepository.syncUnsyncedLocations()
                val reportResult = reportRepository.syncUnsyncedReports()
                val expenseResult = expenseRepository.syncUnsyncedExpenses()
                
                // Count total synced
                val totalSynced = locationResult.getSyncedCount() + 
                    reportResult.getSyncedCount() +
                    expenseResult.getSyncedCount()
                
                loadUnsyncedCount()
                
                // Determine message based on results
                val hasFailures = locationResult is SyncResult.Failed || 
                    reportResult is SyncResult.Failed || 
                    expenseResult is SyncResult.Failed
                
                val hasPartial = locationResult is SyncResult.Partial || 
                    reportResult is SyncResult.Partial || 
                    expenseResult is SyncResult.Partial
                
                _toastMessage.value = when {
                    hasFailures -> "Sync failed for some items"
                    hasPartial -> "Sync partially completed ($totalSynced items synced)"
                    else -> "Sync completed successfully ($totalSynced items)"
                }
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
                    
                    // Lazy sync: if email/phone are null, fetch from auth metadata and update
                    if (userDto.email == null || userDto.phone == null) {
                        syncEmailFromAuth(userId)
                        // Reload profile after sync
                        val updatedResponse = userApi.getUserById(id = "eq.$userId")
                        if (updatedResponse.isSuccessful && !updatedResponse.body().isNullOrEmpty()) {
                            _userProfile.value = updatedResponse.body()!!.first().toModel()
                        } else {
                            _userProfile.value = userDto.toModel()
                        }
                    } else {
                        _userProfile.value = userDto.toModel()
                    }
                } else {
                    _errorMessage.value = "Failed to load profile"
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Network error"
            }
            _isLoading.value = false
        }
    }

    private suspend fun syncEmailFromAuth(userId: String) {
        return withContext(Dispatchers.IO) {
            try {
                val authUserResponse = authApi.getCurrentUser()
                if (authUserResponse.isSuccessful && authUserResponse.body() != null) {
                    val authUser = authUserResponse.body()!!
                    val email = authUser.email
                    val phone = authUser.userMetadata?.get("phone") as? String
                    
                    // Update users table with email/phone from auth
                    userApi.updateUserEmailPhone(
                        id = "eq.$userId",
                        body = com.fieldtrack.app.data.remote.dto.UpdateUserEmailPhoneDto(
                            email = email,
                            phone = phone
                        )
                    )
                }
            } catch (e: Exception) {
                // Silent fail - profile will still load without email/phone
            }
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
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                updateWorkDuration()
                delay(TIMER_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _workDuration.value = "00:00:00"
    }

    private fun updateWorkDuration() {
        val checkIn = sessionManager.checkInTime
        if (checkIn <= 0L) return
        val elapsed = System.currentTimeMillis() - checkIn
        val hours = (elapsed / MILLIS_PER_HOUR) % HOURS_PER_DAY
        val minutes = (elapsed / MILLIS_PER_MINUTE) % 60
        val seconds = (elapsed / MILLIS_PER_SECOND) % 60
        _workDuration.value = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        locationCollectionJob?.cancel()
    }
}

