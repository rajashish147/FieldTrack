package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.databinding.FragmentEmployeeMapBinding
import com.fieldtrack.app.util.toReadableDateTime
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * EmployeeMapFragment — shows a single employee's last known location on a map.
 * Receives employee data via Bundle arguments from LiveTrackingFragment.
 */
class EmployeeMapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentEmployeeMapBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null

    private val employeeName by lazy { arguments?.getString("name") ?: "Employee" }
    private val latitude by lazy { arguments?.getFloat("latitude")?.toDouble() ?: 0.0 }
    private val longitude by lazy { arguments?.getFloat("longitude")?.toDouble() ?: 0.0 }
    private val lastUpdated by lazy { arguments?.getString("lastUpdated") ?: "" }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployeeMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvEmployeeName.text = employeeName

        if (lastUpdated.isEmpty()) {
            // No location data — show empty state, hide map
            binding.tvNoLocation.visibility = View.VISIBLE
        } else {
            // Initialize map
            val mapFragment = childFragmentManager
                .findFragmentById(binding.map.id) as? SupportMapFragment
            mapFragment?.getMapAsync(this)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        val position = LatLng(latitude, longitude)

        map.addMarker(
            MarkerOptions()
                .position(position)
                .title(employeeName)
                .snippet("Last synced: ${lastUpdated.toReadableDateTime()}")
        )?.showInfoWindow()

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }
}
