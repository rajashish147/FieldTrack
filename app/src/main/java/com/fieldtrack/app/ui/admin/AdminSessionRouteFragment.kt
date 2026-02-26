package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentAdminSessionRouteBinding
import com.fieldtrack.app.util.showToast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminSessionRouteFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentAdminSessionRouteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminSessionRouteViewModel by viewModel()

    private var googleMap: GoogleMap? = null
    private var sessionId: String? = null

    companion object {
        private const val ARG_SESSION_ID = "session_id"

        fun newInstance(sessionId: String): AdminSessionRouteFragment {
            return AdminSessionRouteFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SESSION_ID, sessionId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionId = arguments?.getString(ARG_SESSION_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminSessionRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        observeViewModel()

        sessionId?.let { viewModel.loadSessionRoute(it) }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        viewModel.routePoints.value?.let { points ->
            if (points.isNotEmpty()) {
                renderRoute(points)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.routePoints.observe(viewLifecycleOwner) { points ->
            googleMap?.let { map ->
                renderRoute(points)
            }
        }

        viewModel.totalDistanceKm.observe(viewLifecycleOwner) { distance ->
            binding.tvDistance.text = "Total Distance: ${"%.2f".format(distance)} km"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                requireContext().showToast(it)
                viewModel.clearError()
            }
        }
    }

    private fun renderRoute(points: List<com.fieldtrack.app.data.model.Location>) {
        val map = googleMap ?: return

        map.clear()

        if (points.size < 2) {
            binding.tvNoData.visibility = View.VISIBLE
            binding.tvNoData.text = "Insufficient route data"
            return
        }

        binding.tvNoData.visibility = View.GONE

        val latLngList = points.map { LatLng(it.latitude, it.longitude) }

        val polylineOptions = PolylineOptions()
            .addAll(latLngList)
            .width(8f)
            .color(ContextCompat.getColor(requireContext(), R.color.route_polyline))

        map.addPolyline(polylineOptions)

        val startPoint = latLngList.first()
        map.addMarker(
            MarkerOptions()
                .position(startPoint)
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        val endPoint = latLngList.last()
        map.addMarker(
            MarkerOptions()
                .position(endPoint)
                .title("End")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        val boundsBuilder = LatLngBounds.Builder()
        latLngList.forEach { boundsBuilder.include(it) }
        val bounds = boundsBuilder.build()

        val padding = 100
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }
}
