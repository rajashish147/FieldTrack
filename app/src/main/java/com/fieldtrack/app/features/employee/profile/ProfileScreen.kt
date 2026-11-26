package com.fieldtrack.app.features.employee.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            FtTopBar(
                title = "My Profile",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.MD, vertical = Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            if (state.isLoading) {
                SkeletonCard()
                SkeletonCard()
                return@Column
            }

            val profile = state.profile

            // ── Identity Card ────────────────────────────────────────────────
            FtCard(modifier = Modifier.fillMaxWidth()) {
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
                                text = (profile?.name?.firstOrNull() ?: "?").toString().uppercase(),
                                style = FtTypography.H3,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column {
                        Text(profile?.name ?: "—", style = FtTypography.H4,
                             color = MaterialTheme.colorScheme.onSurface)
                        profile?.employeeCode?.let {
                            Text("Code: $it", style = FtTypography.BodySmall,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusBadge(
                            text = profile?.activityStatus?.lowercase()
                                ?.replaceFirstChar { it.uppercase() } ?: "Inactive",
                            variant = (profile?.activityStatus ?: "INACTIVE").toBadgeVariant(),
                        )
                    }
                }
            }

            // ── Stats Card ────────────────────────────────────────────────────
            SectionContainer(title = "Stats This Period") {
                profile?.stats?.let { stats ->
                    listOf(
                        "Total Sessions" to "${stats.totalSessions}",
                        "Distance"       to "${"%.1f".format(stats.totalDistanceKm)} km",
                        "Expenses"       to "${stats.expensesSubmitted} submitted, ${stats.expensesApproved} approved",
                    ).forEach { (label, value) ->
                        ListItemCard(title = label, subtitle = value)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Logout ────────────────────────────────────────────────────────
            SecondaryButton(
                text = "Sign Out",
                onClick = { viewModel.logout(onLogout) },
                fullWidth = true,
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, null,
                              tint = MaterialTheme.colorScheme.onSurfaceVariant,
                              modifier = Modifier.size(18.dp)) }
            )
        }
    }
}
