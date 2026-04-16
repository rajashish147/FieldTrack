package com.fieldtrack.app.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.local.AppDatabase
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.SyncResult
import com.fieldtrack.app.data.repository.AttendanceSessionRepository
import com.fieldtrack.app.data.repository.CoordinatedCheckOutResult
import com.fieldtrack.app.data.repository.CheckOutResult
import com.fieldtrack.app.service.LocationSyncWorker
import com.fieldtrack.app.service.LocationTrackingService
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val sessionManager: SessionManager,
    private val userApi: UserApi,
    private val appDatabase: AppDatabase,
    private val attendanceRepository: AttendanceSessionRepository
) : ViewModel() {

    private val _isCheckedIn = MutableLiveData<Boolean>()
    val isCheckedIn: LiveData<Boolean> = _isCheckedIn

    private val _unsyncedCount = MutableLiveData<Int>()
    val unsyncedCount: LiveData<Int> = _unsyncedCount

    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    private val _checkInTime = MutableLiveData<Long>()
    val checkInTime: LiveData<Long> = _checkInTime

    private val _workDuration = MutableLiveData<String>()
    val workDuration: LiveData<String> = _workDuration

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val durationHandler = Handler(Looper.getMainLooper())
    private val durationRunnable = object : Runnable {
        override fun run() {
            updateWorkDuration()
            durationHandler.postDelayed(this, 1000L)
        }
    }

    init {
        _isCheckedIn.value = sessionManager.isCheckedIn
        _userName.value = sessionManager.userName ?: "Employee"
        _checkInTime.value = sessionManager.checkInTime
        loadUnsyncedCount()

        if (sessionManager.isCheckedIn && sessionManager.checkInTime > 0) {
            startDurationTimer()
        }
    }

    fun checkIn(context: Context) {
        _isLoading.value = true
        val now = System.currentTimeMillis()

        // Generate temporary session ID for immediate service start
        // Will be replaced with real session ID from backend on next sync
        val tempSessionId = "temp_${System.currentTimeMillis()}"
        sessionManager.setCheckedIn(tempSessionId, now)
        _isCheckedIn.value = true
        _checkInTime.value = now

        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)

        // Schedule periodic background sync
        LocationSyncWorker.schedule(context)

        startDurationTimer()

        viewModelScope.launch {
            syncCheckInStatusToBackend(true)
            _isLoading.value = false
        }
    }

    fun checkOut(context: Context) {
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                when (val result = attendanceRepository.coordinatedCheckOut(context)) {
                    is CoordinatedCheckOutResult.Success -> {
                        // Session already cleared by repository
                        _isCheckedIn.value = false
                        stopDurationTimer()
                        
                        val duration = if (sessionManager.checkInTime > 0) {
                            formatDuration(System.currentTimeMillis() - sessionManager.checkInTime)
                        } else {
                            "00:00:00"
                        }
                        _workDuration.value = duration
                        
                        // Build sync message
                        val syncMsg = when (result.syncResult) {
                            is SyncResult.Success -> "${result.syncResult.synced} locations synced"
                            is SyncResult.Partial -> "${result.syncResult.synced} synced, ${result.syncResult.failed} failed"
                            is SyncResult.Failed -> "sync failed: ${result.syncResult.reason}"
                        }
                        
                        Log.d("DashboardViewModel", "Checkout success: $syncMsg")
                    }
                    
                    is CoordinatedCheckOutResult.BackendFailed -> {
                        handleCheckOutResult(result.checkOutResult)
                    }
                    
                    is CoordinatedCheckOutResult.AlreadyInProgress -> {
                        _error.value = "Checkout already in progress"
                    }
                }
            } catch (e: Exception) {
                _error.value = "Check-out failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
        
        // Cancel periodic sync when checked out
        LocationSyncWorker.cancel(context)
    }

    private fun handleCheckOutResult(result: CheckOutResult) {
        when (result) {
            is CheckOutResult.Success -> {
                // Should not reach here in BackendFailed path
                sessionManager.setCheckedOut(System.currentTimeMillis())
                _isCheckedIn.value = false
                stopDurationTimer()
            }
            is CheckOutResult.NotCheckedIn -> {
                _error.value = "Not checked in"
            }
            is CheckOutResult.AccountInactive -> {
                _error.value = "Account inactive or not authorized"
            }
            is CheckOutResult.NotAuthenticated -> {
                _error.value = "Not authenticated"
            }
            is CheckOutResult.NetworkError -> {
                _error.value = "Network error: ${result.message}"
            }
            is CheckOutResult.Error -> {
                _error.value = "Check-out failed: ${result.message}"
            }
            else -> {}
        }
    }

    fun syncData() {
        _isLoading.value = true
        viewModelScope.launch {
            val result = locationRepository.syncUnsyncedLocations()
            when (result) {
                is SyncResult.Success -> Log.d("DashboardViewModel", "Manual sync: ${result.synced} synced")
                is SyncResult.Partial -> Log.w("DashboardViewModel", "Manual sync partial: ${result.synced} synced")
                is SyncResult.Failed -> Log.e("DashboardViewModel", "Manual sync failed: ${result.reason}")
            }
            loadUnsyncedCount()
            _isLoading.value = false
        }
    }

    fun logout(context: Context) {
        if (sessionManager.isCheckedIn) {
            checkOut(context)
        }
        stopDurationTimer()
        viewModelScope.launch {
            // Clear tracking/report data but preserve expense history
            val userId = sessionManager.userId
            if (userId != null) {
                appDatabase.locationDao().deleteAll(userId)
                appDatabase.reportDao().deleteAll(userId)
            }
            authRepository.logout()
        }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun syncCheckInStatusToBackend(checkedIn: Boolean) {
        try {
            val userId = sessionManager.userId ?: return
            val response = userApi.updateCheckInStatus(
                id = "eq.$userId",
                body = mapOf("is_checked_in" to checkedIn)
            )
            if (!response.isSuccessful) {
                _error.postValue("Failed to sync check-in status with server")
            }
        } catch (e: Exception) {
            _error.postValue("Could not reach server. Status saved locally.")
        }
    }

    private fun startDurationTimer() {
        durationHandler.removeCallbacks(durationRunnable)
        durationHandler.post(durationRunnable)
    }

    private fun stopDurationTimer() {
        durationHandler.removeCallbacks(durationRunnable)
    }

    private fun updateWorkDuration() {
        val checkIn = sessionManager.checkInTime
        if (checkIn > 0 && sessionManager.isCheckedIn) {
            val elapsed = System.currentTimeMillis() - checkIn
            _workDuration.value = formatDuration(elapsed)
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun loadUnsyncedCount() {
        viewModelScope.launch {
            _unsyncedCount.value = locationRepository.getUnsyncedCount()
        }
    }

    fun getUserId(): String? = sessionManager.userId

    override fun onCleared() {
        super.onCleared()
        stopDurationTimer()
    }
}
