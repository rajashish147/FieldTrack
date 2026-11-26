package com.fieldtrack.app.features.admin.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.fieldtrack.app.core.navigation.NavRoutes
import com.fieldtrack.app.core.network.dto.AdminSession

@Composable
fun AdminDashboardScreen(
    onNavigateToEmployees: () -> Unit,
    onNavigateToTracking: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            FtTopBar(
                title = "FieldTrack Admin",
                subtitle = "Operations Dashboard",
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, "Refresh",
                             tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        bottomBar = {
            FtBottomNavBar(
                items = adminNavItems(
                    onNavigateToEmployees, onNavigateToTracking,
                    onNavigateToExpenses, onNavigateToAnalytics,
                ),
                currentRoute = NavRoutes.ADMIN_DASHBOARD,
                onNavigate = { route ->
                    when (route) {
                        NavRoutes.ADMIN_EMPLOYEES -> onNavigateToEmployees()
                        NavRoutes.ADMIN_TRACKING  -> onNavigateToTracking()
                        NavRoutes.ADMIN_EXPENSES  -> onNavigateToExpenses()
                        NavRoutes.ADMIN_ANALYTICS -> onNavigateToAnalytics()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->

        if (state.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.MD),
            ) {
                Spacer(Modifier.height(Spacing.SM))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    SkeletonCard(Modifier.weight(1f))
                    SkeletonCard(Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                    SkeletonCard(Modifier.weight(1f))
                    SkeletonCard(Modifier.weight(1f))
                }
                repeat(3) { SkeletonListItem() }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.MD, vertical = Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            val s = state.orgSummary

            // ── KPI Grid ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                KpiCard(
                    title = "Active Now",
                    value = "${s?.activeToday ?: 0}",
                    subtitle = "Employees checked in",
                    highlighted = true,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(Icons.Default.People, null,
                             tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                             modifier = Modifier.size(20.dp))
                    }
                )
                KpiCard(
                    title = "Total Employees",
                    value = "${s?.totalEmployees ?: 0}",
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(Icons.Default.Group, null,
                             tint = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.size(20.dp))
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
            ) {
                KpiCard(
                    title = "Sessions",
                    value = "${s?.totalSessionsThisPeriod ?: 0}",
                    subtitle = "This week",
                    modifier = Modifier.weight(1f),
                )
                KpiCard(
                    title = "Pending",
                    value = "${s?.totalExpensesPending ?: 0}",
                    subtitle = "Expenses",
                    modifier = Modifier.weight(1f),
                    icon = {
                        if ((s?.totalExpensesPending ?: 0) > 0) {
                            Icon(Icons.Default.Notifications, null,
                                 tint = AccentWarning, modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }

            // ── Quick Actions ─────────────────────────────────────────────────
            SectionContainer(title = "Quick Actions") {
                listOf(
                    Triple(Icons.Default.Map, "Live Tracking", onNavigateToTracking),
                    Triple(Icons.Default.People, "Employees", onNavigateToEmployees),
                    Triple(Icons.Default.Receipt, "Expenses", onNavigateToExpenses),
                    Triple(Icons.Default.BarChart, "Analytics", onNavigateToAnalytics),
                ).forEachIndexed { i, (icon, label, action) ->
                    ListItemCard(
                        title = label,
                        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
                                         modifier = Modifier.size(24.dp)) },
                        trailing = { Icon(Icons.Default.ChevronRight, null,
                                          tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = action,
                    )
                    if (i < 3) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }

            // ── Recent Sessions ───────────────────────────────────────────────
            if (state.recentSessions.isNotEmpty()) {
                SectionContainer(title = "Recent Activity") {
                    state.recentSessions.take(5).forEachIndexed { i, session ->
                        ListItemCard(
                            title = session.employeeName ?: "Employee",
                            subtitle = session.checkinAt.take(16).replace("T", " "),
                            trailing = {
                                StatusBadge(
                                    text = session.activityStatus.lowercase().replaceFirstChar { it.uppercase() },
                                    variant = session.activityStatus.toBadgeVariant(),
                                )
                            },
                        )
                        if (i < 4) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun adminNavItems(
    onEmployees: () -> Unit,
    onTracking: () -> Unit,
    onExpenses: () -> Unit,
    onAnalytics: () -> Unit,
): List<NavItem> = listOf(
    NavItem(NavRoutes.ADMIN_DASHBOARD, "Dashboard", { Icon(Icons.Default.Dashboard, "Dashboard") }),
    NavItem(NavRoutes.ADMIN_EMPLOYEES, "Employees", { Icon(Icons.Default.People, "Employees") }),
    NavItem(NavRoutes.ADMIN_TRACKING,  "Tracking",  { Icon(Icons.Default.Map, "Tracking") }),
    NavItem(NavRoutes.ADMIN_EXPENSES,  "Expenses",  { Icon(Icons.Default.Receipt, "Expenses") }),
    NavItem(NavRoutes.ADMIN_ANALYTICS, "Analytics", { Icon(Icons.Default.BarChart, "Analytics") }),
)
