package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.databinding.FragmentLiveTrackingBinding

/**
 * LiveTrackingFragment — admin GPS live tracking view.
 *
 * TODO: Implement Google Maps with real-time employee location markers
 * TODO: Show checked-in employees on the map
 * TODO: Add employee selection/filtering
 * TODO: Poll or subscribe to location updates from Supabase
 */
class LiveTrackingFragment : Fragment() {

    private var _binding: FragmentLiveTrackingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
