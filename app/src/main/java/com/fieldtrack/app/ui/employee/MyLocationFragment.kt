package com.fieldtrack.app.ui.employee

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.databinding.FragmentMyLocationBinding
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
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * MyLocationFragment — employee's own location map view.
 *
 * Displays a Google Map showing today's tracked route as a polyline,
 * with a marker at the latest position. Data flows via [EmployeeHomeViewModel]
 * using a local Room Flow that updates in real-time as the service saves new points.
 */
class MyLocationFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMyLocationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmployeeHomeViewModel by activityViewModel()
    private var googleMap: GoogleMap? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtain the SupportMapFragment and request the map asynchronously
        val mapFragment = childFragmentManager.findFragmentById(binding.map.id) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        observeViewModel()
        viewModel.loadTodayLocations()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = false

        // If data arrived before the map was ready, render it now
        viewModel.todayLocations.value?.let { locations ->
            if (locations.isNotEmpty()) {
                updateRoute(locations)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.todayLocations.observe(viewLifecycleOwner) { locations ->
            updateRoute(locations)
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                requireContext().showToast(it)
                viewModel.clearToast()
            }
        }
    }

    /**
     * Draw today's tracked route as a polyline with a marker at the latest position.
     * Called reactively as the Room Flow emits new location data.
     */
    private fun updateRoute(locations: List<Location>) {
        val map = googleMap ?: return

        map.clear()

        if (locations.isEmpty()) {
            binding.tvNoLocations.visibility = View.VISIBLE
            return
        }

        binding.tvNoLocations.visibility = View.GONE

        val points = locations.map { LatLng(it.latitude, it.longitude) }

        // Draw polyline through all today's tracked points
        map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(Color.parseColor("#4285F4")) // Google Blue
                .width(6f)
        )

        // Add marker at latest position
        val latest = locations.last()
        val latestPos = LatLng(latest.latitude, latest.longitude)
        map.addMarker(
            MarkerOptions()
                .position(latestPos)
                .title("Current Location")
                .snippet("Last updated: ${latest.timestamp.toReadableDateTime()}")
        )

        // Zoom camera to fit route or focus on latest point
        if (points.size > 1) {
            try {
                val boundsBuilder = LatLngBounds.Builder()
                points.forEach { boundsBuilder.include(it) }
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            } catch (e: Exception) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(latestPos, 16f))
            }
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latestPos, 16f))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }
}
