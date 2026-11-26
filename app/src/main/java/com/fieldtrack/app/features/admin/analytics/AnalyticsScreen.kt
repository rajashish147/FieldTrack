package com.fieldtrack.app.features.admin.analytics

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.TopPerformer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val topPerformers: List<TopPerformer> = emptyList(),
    val period: String = "week",
    val error: String? = null,
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(AnalyticsUiState())
    val state: StateFlow<AnalyticsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val performers = api.getTopPerformers(period = _state.value.period, limit = 10)
                _state.update { it.copy(
                    isLoading = false,
                    topPerformers = if (performers.isSuccessful) performers.body()?.data ?: emptyList() else emptyList()
                )}
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setPeriod(period: String) {
        _state.update { it.copy(period = period) }
        load()
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            FtTopBar(
                title = "Analytics",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.MD, vertical = Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            // Period filter chips
            val periods = listOf("week" to "This Week", "month" to "This Month", "year" to "This Year")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                periods.forEach { (p, label) ->
                    FilterChip(
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                        label = { Text(label, style = FtTypography.LabelMedium) },
                        shape = FtShape.Pill,
                    )
                }
            }

            if (state.isLoading) {
                repeat(5) { SkeletonListItem() }
                return@Column
            }

            // ── Top Performers ────────────────────────────────────────────────
            if (state.topPerformers.isNotEmpty()) {
                SectionContainer(title = "Top Performers") {
                    state.topPerformers.forEachIndexed { i, performer ->
                        ListItemCard(
                            title = "${performer.rank}. ${performer.employeeName}",
                            subtitle = "${"%.1f".format(performer.totalDistanceKm)} km · ${performer.totalSessions} sessions",
                            leading = {
                                when (performer.rank) {
                                    1 -> Icon(Icons.Default.EmojiEvents, null, tint = AccentYellow,
                                              modifier = Modifier.size(28.dp))
                                    2 -> Icon(Icons.Default.EmojiEvents, null, tint = AccentGray,
                                              modifier = Modifier.size(24.dp))
                                    3 -> Icon(Icons.Default.EmojiEvents, null, tint = AccentWarning,
                                              modifier = Modifier.size(22.dp))
                                    else -> Box(Modifier.size(20.dp))
                                }
                            },
                            trailing = {
                                KpiCard(
                                    title = "",
                                    value = "${"%.1f".format(performer.totalDistanceKm)}",
                                    subtitle = "km",
                                    modifier = Modifier.width(80.dp),
                                )
                            }
                        )
                        if (i < state.topPerformers.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            } else {
                EmptyState(message = "No analytics data for this period")
            }
        }
    }
}
