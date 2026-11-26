package com.fieldtrack.app.features.employee.expenses

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.data.local.PendingExpenseDao
import com.fieldtrack.app.core.data.local.PendingExpenseEntity
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.*
import com.fieldtrack.app.service.ExpenseRetryWorker
import com.fieldtrack.app.service.ExpenseUpload
import com.fieldtrack.app.service.ExpenseUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val expenses: List<Expense> = emptyList(),
    val error: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val statusFilter: String = "all",
)

data class SubmitExpenseUiState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val amount: String = "",
    val description: String = "",
)

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val api: ApiService,
    private val pendingExpenseDao: PendingExpenseDao,
    private val sessions: SessionManager,
    private val uploader: ExpenseUploader,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _listState   = MutableStateFlow(ExpensesUiState())
    val listState: StateFlow<ExpensesUiState> = _listState.asStateFlow()

    private val _submitState = MutableStateFlow(SubmitExpenseUiState())
    val submitState: StateFlow<SubmitExpenseUiState> = _submitState.asStateFlow()

    init {
        loadExpenses()
        // Schedule retry for any pending expenses from previous crash
        ExpenseRetryWorker.schedule(context)
    }

    fun loadExpenses(reset: Boolean = false) {
        viewModelScope.launch {
            val page = if (reset) 1 else _listState.value.page
            _listState.update { it.copy(isLoading = true, error = null) }
            try {
                val resp = api.getMyExpenses(
                    page   = page,
                    limit  = 20,
                    status = _listState.value.statusFilter,
                )
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    val newList = if (reset) body.data else _listState.value.expenses + body.data
                    _listState.update { it.copy(
                        isLoading = false,
                        expenses  = newList,
                        page      = page + 1,
                        hasMore   = body.pagination.page * body.pagination.limit < body.pagination.total,
                    )}
                } else {
                    _listState.update { it.copy(isLoading = false, error = "Failed to load expenses") }
                }
            } catch (e: Exception) {
                _listState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(status: String) {
        _listState.update { it.copy(statusFilter = status, page = 1) }
        loadExpenses(reset = true)
    }

    fun updateAmount(v: String)      { _submitState.update { it.copy(amount = v, error = null) } }
    fun updateDescription(v: String) { _submitState.update { it.copy(description = v, error = null) } }

    fun submitExpense(onSuccess: () -> Unit) {
        // Multi-tap protection
        if (_submitState.value.isLoading || _submitState.value.success) return

        val state  = _submitState.value
        val amount = state.amount.toDoubleOrNull()

        if (amount == null || !amount.isFinite() || amount <= 0) {
            _submitState.update { it.copy(error = "Enter a valid amount") }
            return
        }
        if (state.description.isBlank()) {
            _submitState.update { it.copy(error = "Description is required") }
            return
        }

        viewModelScope.launch {
            _submitState.update { it.copy(isLoading = true, error = null) }

            try {
                val owner = checkNotNull(sessions.getSession()?.owner) { "Sign in to submit an expense." }
                val pending = PendingExpenseEntity(
                    amount = amount,
                    description = state.description,
                    receiptUrl = null,
                    createdAt = Instant.now().toString(),
                    owner = owner,
                    idempotencyKey = UUID.randomUUID().toString(),
                )
                val pendingId = pendingExpenseDao.insert(pending)
                when (uploader.upload(pending.copy(id = pendingId))) {
                    ExpenseUpload.CONFIRMED -> {
                        _submitState.update { it.copy(isLoading = false, success = true) }
                        loadExpenses(reset = true)
                        onSuccess()
                    }
                    ExpenseUpload.RETRY -> {
                        ExpenseRetryWorker.schedule(context)
                        _submitState.update {
                            it.copy(isLoading = false, success = true, error = "Expense saved. It will sync when you reconnect.")
                        }
                    }
                    ExpenseUpload.REJECTED -> {
                        _submitState.update { it.copy(isLoading = false, error = "The server rejected this expense. Check the amount and description.") }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _submitState.update { it.copy(isLoading = false, error = e.message ?: "Could not save expense.") }
            }
        }
    }

    fun clearError() {
        _submitState.update { it.copy(error = null) }
        _listState.update { it.copy(error = null) }
    }
}
