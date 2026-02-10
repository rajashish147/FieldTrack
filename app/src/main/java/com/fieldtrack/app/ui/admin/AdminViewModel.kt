package com.fieldtrack.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.repository.AdminRepository
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for admin fragments (EmployeeListFragment, ApprovalFragment).
 * Manages employee list + approval operations, all RLS-enforced server-side.
 */
class AdminViewModel(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _allEmployees = MutableLiveData<List<User>>()
    val allEmployees: LiveData<List<User>> = _allEmployees

    private val _pendingEmployees = MutableLiveData<List<User>>()
    val pendingEmployees: LiveData<List<User>> = _pendingEmployees

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    fun loadAllEmployees() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = adminRepository.getAllEmployees()) {
                is NetworkResult.Success -> _allEmployees.value = result.data
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
            _isLoading.value = false
        }
    }

    fun loadPendingEmployees() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = adminRepository.getPendingEmployees()) {
                is NetworkResult.Success -> _pendingEmployees.value = result.data
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
            _isLoading.value = false
        }
    }

    fun approveEmployee(userId: String) {
        viewModelScope.launch {
            when (val result = adminRepository.approveEmployee(userId)) {
                is NetworkResult.Success -> {
                    _toastMessage.value = "Employee approved"
                    loadPendingEmployees()
                    loadAllEmployees()
                }
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
        }
    }

    fun deactivateEmployee(userId: String) {
        viewModelScope.launch {
            when (val result = adminRepository.deactivateEmployee(userId)) {
                is NetworkResult.Success -> {
                    _toastMessage.value = "Employee deactivated"
                    loadAllEmployees()
                    loadPendingEmployees()
                }
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }
}
