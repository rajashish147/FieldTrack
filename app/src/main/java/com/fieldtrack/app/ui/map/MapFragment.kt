package com.fieldtrack.app.ui.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.databinding.FragmentMapBinding
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.util.currentDateString
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMap()
        observeViewModel()
        loadTodayLocations()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)
    }

    private fun loadTodayLocations() {
        val userId = sessionManager.userId ?: return
        viewModel.loadLocationsByDate(userId, currentDateString())
    }

    private fun observeViewModel() {
        viewModel.locations.observe(viewLifecycleOwner) { locations ->
            binding.mapView.overlays.clear()

            if (locations.isEmpty()) {
                binding.tvNoLocations.visibility = View.VISIBLE
                return@observe
            }

            binding.tvNoLocations.visibility = View.GONE

            val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }

            if (geoPoints.size > 1) {
                val polyline = Polyline().apply {
                    setPoints(geoPoints)
                    outlinePaint.color = Color.BLUE
                    outlinePaint.strokeWidth = 5f
                }
                binding.mapView.overlays.add(polyline)
            }

            geoPoints.firstOrNull()?.let { startPoint ->
                val startMarker = Marker(binding.mapView).apply {
                    position = startPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Start"
                }
                binding.mapView.overlays.add(startMarker)
            }

            geoPoints.lastOrNull()?.let { endPoint ->
                val endMarker = Marker(binding.mapView).apply {
                    position = endPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Current"
                }
                binding.mapView.overlays.add(endMarker)
                binding.mapView.controller.setCenter(endPoint)
            }

            binding.mapView.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
