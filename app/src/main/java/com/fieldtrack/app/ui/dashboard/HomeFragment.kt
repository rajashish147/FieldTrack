package com.fieldtrack.app.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fieldtrack.app.databinding.FragmentHomeBinding
import com.fieldtrack.app.util.LocationUtils
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnCheckInOut.setOnClickListener {
            val isCheckedIn = viewModel.isCheckedIn.value ?: false
            if (isCheckedIn) {
                viewModel.checkOut(requireContext())
            } else {
                if (!LocationUtils.hasLocationPermission(requireContext())) {
                    requireContext().showToast("Location permission required")
                    return@setOnClickListener
                }
                if (!LocationUtils.isGpsEnabled(requireContext())) {
                    requireContext().showToast("Please enable GPS")
                    return@setOnClickListener
                }
                if (LocationUtils.isMockLocationEnabled(requireContext())) {
                    requireContext().showToast("Mock location detected. Please disable it.")
                    return@setOnClickListener
                }
                viewModel.checkIn(requireContext())
            }
        }

        binding.btnSync.setOnClickListener {
            viewModel.syncData()
            requireContext().showToast("Syncing data...")
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout(requireContext())
            (activity as? DashboardActivity)?.navigateToLogin()
        }
    }

    private fun observeViewModel() {
        viewModel.isCheckedIn.observe(viewLifecycleOwner) { isCheckedIn ->
            if (isCheckedIn) {
                binding.btnCheckInOut.text = "Check Out"
                binding.btnCheckInOut.setBackgroundColor(
                    resources.getColor(android.R.color.holo_red_dark, null)
                )
                binding.tvStatus.text = "Status: Checked In"
                binding.tvStatus.setTextColor(
                    resources.getColor(android.R.color.holo_green_dark, null)
                )
            } else {
                binding.btnCheckInOut.text = "Check In"
                binding.btnCheckInOut.setBackgroundColor(
                    resources.getColor(android.R.color.holo_green_dark, null)
                )
                binding.tvStatus.text = "Status: Checked Out"
                binding.tvStatus.setTextColor(
                    resources.getColor(android.R.color.holo_red_dark, null)
                )
            }
        }

        viewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.tvWelcome.text = "Welcome, $name"
        }

        viewModel.unsyncedCount.observe(viewLifecycleOwner) { count ->
            binding.tvUnsyncedCount.text = if (count > 0) {
                "$count unsynced location(s)"
            } else {
                "All data synced"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
