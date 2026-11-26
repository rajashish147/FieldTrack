package com.fieldtrack.app.features.admin.employees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
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

// ─── EmployeesScreen ──────────────────────────────────────────────────────────

@Composable
fun EmployeesScreen(
    onEmployeeClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: EmployeesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            FtTopBar(
                title = "Employees",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
        ) {
            // Search bar
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.MD, vertical = Spacing.SM)) {
                InputField(
                    value = searchText,
                    onValueChange = { searchText = it; viewModel.search(it) },
                    label = "",
                    placeholder = "Search by name or code…",
                    leadingIcon = {
                        Icon(Icons.Default.Search, null,
                             tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                )
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.Micro),
                contentPadding = PaddingValues(bottom = Spacing.MD),
            ) {
                if (state.isLoading && state.employees.isEmpty()) {
                    items(6) { SkeletonListItem() }
                    return@LazyColumn
                }

                if (state.employees.isEmpty()) {
                    item {
                        EmptyState(
                            message = if (searchText.isNotBlank()) "No employees matching \"$searchText\""
                                       else "No employees found",
                            icon = { Icon(Icons.Default.People, null, Modifier.size(48.dp),
                                         tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                    return@LazyColumn
                }

                items(state.employees, key = { it.id }) { employee ->
                    EmployeeListItem(employee, onClick = { onEmployeeClick(employee.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
