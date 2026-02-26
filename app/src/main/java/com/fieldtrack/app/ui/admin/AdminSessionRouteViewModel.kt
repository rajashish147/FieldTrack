package com.fieldtrack.app.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.util.RouteCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for AdminSessionRouteFragment.
 *
 * Loads GPS locations for a session, filters out inaccurate points, and
 * calculates total route distance using the shared [RouteCalculator].
 *
 * Threading model:
 *   - [locationRepository.getLocationsForSession] is a suspend function; its
 *     implementation uses withContext(Dispatchers.IO) internally.
 *   - Accuracy filtering and distance calculation are CPU-bound and are
 *     dispatched to [Dispatchers.Default] so the main thread is never blocked.
 *
 * Accuracy policy:
 *   Points where [Location.accuracy] > [ACCURACY_THRESHOLD_METERS] are excluded
 *   from both the rendered polyline and the distance calculation.
 *   Threshold is 50 m — tighter than RouteCalculator's default 100 m — to keep
 *   the admin route view as precise as possible.
 */
class AdminSessionRouteViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    // ── Exposed state ────────────────────────────────────────────────────────

    /** Accuracy-filtered GPS points for the polyline renderer. */
    private val _routePoints = MutableLiveData<List<Location>>()
    val routePoints: LiveData<List<Location>> = _routePoints

    /**
     * Total route distance in kilometres, rounded to 2 decimal places.
     * Computed by [RouteCalculator] using [DistanceCalculator.haversineDistance].
     * 0.0 when the session has no valid locations.
     */
    private val _totalDistanceKm = MutableLiveData<Double>()
    val totalDistanceKm: LiveData<Double> = _totalDistanceKm

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // ── Constants ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Maximum GPS accuracy radius (metres) to include a point in the route.
         * Points with [Location.accuracy] > this value are silently discarded.
         *
         * 50 m is intentionally stricter than [RouteCalculator.DEFAULT_ACCURACY_THRESHOLD]
         * (100 m) because the admin route view prioritises precision over coverage.
         */
        const val ACCURACY_THRESHOLD_METERS = 50f
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Load, filter, and calculate the route for [sessionId].
     *
     * State transitions:
     *   isLoading = true
     *   → fetch locations (IO thread, inside repository)
     *   → filter + calculate distance (Default thread)
     *   → emit routePoints + totalDistanceKm (Main thread)
     *   → isLoading = false
     *
     * On failure the error message is emitted via [error] and both
     * [routePoints] and [totalDistanceKm] are reset to safe empty values.
     *
     * @param sessionId UUID of the attendance session to render.
     */
    fun loadSessionRoute(sessionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // 1. Fetch raw locations (suspend; repository runs on Dispatchers.IO).
                val locations = locationRepository.getLocationsForSession(sessionId)

                // 2. Filter and calculate off the main thread.
                //    Two independent results are returned together to avoid two
                //    separate withContext calls for the same data set.
                val (filteredForMap, distanceKm) = withContext(Dispatchers.Default) {

                    // Accuracy-filtered list for polyline rendering.
                    // RouteCalculator internally applies the same filter to build
                    // RouteResult.points (List<GeoPoint>), but _routePoints needs
                    // List<Location> so the map renderer can access full domain fields.
                    val filtered = locations.filter { it.accuracy <= ACCURACY_THRESHOLD_METERS }

                    // RouteCalculator handles sessionId-null gracefully (returns null),
                    // covers the Haversine formula via DistanceCalculator, and applies
                    // the same accuracy threshold — no custom Haversine needed here.
                    val route = RouteCalculator.calculateRoute(
                        locations = locations,
                        accuracyThreshold = ACCURACY_THRESHOLD_METERS
                    )

                    // Truncate to 2 decimal places to match display precision.
                    val km = if (route != null) {
                        String.format("%.2f", route.distanceKm).toDouble()
                    } else {
                        0.0
                    }

                    Pair(filtered, km)
                }

                // 3. Post results back to Main (LiveData.value is main-thread-only).
                _routePoints.value = filteredForMap
                _totalDistanceKm.value = distanceKm

            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load route"
                _routePoints.value = emptyList()
                _totalDistanceKm.value = 0.0
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
