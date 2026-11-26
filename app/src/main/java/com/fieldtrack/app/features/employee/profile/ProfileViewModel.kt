package com.fieldtrack.app.features.employee.profile

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.ProfileData
import com.fieldtrack.app.service.ExpenseRetryWorker
import com.fieldtrack.app.service.LocationForegroundService
import com.fieldtrack.app.service.LocationSyncWorker
import com.fieldtrack.app.service.SessionRecoveryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: ProfileData? = null,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: ApiService,
    private val session: SessionManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = api.getMyProfile()
                if (resp.isSuccessful) {
                    _state.update { it.copy(isLoading = false, profile = resp.body()?.data) }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Failed to load profile (${resp.code()})") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Network error") }
            }
        }
    }

    /**
     * Full logout:
     *   1. Stop foreground location service (prevents ghost tracking after logout)
     *   2. Cancel all background workers (no background calls with cleared token)
     *   3. Clear session (token, role, userId, orgId, email)
     *   4. Navigate to Login
     */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                session.clearSession()
                LocationForegroundService.stop(context)
                SessionRecoveryWorker.cancel(context)
                LocationSyncWorker.cancel(context)
                WorkManager.getInstance(context).cancelUniqueWork(ExpenseRetryWorker.WORK_NAME)
                onDone()
            } catch (e: Exception) {
                Log.w("ProfileViewModel", "Logout failed", e)
                _state.update { it.copy(error = "Could not finish signing out. Try again.") }
            }
        }
    }
}
