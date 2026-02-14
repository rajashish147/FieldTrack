package com.fieldtrack.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.Expense
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.repository.AdminRepository
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.LocationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Lightweight projection for the tracking list and map detail — one per employee.
 */
data class EmployeeLocation(
    val userId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lastUpdated: String  // ISO 8601 or empty string if never synced
) {
    val hasLocation: Boolean get() = lastUpdated.isNotEmpty()
}

/**
 * Shared ViewModel for admin fragments (EmployeeListFragment, ApprovalFragment, LiveTrackingFragment).
 * Manages employee list + approval operations + live tracking data.
 * All RLS-enforced server-side.
 */
class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val locationRepository: LocationRepository,
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: com.fieldtrack.app.data.remote.SessionManager
) : ViewModel() {

    private val _allEmployeesRaw = MutableLiveData<List<User>>()
    
    private val _allEmployees = MutableLiveData<List<User>>()
    val allEmployees: LiveData<List<User>> = _allEmployees

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery

    private val _pendingEmployeesRaw = MutableLiveData<List<User>>()
    
    private val _pendingEmployees = MutableLiveData<List<User>>()
    val pendingEmployees: LiveData<List<User>> = _pendingEmployees

    private val _pendingSearchQuery = MutableLiveData("")
    val pendingSearchQuery: LiveData<String> = _pendingSearchQuery

    private val _selectedEmployee = MutableLiveData<User?>()
    val selectedEmployee: LiveData<User?> = _selectedEmployee

    private val _employeeLocations = MutableLiveData<List<EmployeeLocation>>()
    val employeeLocations: LiveData<List<EmployeeLocation>> = _employeeLocations

    private val _expensesRaw = MutableLiveData<List<Expense>>()
    
    private val _expenses = MutableLiveData<List<Expense>>()
    val expenses: LiveData<List<Expense>> = _expenses

    private val _expenseSearchQuery = MutableLiveData("")
    val expenseSearchQuery: LiveData<String> = _expenseSearchQuery

    private val _expenseStatusFilter = MutableLiveData("all")
    val expenseStatusFilter: LiveData<String> = _expenseStatusFilter

    /** Map of userId → employee name for display in expense cards */
    private val _employeeNames = MutableLiveData<Map<String, String>>(emptyMap())
    val employeeNames: LiveData<Map<String, String>> = _employeeNames

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    fun loadAllEmployees() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = adminRepository.getAllEmployees()) {
                is NetworkResult.Success -> {
                    _allEmployeesRaw.value = result.data
                    applySearchFilter()
                }
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter()
    }

    fun updatePendingSearchQuery(query: String) {
        _pendingSearchQuery.value = query
        applyPendingSearchFilter()
    }

    fun updateExpenseSearchQuery(query: String) {
        _expenseSearchQuery.value = query
        applyExpenseSearchFilter()
    }

    fun updateExpenseStatusFilter(status: String) {
        _expenseStatusFilter.value = status
        applyExpenseSearchFilter()
    }

    private fun applySearchFilter() {
        val query = _searchQuery.value ?: ""
        val rawList = _allEmployeesRaw.value ?: emptyList()
        
        _allEmployees.value = if (query.isBlank()) {
            rawList
        } else {
            rawList.filter { it.matchesSearch(query) }
        }
    }

    private fun applyPendingSearchFilter() {
        val query = _pendingSearchQuery.value ?: ""
        val rawList = _pendingEmployeesRaw.value ?: emptyList()
        
        _pendingEmployees.value = if (query.isBlank()) {
            rawList
        } else {
            rawList.filter { it.matchesSearch(query) }
        }
    }

    private fun applyExpenseSearchFilter() {
        val query = _expenseSearchQuery.value ?: ""
        val statusFilter = _expenseStatusFilter.value ?: "all"
        val rawList = _expensesRaw.value ?: emptyList()
        val names = _employeeNames.value ?: emptyMap()
        
        _expenses.value = rawList
            .filter { expense ->
                // Status filter
                when (statusFilter) {
                    "all" -> true
                    "pending" -> expense.status == com.fieldtrack.app.util.Constants.EXPENSE_STATUS_PENDING
                    "approved" -> expense.status == com.fieldtrack.app.util.Constants.EXPENSE_STATUS_APPROVED
                    "rejected" -> expense.status == com.fieldtrack.app.util.Constants.EXPENSE_STATUS_REJECTED
                    else -> true
                }
            }
            .filter { expense ->
                // Search filter
                if (query.isBlank()) {
                    true
                } else {
                    val employeeName = names[expense.userId] ?: expense.userId
                    expense.matchesSearch(query, employeeName)
                }
            }
    }

    fun selectEmployee(user: User) {
        _selectedEmployee.value = user
    }

    fun clearSelectedEmployee() {
        _selectedEmployee.value = null
    }

    fun loadPendingEmployees() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = adminRepository.getPendingEmployees()) {
                is NetworkResult.Success -> {
                    _pendingEmployeesRaw.value = result.data
                    applyPendingSearchFilter()
                }
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
            _isLoading.value = false
        }
    }

    /**
     * Fetch all employees, then fetch the latest location for each concurrently.
     * Results are emitted as a list of [EmployeeLocation] for map markers.
     */
    fun loadEmployeeLocations() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val empResult = adminRepository.getAllEmployees()) {
                is NetworkResult.Success -> {
                    val employees = empResult.data
                    // Limit concurrent network calls to avoid overwhelming backend
                    val semaphore = Semaphore(5)
                    val locationResults = employees.map { employee ->
                        async {
                            semaphore.withPermit {
                                when (val locResult = locationRepository.fetchRemoteLocations(employee.id)) {
                                    is NetworkResult.Success -> {
                                        val loc = locResult.data.firstOrNull()
                                        EmployeeLocation(
                                            userId = employee.id,
                                            name = employee.name,
                                            latitude = loc?.latitude ?: 0.0,
                                            longitude = loc?.longitude ?: 0.0,
                                            lastUpdated = loc?.timestamp ?: ""
                                        )
                                    }
                                    is NetworkResult.Error -> {
                                        EmployeeLocation(
                                            userId = employee.id,
                                            name = employee.name,
                                            latitude = 0.0,
                                            longitude = 0.0,
                                            lastUpdated = ""
                                        )
                                    }
                                    is NetworkResult.Loading -> null
                                }
                            }
                        }
                    }.awaitAll()

                    _employeeLocations.value = locationResults.filterNotNull()
                }
                is NetworkResult.Error -> _toastMessage.value = empResult.message
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

    // ── Expense management ──────────────────────────────────────────

    fun loadAllExpenses() {
        _isLoading.value = true
        viewModelScope.launch {
            // Load employee names if not yet cached
            if (_employeeNames.value.isNullOrEmpty()) {
                when (val empResult = adminRepository.getAllEmployees()) {
                    is NetworkResult.Success -> {
                        _employeeNames.value = empResult.data.associate { it.id to it.name }
                    }
                    else -> { /* names will fall back to userId */ }
                }
            }

            when (val result = expenseRepository.fetchAllExpenses()) {
                is NetworkResult.Success -> {
                    _expensesRaw.value = result.data
                    applyExpenseSearchFilter()
                }
                is NetworkResult.Error -> {
                    _toastMessage.value = result.message
                }
                is NetworkResult.Loading -> { /* no-op */ }
            }
            _isLoading.value = false
        }
    }

    fun approveExpense(expenseId: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.updateExpenseStatus(expenseId, "approved")) {
                is NetworkResult.Success -> {
                    _toastMessage.value = "Expense approved"
                    loadAllExpenses()
                }
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
        }
    }

    fun rejectExpense(expenseId: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.updateExpenseStatus(expenseId, "rejected")) {
                is NetworkResult.Success -> {
                    _toastMessage.value = "Expense rejected"
                    loadAllExpenses()
                }
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
        }
    }

    fun getAuthToken(): String? {
        return sessionManager.accessToken
    }
}
