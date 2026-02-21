package com.fieldtrack.app.ui.route

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.model.RouteResult
import com.fieldtrack.app.databinding.FragmentSessionRouteBinding
import com.fieldtrack.app.util.showToast
import com.fieldtrack.app.util.toReadableDateTime
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Phase 2 Beta: Session route visualization fragment.
 * 
 * Displays a Google Map showing a session's tracked route as a polyline
 * with distance and point count summary.
 * 
 * **UI LAYER ONLY** - No business logic, no data fetching.
 * 
 * Responsibilities:
 * - Observe RouteViewModel state
 * - Convert GeoPoint → LatLng (UI layer conversion)
 * - Render polyline on Google Maps
 * - Display route summary (distance, points, time)
 * - Handle loading, empty, and error states
 * 
 * Architecture:
 * - MVVM pattern (observes ViewModel)
 * - Lifecycle-safe (uses viewLifecycleOwner)
 * - Defensive UI (null checks, empty states)
 * 
 * Usage:
 * Pass sessionId via arguments:
 * ```
 * val bundle = Bundle().apply {
 *     putString("sessionId", "abc-123")
 * }
 * findNavController().navigate(R.id.sessionRouteFragment, bundle)
 * ```
 */
class SessionRouteFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentSessionRouteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RouteViewModel by viewModel()
    private var googleMap: GoogleMap? = null
    
    private var sessionId: String? = null
    
    // Admin fix: Accept userId parameter for viewing employee routes
    private var userId: String? = null
    
    // Production hardening: Prevent camera re-zoom on configuration change
    private var hasInitialZoom = false
    
    // Production hardening: Prevent duplicate polyline rendering
    private var lastRenderedSessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extract sessionId and userId from arguments
        sessionId = arguments?.getString(ARG_SESSION_ID)
        userId = arguments?.getString(ARG_USER_ID)  // Admin fix: Extract userId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSessionRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup map
        val mapFragment = childFragmentManager.findFragmentById(binding.map.id) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Setup retry button
        binding.btnRetry.setOnClickListener {
            viewModel.retry(sessionId, userId)  // Admin fix: Pass userId
        }

        // Observe ViewModel state
        observeViewModel()

        // Load route if sessionId is valid
        if (sessionId.isNullOrBlank()) {
            showEmptyState("Invalid session ID")
        } else {
            viewModel.loadRoute(sessionId, userId)  // Admin fix: Pass userId
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Configure map UI
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        // If route data arrived before map was ready, render it now
        // Production hardening: Only zoom on first render (not on rotation)
        val currentState = viewModel.routeState.value
        if (currentState is RouteState.RouteAvailable) {
            renderRoute(currentState.route, shouldZoom = !hasInitialZoom)
            hasInitialZoom = true
        }
    }

    private fun observeViewModel() {
        viewModel.routeState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RouteState.Initial -> {
                    // Initial state - do nothing
                }
                
                is RouteState.Loading -> {
                    showLoading()
                }
                
                is RouteState.RouteAvailable -> {
                    hideLoading()
                    renderRoute(state.route, shouldZoom = !hasInitialZoom)
                    hasInitialZoom = true
                }
                
                is RouteState.NoRouteData -> {
                    hideLoading()
                    showEmptyState(state.message)
                }
                
                is RouteState.Error -> {
                    hideLoading()
                    showErrorState(state.message, state.isRecoverable)
                }
            }
        }
    }

    /**
     * Render route on Google Maps.
     * 
     * **CRITICAL: GeoPoint → LatLng conversion happens HERE (UI layer only)**
     * 
     * Process:
     * 1. Convert GeoPoint → LatLng (UI framework conversion)
     * 2. Draw polyline through all points
     * 3. Add start/end markers
     * 4. Auto-zoom camera to route bounds (optional)
     * 5. Display summary card
     * 
     * @param route The route data to render
     * @param shouldZoom Whether to auto-zoom camera (false on rotation to preserve user's view)
     */
    private fun renderRoute(route: RouteResult, shouldZoom: Boolean = true) {
        val map = googleMap
        
        // Show summary card
        displaySummary(route)
        
        // If map not ready yet, wait for onMapReady callback
        if (map == null) return
        
        // Production hardening: Prevent duplicate rendering of same route
        val currentSessionId = sessionId
        if (lastRenderedSessionId == currentSessionId && !shouldZoom) {
            // Route already rendered, skip redraw
            return
        }
        lastRenderedSessionId = currentSessionId
        
        // Clear previous route (CRITICAL: always clear before redraw)
        map.clear()
        
        // Validate route has points
        if (route.points.isEmpty()) {
            showEmptyState("Route has no points")
            return
        }
        
        // **UI LAYER CONVERSION: GeoPoint → LatLng**
        // This is the ONLY place where framework-specific conversion happens
        val latLngPoints = route.points.map { geoPoint ->
            LatLng(geoPoint.latitude, geoPoint.longitude)
        }
        
        // Production safety: Cap polyline points to prevent OOM on extreme sessions
        // Normal sessions: 50-500 points
        // Long sessions: 500-1000 points
        // Extreme sessions: 1000-2000 points (cap here)
        val cappedPoints = if (latLngPoints.size > 2000) {
            if (BuildConfig.DEBUG) {
                android.util.Log.w(
                    "SessionRouteFragment",
                    "Route has ${latLngPoints.size} points, capping to 2000 for performance"
                )
            }
            // Production logging: Track extreme sessions
            android.util.Log.w(
                "SessionRouteFragment",
                "Extreme session detected: ${latLngPoints.size} points capped to 2000"
            )
            
            // Simple decimation: Take every Nth point to reach ~2000 points
            val step = latLngPoints.size / 2000
            latLngPoints.filterIndexed { index, _ -> index % step == 0 }
                .take(2000)
        } else {
            latLngPoints
        }
        
        // Draw polyline
        val polylineOptions = PolylineOptions()
            .addAll(cappedPoints)
            .color(Color.parseColor("#4285F4")) // Google Blue
            .width(8f)
        
        map.addPolyline(polylineOptions)
        
        // Add start marker (green)
        val startPoint = cappedPoints.first()
        map.addMarker(
            MarkerOptions()
                .position(startPoint)
                .title("Start")
                .snippet(route.startTime?.toReadableDateTime() ?: "Unknown time")
        )
        
        // Add end marker (red) if different from start
        if (cappedPoints.size > 1) {
            val endPoint = cappedPoints.last()
            map.addMarker(
                MarkerOptions()
                    .position(endPoint)
                    .title("End")
                    .snippet(route.endTime?.toReadableDateTime() ?: "Unknown time")
            )
        }
        
        // Auto-zoom camera to fit route bounds (only if requested)
        // Production hardening: Skip zoom on rotation to preserve user's view
        if (shouldZoom) {
            zoomToRoute(cappedPoints)
        }
        
        // Hide empty/error states
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
    }

    /**
     * Display route summary card.
     * 
     * Shows:
     * - Distance (formatted)
     * - Points used vs discarded
     * - Time range (start - end)
     */
    private fun displaySummary(route: RouteResult) {
        binding.summaryCard.visibility = View.VISIBLE
        
        // Distance
        binding.tvDistance.text = route.formatDistance()
        
        // Points info
        val pointsText = if (route.discardedPointCount > 0) {
            "${route.filteredPointCount} points used (${route.discardedPointCount} discarded)"
        } else {
            "${route.filteredPointCount} points used"
        }
        binding.tvPoints.text = pointsText
        
        // Time range
        val timeRangeText = when {
            route.startTime != null && route.endTime != null -> {
                val start = route.startTime.toReadableDateTime()
                val end = route.endTime.toReadableDateTime()
                "$start - $end"
            }
            route.startTime != null -> {
                "Started: ${route.startTime.toReadableDateTime()}"
            }
            else -> {
                "Time unknown"
            }
        }
        binding.tvTimeRange.text = timeRangeText
    }

    /**
     * Auto-zoom camera to fit route bounds with padding.
     * 
     * Handles edge cases:
     * - Single point: Zoom to fixed level
     * - Multiple points: Fit bounds with padding
     * - Exception: Fallback to first point
     */
    private fun zoomToRoute(points: List<LatLng>) {
        val map = googleMap ?: return
        
        if (points.isEmpty()) return
        
        try {
            if (points.size == 1) {
                // Single point - zoom to fixed level
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 16f))
            } else {
                // Multiple points - fit bounds
                val boundsBuilder = LatLngBounds.Builder()
                points.forEach { boundsBuilder.include(it) }
                val bounds = boundsBuilder.build()
                
                // Padding in pixels (100px on all sides)
                val padding = 100
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
            }
        } catch (e: Exception) {
            // Fallback: zoom to first point
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        }
    }

    /**
     * Show loading state.
     */
    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.summaryCard.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
    }

    /**
     * Hide loading state.
     */
    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
    }

    /**
     * Show empty state with message.
     * 
     * Use cases:
     * - No route data for session
     * - All points filtered out
     * - Invalid sessionId
     */
    private fun showEmptyState(message: String) {
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = message
        
        binding.summaryCard.visibility = View.GONE
        binding.errorState.visibility = View.GONE
        
        // Clear map
        googleMap?.clear()
    }

    /**
     * Show error state with message and optional retry button.
     * 
     * Use cases:
     * - Network error
     * - Database error
     * - Unexpected exception
     */
    private fun showErrorState(message: String, isRecoverable: Boolean) {
        binding.errorState.visibility = View.VISIBLE
        binding.tvErrorMessage.text = message
        binding.btnRetry.visibility = if (isRecoverable) View.VISIBLE else View.GONE
        
        binding.summaryCard.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        
        // Clear map
        googleMap?.clear()
        
        // Show toast for user feedback
        requireContext().showToast(message)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Reset ViewModel state
        viewModel.reset()
        
        // Production hardening: Reset tracking flags
        lastRenderedSessionId = null
        hasInitialZoom = false
        
        // Clean up references
        googleMap = null
        _binding = null
    }

    companion object {
        private const val ARG_SESSION_ID = "sessionId"
        private const val ARG_USER_ID = "userId"  // Admin fix: Add userId argument
        
        /**
         * Create new instance with sessionId and optional userId.
         * 
         * @param sessionId The attendance session ID to display route for
         * @param userId The user ID who owns the locations (for admin viewing employee routes, null = current user)
         * @return Fragment instance with arguments
         */
        fun newInstance(sessionId: String, userId: String? = null): SessionRouteFragment {
            return SessionRouteFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SESSION_ID, sessionId)
                    userId?.let { putString(ARG_USER_ID, it) }
                }
            }
        }
    }
}
