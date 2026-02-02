package com.fieldtrack.app.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.service.LocationTrackingService
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isCheckedIn = MutableLiveData<Boolean>()
    val isCheckedIn: LiveData<Boolean> = _isCheckedIn

    private val _unsyncedCount = MutableLiveData<Int>()
    val unsyncedCount: LiveData<Int> = _unsyncedCount

    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> = _userName

    init {
        _isCheckedIn.value = sessionManager.isCheckedIn
        _userName.value = sessionManager.userName ?: "Employee"
        loadUnsyncedCount()
    }

    fun checkIn(context: Context) {
        sessionManager.isCheckedIn = true
        _isCheckedIn.value = true

        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    fun checkOut(context: Context) {
        sessionManager.isCheckedIn = false
        _isCheckedIn.value = false

        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        context.startService(intent)

        viewModelScope.launch {
            locationRepository.syncUnsyncedLocations()
        }
    }

    fun syncData() {
        viewModelScope.launch {
            locationRepository.syncUnsyncedLocations()
            loadUnsyncedCount()
        }
    }

    fun logout(context: Context) {
        if (sessionManager.isCheckedIn) {
            checkOut(context)
        }
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    private fun loadUnsyncedCount() {
        viewModelScope.launch {
            _unsyncedCount.value = locationRepository.getUnsyncedCount()
        }
    }

    fun getUserId(): String? = sessionManager.userId
}
