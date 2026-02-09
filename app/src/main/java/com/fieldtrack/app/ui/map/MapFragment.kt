package com.fieldtrack.app.ui.map

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.Location
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.databinding.FragmentMapBinding
import com.fieldtrack.app.util.currentDateString
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    private var googleMap: GoogleMap? = null

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
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map_container) as? SupportMapFragment
        mapFragment?.getMapAsync { map ->
            googleMap = map
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false
            loadTodayLocations()
        }
    }

    private fun loadTodayLocations() {
        val userId = sessionManager.userId ?: return
        viewModel.loadLocationsByDate(userId, currentDateString())
    }

    private fun observeViewModel() {
        viewModel.locations.observe(viewLifecycleOwner) { locations ->
            val map = googleMap ?: return@observe
            drawLocationsOnMap(map, locations)
        }
    }

    private fun drawLocationsOnMap(map: GoogleMap, locations: List<Location>) {
        map.clear()

        if (locations.isEmpty()) {
            binding.tvNoLocations.visibility = View.VISIBLE
            return
        }

        binding.tvNoLocations.visibility = View.GONE

        val latLngs = locations.map { LatLng(it.latitude, it.longitude) }

        if (latLngs.size > 1) {
            val polylineOptions = PolylineOptions()
                .addAll(latLngs)
                .color(Color.BLUE)
                .width(5f)
            map.addPolyline(polylineOptions)
        }

        latLngs.firstOrNull()?.let { startPoint ->
            map.addMarker(
                MarkerOptions()
                    .position(startPoint)
                    .title("Start")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
        }

        latLngs.lastOrNull()?.let { endPoint ->
            map.addMarker(
                MarkerOptions()
                    .position(endPoint)
                    .title("Current")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(endPoint, 15f))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }
}
