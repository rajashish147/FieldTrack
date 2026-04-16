package com.fieldtrack.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.AdminSession
import com.fieldtrack.app.data.model.OpenSession
import com.fieldtrack.app.ui.UiState
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
 * Filter options for employee list.
 */
enum class EmployeeFilter {
    ALL,      // Show all employees
    ACTIVE,   // Show only checked-in employees (isCheckedIn == true)
    INACTIVE  // Show only checked-out employees (isCheckedIn == false)
}

/**
 * Lightweight projection for the tracking list and map detail — one per employee.
 */
data class EmployeeLocation(
    val userId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lastUpdated: String,  // ISO 8601 or empty string if never synced
    val activeSessionId: String? = null  // Current open session ID if checked in
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

    private var currentEmployeeFilter = EmployeeFilter.ALL

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



    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _pendingEmployeeCount = MutableLiveData(0)
    val pendingEmployeeCount: LiveData<Int> = _pendingEmployeeCount

    private val _pendingExpenseCount = MutableLiveData(0)
    val pendingExpenseCount: LiveData<Int> = _pendingExpenseCount

    // P2.1 - Admin Open Sessions
    private val _openSessions = MutableLiveData<UiState<List<OpenSession>>>()
    val openSessions: LiveData<UiState<List<OpenSession>>> = _openSessions

    // P2.2 - Admin Force Check-Out
    private val _forceLoading = MutableLiveData(false)
    val forceLoading: LiveData<Boolean> = _forceLoading

    private val _forceError = MutableLiveData<String?>()
    val forceError: LiveData<String?> = _forceError

    // v1.4 - Admin Session History
    private val _sessionHistory = MutableLiveData<UiState<List<AdminSession>>>()
    val sessionHistory: LiveData<UiState<List<AdminSession>>> = _sessionHistory

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

    fun applyEmployeeFilter(filter: EmployeeFilter) {
        currentEmployeeFilter = filter
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

        // Apply filter first
        val filteredByStatus = when (currentEmployeeFilter) {
            EmployeeFilter.ALL -> rawList
            EmployeeFilter.ACTIVE -> rawList.filter { it.isCheckedIn }
            EmployeeFilter.INACTIVE -> rawList.filter { !it.isCheckedIn }
        }

        // Then apply search
        _allEmployees.value = if (query.isBlank()) {
            filteredByStatus
        } else {
            filteredByStatus.filter { it.matchesSearch(query) }
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
                expense.matchesSearch(query)
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
                    _pendingEmployeeCount.value = result.data.size
                }
                is NetworkResult.Error -> _toastMessage.value = result.message
                is NetworkResult.Loading -> { /* no-op */ }
            }
            _isLoading.value = false
        }
    }

    /**
     * Fetch all employees, then fetch the latest location for each concurrently.
     * Also fetches open sessions to populate activeSessionId.
     * Results are emitted as a list of [EmployeeLocation] for map markers.
     */
    fun loadEmployeeLocations() {
        _isLoading.value = true
        viewModelScope.launch {
            // Fetch open sessions first
            val openSessionsMap = when (val sessionsResult = adminRepository.getOpenSessions()) {
                is NetworkResult.Success -> {
                    sessionsResult.data.associate { it.userId to it.sessionId }
                }
                else -> emptyMap()
            }

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
                                            lastUpdated = loc?.timestamp ?: "",
                                            activeSessionId = openSessionsMap[employee.id]
                                        )
                                    }
                                    is NetworkResult.Error -> {
                                        EmployeeLocation(
                                            userId = employee.id,
                                            name = employee.name,
                                            latitude = 0.0,
                                            longitude = 0.0,
                                            lastUpdated = "",
                                            activeSessionId = openSessionsMap[employee.id]
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
            when (val result = expenseRepository.fetchAllExpenses()) {
                is NetworkResult.Success -> {
                    _expensesRaw.value = result.data
                    applyExpenseSearchFilter()

                    // Update pending expense count
                    val pendingCount = result.data.count {
                        it.status == com.fieldtrack.app.util.Constants.EXPENSE_STATUS_PENDING
                    }
                    _pendingExpenseCount.value = pendingCount
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

    // P2.1 - Admin Open Sessions
    /**
     * Load all currently open attendance sessions (admin-only).
     * Updates openSessions LiveData with list of active sessions.
     */
    fun loadOpenSessions() {
        _openSessions.value = UiState.Loading
        viewModelScope.launch {
            when (val result = adminRepository.getOpenSessions()) {
                is NetworkResult.Success -> _openSessions.value = UiState.Success(result.data)
                is NetworkResult.Error   -> _openSessions.value = UiState.Error(result.message)
                is NetworkResult.Loading -> Unit
            }
        }
    }

    // P2.2 - Admin Force Check-Out
    /**
     * Force check-out a user and close their open session (admin-only).
     *
     * @param userId Target user's UUID
     * @param reason Optional reason for force check-out
     *
     * On success: Reloads open sessions list
     * On error: Exposes error message via forceError LiveData
     */
    fun forceCheckOut(userId: String, reason: String?) {
        _forceLoading.value = true
        _forceError.value = null
        viewModelScope.launch {
            when (val result = adminRepository.forceCheckOut(userId, reason)) {
                is NetworkResult.Success -> {
                    _toastMessage.value = "Session closed"
                    loadOpenSessions()
                }
                is NetworkResult.Error -> {
                    _forceError.value = result.message
                }
                is NetworkResult.Loading -> { /* no-op */ }
            }
            _forceLoading.value = false
        }
    }

    fun clearForceError() {
        _forceError.value = null
    }

    // v1.4 - Admin Session History ─────────────────────────────────────────

    /**
     * Load full attendance session history (open + closed) for a specific employee.
     *
     * Calls admin_list_sessions_for_user() RPC via AdminRepository.
     * An empty list is a valid success — the employee simply has no sessions yet.
     *
     * State transitions:
     *   UiState.Loading → request in flight
     *   UiState.Success → list emitted (may be empty)
     *   UiState.Error   → user-facing message from the repository
     *
     * @param userId Target employee's UUID (must be non-empty, validated by RPC)
     */
    fun loadSessionHistory(userId: String) {
        _sessionHistory.value = UiState.Loading
        viewModelScope.launch {
            when (val result = adminRepository.getSessionsForUser(userId)) {
                is NetworkResult.Success -> {
                    _sessionHistory.value = UiState.Success(result.data)
                }
                is NetworkResult.Error -> {
                    _sessionHistory.value = UiState.Error(result.message)
                }
                is NetworkResult.Loading -> Unit
            }
        }
    }
}
