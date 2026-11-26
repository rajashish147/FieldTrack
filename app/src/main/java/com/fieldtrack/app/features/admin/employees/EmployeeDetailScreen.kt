package com.fieldtrack.app.features.admin.employees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldtrack.app.core.components.ErrorState
import com.fieldtrack.app.core.components.FtCard
import com.fieldtrack.app.core.components.FtTopBar
import com.fieldtrack.app.core.components.ListItemCard
import com.fieldtrack.app.core.components.SectionContainer
import com.fieldtrack.app.core.components.SkeletonCard
import com.fieldtrack.app.core.components.StatusBadge
import com.fieldtrack.app.core.components.toBadgeVariant
import com.fieldtrack.app.core.designsystem.FtShape
import com.fieldtrack.app.core.designsystem.FtTypography
import com.fieldtrack.app.core.designsystem.Spacing

@Composable
fun EmployeeDetailScreen(
    employeeId: String,
    onBack: () -> Unit,
) {
    val viewModel: EmployeeDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            FtTopBar(title = "Employee Profile", showBackButton = true, onBackClick = onBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.isLoading) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.MD),
                verticalArrangement = Arrangement.spacedBy(Spacing.MD),
            ) {
                SkeletonCard()
                SkeletonCard()
            }
            return@Scaffold
        }

        state.error?.let {
            ErrorState(
                message = it,
                onRetry = { viewModel.load(employeeId) },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        val profile = state.profile
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.MD, vertical = Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            FtCard(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
                ) {
                    Surface(
                        shape = FtShape.Pill,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                (profile?.name?.firstOrNull() ?: "?").toString().uppercase(),
                                style = FtTypography.H3,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text(
                            profile?.name ?: "—",
                            style = FtTypography.H4,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        profile?.employeeCode?.let {
                            Text(
                                "Code: $it",
                                style = FtTypography.BodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusBadge(
                            text = profile?.activityStatus?.lowercase()
                                ?.replaceFirstChar { it.uppercase() } ?: "Inactive",
                            variant = (profile?.activityStatus ?: "INACTIVE").toBadgeVariant(),
                        )
                    }
                }
            }

            profile?.stats?.let { stats ->
                SectionContainer(title = "Performance Stats") {
                    listOf(
                        "Sessions" to "${stats.totalSessions}",
                        "Distance" to "${"%.1f".format(stats.totalDistanceKm)} km",
                        "Expenses Submitted" to "${stats.expensesSubmitted}",
                        "Expenses Approved" to "${stats.expensesApproved}",
                    ).forEachIndexed { i, (label, value) ->
                        ListItemCard(title = label, subtitle = value)
                        if (i < 3) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}
