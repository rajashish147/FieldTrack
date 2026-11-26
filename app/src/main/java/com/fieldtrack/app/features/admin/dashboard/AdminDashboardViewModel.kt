package com.fieldtrack.app.features.admin.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.AdminSession
import com.fieldtrack.app.core.network.dto.OrgSummaryData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val orgSummary: OrgSummaryData? = null,
    val recentSessions: List<AdminSession> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(AdminDashboardUiState())
    val state: StateFlow<AdminDashboardUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val summaryResp = api.getOrgSummary(period = "week")
                val sessionsResp = api.getAdminSessions(page = 1, limit = 10)

                _state.update { it.copy(
                    isLoading = false,
                    orgSummary = if (summaryResp.isSuccessful) summaryResp.body()?.data else null,
                    recentSessions = if (sessionsResp.isSuccessful) sessionsResp.body()?.data ?: emptyList() else emptyList(),
                    error = if (!summaryResp.isSuccessful) "Failed to load summary" else null,
                )}
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
