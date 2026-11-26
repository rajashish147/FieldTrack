package com.fieldtrack.app.features.admin.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.Employee
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmployeesUiState(
    val isLoading: Boolean = true,
    val employees: List<Employee> = emptyList(),
    val searchQuery: String = "",
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class EmployeesViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(EmployeesUiState())
    val state: StateFlow<EmployeesUiState> = _state.asStateFlow()

    init { load() }

    fun load(reset: Boolean = false) {
        viewModelScope.launch {
            val page = if (reset) 1 else _state.value.page
            val query = _state.value.searchQuery.takeIf { it.isNotBlank() }
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = api.getEmployees(page = page, limit = 20, search = query)
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    val list = if (reset) body.data else _state.value.employees + body.data
                    _state.update { it.copy(
                        isLoading = false, employees = list,
                        page = page + 1,
                        hasMore = body.pagination.page * body.pagination.limit < body.pagination.total,
                    )}
                } else {
                    _state.update { it.copy(isLoading = false, error = "Failed to load employees") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        load(reset = true)
    }

    fun refresh() = load(reset = true)
}
