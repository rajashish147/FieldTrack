package com.fieldtrack.app.ui.employee

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.local.AppDatabase
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.data.repository.ExpenseRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for employee home — manages check-in/out state, work timer,
 * sync operations, and logout.
 * Replaces the old DashboardViewModel.
 */
class EmployeeHomeViewModel(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val reportRepository: ReportRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
    private val database: AppDatabase
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

    // ── UI messages ──

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

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
            // Clean up local data
            database.locationDao().deleteAll()
            database.reportDao().deleteAll()
            database.expenseDao().deleteAll()
            authRepository.logout()
        }
    }

    fun clearToast() {
        _toastMessage.value = null
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
    }
}
