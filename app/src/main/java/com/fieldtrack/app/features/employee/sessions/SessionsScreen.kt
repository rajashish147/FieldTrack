package com.fieldtrack.app.features.employee.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*
import com.fieldtrack.app.core.network.dto.AttendanceSession

@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            FtTopBar(
                title = "My Sessions",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh",
                             tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter tabs
            val filters = listOf(
                "all" to "All",
                "active" to "Active",
                "recent" to "Recent",
                "inactive" to "Inactive"
            )
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.SM),
                contentPadding = PaddingValues(vertical = Spacing.MD),
            ) {
                if (state.isLoading && state.sessions.isEmpty()) {
                    items(5) { SkeletonListItem() }
                    return@LazyColumn
                }

                if (state.sessions.isEmpty()) {
                    item {
                        EmptyState(
                            message = "No sessions yet. Check in to start tracking.",
                            icon = { Icon(Icons.Default.Timer, null,
                                         modifier = Modifier.size(48.dp),
                                         tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        )
                    }
                    return@LazyColumn
                }

                items(state.sessions, key = { it.id }) { session ->
                    SessionItem(session)
                }

                if (state.hasMore) {
                    item {
                        LaunchedEffect(Unit) { viewModel.loadMore() }
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.MD),
                            contentAlignment = androidx.compose.ui.Alignment.Center) {
                            FtSpinner(size = SpinnerSize.Small)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(session: AttendanceSession) {
    val isActive = session.checkoutAt == null
    FtCardSmall(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                StatusBadge(
                    if (isActive) "Active" else "Completed",
                    if (isActive) BadgeVariant.SUCCESS else BadgeVariant.NEUTRAL,
                )
                Text(
                    text = session.checkinAt.take(10),
                    style = FtTypography.LabelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isActive) "In progress"
                           else "Out: ${session.checkoutAt?.take(16)?.replace("T", " ") ?: ""}",
                    style = FtTypography.BodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                session.totalDistanceKm?.let {
                    Text("${"%.1f".format(it)} km", style = FtTypography.LabelLarge,
                         color = MaterialTheme.colorScheme.primary)
                }
                session.totalDurationSeconds?.let { sec ->
                    val h = sec / 3600; val m = (sec % 3600) / 60
                    Text("${h}h ${m}m", style = FtTypography.BodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
