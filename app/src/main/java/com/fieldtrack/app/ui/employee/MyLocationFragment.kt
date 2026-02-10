package com.fieldtrack.app.ui.employee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.databinding.FragmentMyLocationBinding

/**
 * MyLocationFragment — employee's own location map view.
 *
 * TODO: Implement Google Maps integration to show employee's tracked route
 * TODO: Show today's location history on the map
 * TODO: Display accuracy info and last-synced timestamp
 */
class MyLocationFragment : Fragment() {

    private var _binding: FragmentMyLocationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
