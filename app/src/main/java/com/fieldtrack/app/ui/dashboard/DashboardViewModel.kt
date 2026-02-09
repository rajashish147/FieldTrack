package com.fieldtrack.app.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.remote.dto.UpdateCheckInDto
import com.fieldtrack.app.data.local.AppDatabase
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.service.LocationSyncWorker
import com.fieldtrack.app.service.LocationTrackingService
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val sessionManager: SessionManager,
    private val userApi: UserApi,
    private val appDatabase: AppDatabase
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

        sessionManager.isCheckedIn = true
        sessionManager.checkInTime = now
        _isCheckedIn.value = true
        _checkInTime.value = now

        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        context.startForegroundService(intent)

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
        val now = System.currentTimeMillis()

        sessionManager.isCheckedIn = false
        sessionManager.lastCheckOutTime = now
        _isCheckedIn.value = false

        stopDurationTimer()

        val duration = if (sessionManager.checkInTime > 0) {
            formatDuration(now - sessionManager.checkInTime)
        } else {
            "00:00:00"
        }
        _workDuration.value = duration

        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        context.startService(intent)

        viewModelScope.launch {
            locationRepository.syncUnsyncedLocations()
            syncCheckInStatusToBackend(false)
            loadUnsyncedCount()
            _isLoading.value = false
        }

        // Cancel periodic sync when checked out
        LocationSyncWorker.cancel(context)
    }

    fun syncData() {
        _isLoading.value = true
        viewModelScope.launch {
            locationRepository.syncUnsyncedLocations()
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
            // Clear all local data for privacy
            appDatabase.locationDao().deleteAll()
            appDatabase.reportDao().deleteAll()
            appDatabase.expenseDao().deleteAll()
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
                body = UpdateCheckInDto(isCheckedIn = checkedIn)
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
