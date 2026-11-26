package com.fieldtrack.app.features.admin.employees

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.ProfileData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmployeeDetailUiState(
    val isLoading: Boolean = true,
    val profile: ProfileData? = null,
    val error: String? = null,
)

@HiltViewModel
class EmployeeDetailViewModel @Inject constructor(
    private val api: ApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val employeeId: String = checkNotNull(savedStateHandle["employeeId"])

    private val _state = MutableStateFlow(EmployeeDetailUiState())
    val state: StateFlow<EmployeeDetailUiState> = _state.asStateFlow()

    init { load(employeeId) }

    fun load(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = api.getEmployeeProfile(id)
                if (resp.isSuccessful) {
                    _state.update { it.copy(isLoading = false, profile = resp.body()?.data) }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Failed to load employee (${resp.code()})") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
