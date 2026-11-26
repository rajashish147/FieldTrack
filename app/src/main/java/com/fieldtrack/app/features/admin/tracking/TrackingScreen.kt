package com.fieldtrack.app.features.admin.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.fieldtrack.app.core.components.*
import com.fieldtrack.app.core.designsystem.*
import com.fieldtrack.app.core.network.ApiService
import com.fieldtrack.app.core.network.dto.EmployeeMapMarker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class TrackingUiState(
    val isLoading: Boolean = true,
    val markers: List<EmployeeMapMarker> = emptyList(),
    val error: String? = null,
    val updatedAt: Instant? = null,
)

@HiltViewModel
class TrackingViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()
    private val fetchLock = Mutex()

    fun refresh() { viewModelScope.launch { load() } }

    suspend fun load() = fetchLock.withLock {
        try {
            val response = api.getAdminMapMarkers()
            val markers = response.body()?.data
            if (response.isSuccessful && markers != null) {
                _state.value = TrackingUiState(isLoading = false, markers = markers, updatedAt = Instant.now())
            } else {
                _state.update { it.copy(isLoading = false, error = "Could not refresh locations (${response.code()}).") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Could not refresh locations.") }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TrackingScreen(
    onBack: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.load()
                delay(15_000)
            }
        }
    }

    Scaffold(
        topBar = {
            FtTopBar(
                title = "Live Tracking",
                subtitle = "${state.markers.size} employees on map",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                FullScreenLoader()
                return@Scaffold
            }

            // OSMDroid Map
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = "FieldTrack/2.0"
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(20.5937, 78.9629)) // India centre default
                        setMultiTouchControls(true)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()
                    val points = state.markers.map { marker ->
                        GeoPoint(marker.latitude, marker.longitude)
                    }
                    state.markers.forEach { marker ->
                        Marker(mapView).apply {
                            position = GeoPoint(marker.latitude, marker.longitude)
                            title = marker.employeeName
                            snippet = buildString {
                                append(locationAge(marker, state.updatedAt))
                                marker.employeeCode?.let { append(" · ").append(it) }
                            }
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }.also { mapView.overlays.add(it) }
                    }
                    if (!fitted && points.size == 1) {
                        mapView.controller.setZoom(15.0)
                        mapView.controller.setCenter(points.first())
                        fitted = true
                    } else if (!fitted && points.size > 1) {
                        val lats = points.map { it.latitude }
                        val lngs = points.map { it.longitude }
                        mapView.post {
                            mapView.zoomToBoundingBox(
                                BoundingBox(lats.max() + 0.001, lngs.max() + 0.001, lats.min() - 0.001, lngs.min() - 0.001),
                                true,
                            )
                        }
                        fitted = true
                    }
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.onDetach() },
            )

            // Active count badge overlay
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.MD),
                shape = FtShape.Pill,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.MD, vertical = Spacing.SM),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.FiberManualRecord, null,
                         tint = AccentSuccess, modifier = Modifier.size(8.dp))
                    val recent = state.markers.count { minutesSince(it, state.updatedAt)?.let { age -> age < 2 } == true }
                    Text("$recent of ${state.markers.size} updated in the last 2 min",
                         style = FtTypography.LabelMedium,
                         color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            state.error?.let { message ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.MD),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = FtShape.CardSmall,
                ) {
                    Text(
                        if (state.markers.isEmpty()) message else "$message Showing last known positions.",
                        modifier = Modifier.padding(Spacing.MD),
                        style = FtTypography.BodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

private fun minutesSince(marker: EmployeeMapMarker, reference: Instant?): Long? =
    runCatching {
        Duration.between(OffsetDateTime.parse(marker.recordedAt).toInstant(), reference ?: Instant.now())
            .toMinutes().coerceAtLeast(0)
    }.getOrNull()

private fun locationAge(marker: EmployeeMapMarker, reference: Instant?): String =
    when (val minutes = minutesSince(marker, reference)) {
        null -> "Location time unavailable"
        0L -> "Updated less than a minute ago"
        else -> "Updated $minutes min ago"
    }
