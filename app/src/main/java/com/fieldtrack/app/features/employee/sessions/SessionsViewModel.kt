package com.fieldtrack.app.features.employee.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.AttendanceSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionsUiState(
    val isLoading: Boolean = true,
    val sessions: List<AttendanceSession> = emptyList(),
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val statusFilter: String = "all",  // "all", "active", "recent", "inactive" (lowercase)
)

@HiltViewModel
class SessionsViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    init { load() }

    fun load(reset: Boolean = false) {
        viewModelScope.launch {
            val page = if (reset) 1 else _state.value.page
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = api.getMySessions(
                    page = page,
                    limit = 20,
                    status = _state.value.statusFilter
                )
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    val newList = if (reset) body.data else _state.value.sessions + body.data
                    _state.update { it.copy(
                        isLoading = false,
                        sessions = newList,
                        page = page + 1,
                        hasMore = body.pagination.page * body.pagination.limit < body.pagination.total,
                    )}
                } else {
                    _state.update { it.copy(isLoading = false, error = "Failed to load sessions") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(status: String) {
        _state.update { it.copy(statusFilter = status, page = 1) }
        load(reset = true)
    }

    fun refresh() = load(reset = true)
    fun loadMore() { if (_state.value.hasMore && !_state.value.isLoading) load() }
}
