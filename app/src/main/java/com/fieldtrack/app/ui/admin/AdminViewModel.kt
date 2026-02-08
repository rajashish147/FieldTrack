package com.fieldtrack.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.Expense
import com.fieldtrack.app.data.model.Report
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.remote.api.UserApi
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import com.fieldtrack.app.util.Constants
import kotlinx.coroutines.launch

class AdminViewModel(
    private val userApi: UserApi,
    private val locationRepository: LocationRepository,
    private val reportRepository: ReportRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _employees = MutableLiveData<List<User>>()
    val employees: LiveData<List<User>> = _employees

    private val _employeeNames = MutableLiveData<Map<String, String>>()
    val employeeNames: LiveData<Map<String, String>> = _employeeNames

    private val _allReports = MutableLiveData<List<Report>>()
    val allReports: LiveData<List<Report>> = _allReports

    private val _allExpenses = MutableLiveData<List<Expense>>()
    val allExpenses: LiveData<List<Expense>> = _allExpenses

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _statusUpdateResult = MutableLiveData<String?>()
    val statusUpdateResult: LiveData<String?> = _statusUpdateResult

    fun loadEmployees() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = userApi.getUsersByRole(role = "eq.${Constants.ROLE_EMPLOYEE}")
                if (response.isSuccessful && response.body() != null) {
                    val users = response.body()!!.map { it.toModel() }
                    _employees.value = users
                    _employeeNames.value = users.associate { it.id to it.name }
                } else {
                    _error.value = "Failed to load employees"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun loadAllReports() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = reportRepository.fetchAllReports()) {
                is NetworkResult.Success -> _allReports.value = result.data
                is NetworkResult.Error -> _error.value = result.message
                is NetworkResult.Loading -> { }
            }
            _isLoading.value = false
        }
    }

    fun loadAllExpenses() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = expenseRepository.fetchAllExpenses()) {
                is NetworkResult.Success -> _allExpenses.value = result.data
                is NetworkResult.Error -> _error.value = result.message
                is NetworkResult.Loading -> { }
            }
            _isLoading.value = false
        }
    }

    fun approveExpense(expenseId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = expenseRepository.updateExpenseStatus(expenseId, Constants.EXPENSE_STATUS_APPROVED)) {
                is NetworkResult.Success -> {
                    _statusUpdateResult.value = "Expense approved"
                    loadAllExpenses()
                }
                is NetworkResult.Error -> _error.value = result.message
                is NetworkResult.Loading -> { }
            }
        }
    }

    fun rejectExpense(expenseId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = expenseRepository.updateExpenseStatus(expenseId, Constants.EXPENSE_STATUS_REJECTED)) {
                is NetworkResult.Success -> {
                    _statusUpdateResult.value = "Expense rejected"
                    loadAllExpenses()
                }
                is NetworkResult.Error -> _error.value = result.message
                is NetworkResult.Loading -> { }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearStatusUpdateResult() {
        _statusUpdateResult.value = null
    }
}
