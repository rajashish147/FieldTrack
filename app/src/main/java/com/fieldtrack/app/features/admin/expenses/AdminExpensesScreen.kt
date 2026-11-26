package com.fieldtrack.app.features.admin.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.Expense
import com.fieldtrack.app.core.network.dto.UpdateExpenseStatusRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class AdminExpensesUiState(
    val isLoading: Boolean = true,
    val expenses: List<Expense> = emptyList(),
    val statusFilter: String = "PENDING",
    val page: Int = 1,
    val hasMore: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null,
)

@HiltViewModel
class AdminExpensesViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(AdminExpensesUiState())
    val state: StateFlow<AdminExpensesUiState> = _state.asStateFlow()

    init { load() }

    fun load(reset: Boolean = false) {
        viewModelScope.launch {
            val page = if (reset) 1 else _state.value.page
            _state.update { it.copy(isLoading = true) }
            try {
                val resp = api.getAdminExpenses(page = page, limit = 20, status = _state.value.statusFilter)
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    val list = if (reset) body.data else _state.value.expenses + body.data
                    _state.update { it.copy(
                        isLoading = false,
                        expenses = list,
                        page = page + 1,
                        hasMore = body.pagination.page * body.pagination.limit < body.pagination.total,
                    ) }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Failed to load expenses") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setFilter(status: String) {
        _state.update { it.copy(statusFilter = status) }
        load(reset = true)
    }

    fun updateStatus(expenseId: String, status: String, comment: String? = null) {
        viewModelScope.launch {
            try {
                val resp = api.updateExpenseStatus(expenseId, UpdateExpenseStatusRequest(status, comment))
                if (resp.isSuccessful) {
                    _state.update { it.copy(
                        expenses = it.expenses.filter { e -> e.id != expenseId },
                        actionMessage = "Expense $status".lowercase().replaceFirstChar { c -> c.uppercase() },
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearMessage() { _state.update { it.copy(actionMessage = null) } }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun AdminExpensesScreen(
    onBack: () -> Unit,
    viewModel: AdminExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            FtTopBar(title = "Expense Management", showBackButton = true, onBackClick = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val filters = listOf("PENDING" to "Pending", "APPROVED" to "Approved",
                                 "REJECTED" to "Rejected", "processed" to "Processed",
                                 "all" to "All")
            ScrollableTabRow(
                selectedTabIndex = filters.indexOfFirst { it.first == state.statusFilter }.coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = Spacing.MD,
            ) {
                filters.forEach { (status, label) ->
                    Tab(
                        selected = state.statusFilter == status,
                        onClick = { viewModel.setFilter(status) },
                        text = { Text(label, style = FtTypography.LabelMedium) }
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.SM),
                contentPadding = PaddingValues(vertical = Spacing.MD),
            ) {
                if (state.isLoading && state.expenses.isEmpty()) {
                    items(5) { SkeletonListItem() }
                    return@LazyColumn
                }

                if (state.expenses.isEmpty()) {
                    item { EmptyState(message = "No expenses in this category") }
                    return@LazyColumn
                }

                items(state.expenses, key = { it.id }) { expense ->
                    AdminExpenseItem(
                        expense = expense,
                        onApprove = { viewModel.updateStatus(expense.id, "APPROVED") },
                        onReject  = { viewModel.updateStatus(expense.id, "REJECTED") },
                    )
                }

                if (state.hasMore) {
                    item {
                        LaunchedEffect(Unit) { viewModel.load() }
                        Box(Modifier.fillMaxWidth().padding(Spacing.MD), Alignment.Center) {
                            FtSpinner(SpinnerSize.Small)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminExpenseItem(
    expense: Expense,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    FtCardSmall(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = Spacing.SM),
                   verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(expense.description, style = FtTypography.LabelLarge,
                     color = MaterialTheme.colorScheme.onSurface)
                Text("By: ${expense.employeeName ?: "—"} · ₹${"%.2f".format(expense.amount)}",
                     style = FtTypography.BodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(expense.submittedAt.take(10), style = FtTypography.BodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(
                expense.status.lowercase().replaceFirstChar { it.uppercase() },
                expense.status.toBadgeVariant(),
            )
        }

        if (expense.status == "PENDING") {
            Spacer(Modifier.height(Spacing.SM))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                PrimaryButton(text = "Approve", onClick = onApprove, modifier = Modifier.weight(1f))
                SecondaryButton(text = "Reject",  onClick = onReject,  modifier = Modifier.weight(1f))
            }
        }
    }
}
