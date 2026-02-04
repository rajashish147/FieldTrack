package com.fieldtrack.app.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentHomeBinding
import com.fieldtrack.app.util.LocationUtils
import com.fieldtrack.app.util.showSnackbar
import com.fieldtrack.app.util.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                showCheckOutConfirmation()
            } else {
                attemptCheckIn()
            }
        }

        binding.btnSync.setOnClickListener {
            viewModel.syncData()
        }

        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    viewModel.logout(requireContext())
                    (activity as? DashboardActivity)?.navigateToLogin()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun attemptCheckIn() {
        if (!LocationUtils.hasLocationPermission(requireContext())) {
            requireContext().showToast("Location permission required")
            return
        }
        if (!LocationUtils.isGpsEnabled(requireContext())) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("GPS Required")
                .setMessage("GPS is turned off. Location tracking requires GPS to be enabled.\n\nWould you like to open location settings?")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (!LocationUtils.hasBackgroundLocationPermission(requireContext())) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Background Location")
                .setMessage("Background location access is recommended for reliable tracking during work hours.\n\nPlease grant \"Allow all the time\" in location settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                    })
                }
                .setNegativeButton("Continue Anyway") { _, _ ->
                    showCheckInConsent()
                }
                .show()
            return
        }
        if (LocationUtils.isMockLocationEnabled(requireContext())) {
            requireContext().showToast("Mock location detected. Please disable it.")
            return
        }

        showCheckInConsent()
    }

    private fun showCheckInConsent() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Check In")
            .setMessage("You are about to check in. Location tracking will start and remain active until you check out.\n\nDo you consent to location tracking during your work hours?")
            .setPositiveButton("Check In") { _, _ ->
                viewModel.checkIn(requireContext())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCheckOutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Check Out")
            .setMessage("You are about to check out. Location tracking will stop.\n\nAre you sure?")
            .setPositiveButton("Check Out") { _, _ ->
                viewModel.checkOut(requireContext())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isCheckedIn.observe(viewLifecycleOwner) { isCheckedIn ->
            if (isCheckedIn) {
                binding.btnCheckInOut.text = "Check Out"
                binding.btnCheckInOut.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.status_rejected)
                )
                binding.tvStatus.text = "Checked In"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_approved)
                )
                binding.tvWorkDuration.visibility = View.VISIBLE
                binding.tvCheckInTime.visibility = View.VISIBLE
            } else {
                binding.btnCheckInOut.text = "Check In"
                binding.btnCheckInOut.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.status_approved)
                )
                binding.tvStatus.text = "Checked Out"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_rejected)
                )
                binding.tvWorkDuration.visibility = View.GONE
                binding.tvCheckInTime.visibility = View.GONE
            }
        }

        viewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.tvWelcome.text = "Welcome, $name"
        }

        viewModel.checkInTime.observe(viewLifecycleOwner) { time ->
            if (time > 0) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                binding.tvCheckInTime.text = "Checked in at ${sdf.format(Date(time))}"
            }
        }

        viewModel.workDuration.observe(viewLifecycleOwner) { duration ->
            binding.tvWorkDuration.text = duration
        }

        viewModel.unsyncedCount.observe(viewLifecycleOwner) { count ->
            binding.tvUnsyncedCount.text = if (count > 0) {
                "$count unsynced location(s)"
            } else {
                "All data synced"
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnCheckInOut.isEnabled = !loading
            binding.btnSync.isEnabled = !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                binding.root.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
