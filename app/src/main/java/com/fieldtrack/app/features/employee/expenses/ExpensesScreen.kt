package com.fieldtrack.app.features.employee.expenses

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*

// ─── ExpensesScreen ───────────────────────────────────────────────────────────

@Composable
fun ExpensesScreen(
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.listState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            FtTopBar(
                title = "My Expenses",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSubmit,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Expense", style = FtTypography.Button) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Filter tabs
            val filters = listOf("all" to "All", "PENDING" to "Pending",
                                 "APPROVED" to "Approved", "REJECTED" to "Rejected",
                                 "processed" to "Processed")
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
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.SM),
                contentPadding = PaddingValues(vertical = Spacing.MD),
            ) {
                if (state.isLoading && state.expenses.isEmpty()) {
                    items(5) { SkeletonListItem() }
                    return@LazyColumn
                }

                if (state.expenses.isEmpty()) {
                    item {
                        EmptyState(
                            message = "No expenses found. Tap + to submit one.",
                            icon = { Icon(Icons.Default.Receipt, null,
                                         modifier = Modifier.size(48.dp),
                                         tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                    return@LazyColumn
                }

                items(state.expenses, key = { it.id }) { expense ->
                    ExpenseItem(expense)
                }

                if (state.hasMore) {
                    item {
                        LaunchedEffect(Unit) { viewModel.loadExpenses() }
                        Box(Modifier.fillMaxWidth().padding(Spacing.MD),
                            contentAlignment = Alignment.Center) { FtSpinner(SpinnerSize.Small) }
                    }
                }
            }
        }
    }
}
