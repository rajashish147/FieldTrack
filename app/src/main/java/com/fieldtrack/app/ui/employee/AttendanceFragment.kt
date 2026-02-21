package com.fieldtrack.app.ui.employee

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.R
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.databinding.FragmentAttendanceBinding
import com.fieldtrack.app.service.LocationTrackingService
import com.fieldtrack.app.service.LocationSyncWorker
import com.fieldtrack.app.ui.auth.LoginActivity
import com.fieldtrack.app.util.LocationUtils
import com.fieldtrack.app.util.SecurityUtils
import com.fieldtrack.app.util.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AttendanceFragment — replaces old HomeFragment.
 * Handles check-in/out, work timer, and data sync.
 */
class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EmployeeHomeViewModel by activityViewModel()
    private val sessionManager: SessionManager by inject()
    
    private var pulseAnimator: ObjectAnimator? = null
    
    // Phase 4: Long-session reminder
    private var hasShownLongSessionReminder = false
    private val longSessionThresholdHours = 6
    
    // v1.4 Phase 0: Idempotency guard for service start
    // Prevents duplicate service start calls from lifecycle edge cases
    private var serviceStarted = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.tvWelcome.text = "Welcome, ${viewModel.userName}"

        binding.btnCheckInOut.setOnClickListener {
            if (SecurityUtils.isRateLimited("check_in_out")) {
                requireContext().showToast("Please wait before trying again")
                return@setOnClickListener
            }
            if (viewModel.isCheckedIn.value == true) {
                attemptCheckOut()
            } else {
                attemptCheckIn()
            }
        }

        binding.btnSync.setOnClickListener {
            viewModel.syncData()
        }

        // Phase 2 Beta - View Route button
        binding.btnViewRoute.setOnClickListener {
            navigateToSessionRoute()
        }

        binding.btnLogout.setOnClickListener {
            // Phase 3: Logout interlock - prevent logout while checked in
            if (viewModel.isCheckedIn.value == true) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Active Work Session")
                    .setMessage("You are currently checked in. Please check out before logging out to ensure accurate attendance.")
                    .setPositiveButton("Check Out Now") { _, _ ->
                        attemptCheckOut()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Logout") { _, _ -> performLogout() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun attemptCheckIn() {
        // Pre-flight checks
        if (!LocationUtils.hasLocationPermission(requireContext())) {
            requireContext().showToast("Location permission required")
            return
        }
        if (!LocationUtils.isGpsEnabled(requireContext())) {
            requireContext().showToast("Please enable GPS")
            return
        }
        if (LocationUtils.isMockLocationEnabled(requireContext())) {
            requireContext().showToast("Please disable mock locations")
            return
        }

        // Consent dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Start Tracking")
            .setMessage("Your location will be tracked while checked in. Do you consent?")
            .setPositiveButton("Accept") { _, _ ->
                viewModel.checkIn()
                // Service will start automatically via isCheckedIn observer
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun attemptCheckOut() {
        viewModel.checkOut(requireContext())
        // Service will stop automatically via isCheckedIn observer
    }

    private fun startLocationService() {
        // v1.4 Phase 0: Idempotency guard
        // Prevents duplicate service start from lifecycle edge cases
        if (serviceStarted) {
            if (BuildConfig.DEBUG) {
                android.util.Log.d("AttendanceFragment", "Service already started, skipping duplicate call")
            }
            return
        }
        serviceStarted = true
        
        val serviceIntent = Intent(requireContext(), LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }

    private fun stopLocationService() {
        // Reset idempotency guard when service stops
        serviceStarted = false
        
        val serviceIntent = Intent(requireContext(), LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
        }
        requireContext().startService(serviceIntent)
    }

    // Phase 2 Beta - Navigate to Session Route
    /**
     * Navigate to SessionRouteFragment to visualize today's tracked route.
     * 
     * Guards:
     * - If not checked in and no currentSessionId → show Snackbar
     * - Employee can only view their own routes (enforced by repository using SessionManager.userId)
     */
    private fun navigateToSessionRoute() {
        // Get current session ID from SessionManager
        val sessionId = sessionManager.currentSessionId
        
        // Guard: Check if sessionId is available
        if (sessionId.isNullOrBlank()) {
            Snackbar.make(
                binding.root,
                "Route data not available for this session",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // Navigate with sessionId argument using Bundle
        val bundle = Bundle().apply {
            putString("sessionId", sessionId)
        }
        
        findNavController().navigate(
            R.id.sessionRouteFragment,
            bundle
        )
    }

    private fun performLogout() {
        stopLocationService()
        LocationSyncWorker.cancel(requireContext())
        // Clear session synchronously BEFORE navigation to prevent auto-login
        sessionManager.clearSession()
        // Async cleanup (database, API call) - happens in background
        viewModel.logout()
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    private fun observeViewModel() {
        // v1.4 Phase 0: Single observer for service start (production-grade)
        // No local state, no duplicate calls, lifecycle-safe
        viewModel.shouldStartService.observe(viewLifecycleOwner) { shouldStart ->
            if (shouldStart) {
                startLocationService()
                LocationSyncWorker.schedule(requireContext())
            }
        }
        
        viewModel.isCheckedIn.observe(viewLifecycleOwner) { isCheckedIn ->
            binding.btnCheckInOut.text = if (isCheckedIn) "Check Out" else "Check In"
            binding.tvStatus.text = if (isCheckedIn) "Checked In" else "Checked Out"
            binding.tvWorkDuration.visibility = if (isCheckedIn) View.VISIBLE else View.GONE
            binding.tvCheckInTime.visibility = if (isCheckedIn) View.VISIBLE else View.GONE
            
            // Phase 1: Visual status reinforcement
            if (isCheckedIn) {
                // Tint card background
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.session_active_tint)
                )
                // Change status text color
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.session_active_accent)
                )
                // Show and animate session dot
                binding.viewSessionDot.visibility = View.VISIBLE
                startPulseAnimation()
                // Style check-out button
                binding.btnCheckInOut.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.session_active_accent)
                )
                binding.btnCheckInOut.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.on_primary)
                )
            } else {
                // Restore original card background
                binding.cardStatus.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.surface)
                )
                // Restore original text color
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.on_surface)
                )
                // Hide session dot and stop animation
                binding.viewSessionDot.visibility = View.GONE
                stopPulseAnimation()
                // Restore original button background
                binding.btnCheckInOut.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
                binding.btnCheckInOut.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.on_primary)
                )
                // Stop service and worker when checked out
                stopLocationService()
                LocationSyncWorker.cancel(requireContext())
            }
        }

        viewModel.checkInTime.observe(viewLifecycleOwner) { time ->
            if (time > 0L) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                binding.tvCheckInTime.text = "Checked in at ${sdf.format(Date(time))}"
            }
        }

        viewModel.workDuration.observe(viewLifecycleOwner) { duration ->
            binding.tvWorkDuration.text = duration
        }

        viewModel.unsyncedCount.observe(viewLifecycleOwner) { count ->
            binding.tvUnsyncedCount.text = if (count > 0) {
                "$count locations pending sync"
            } else {
                "All data synced"
            }
        }

        viewModel.isSyncing.observe(viewLifecycleOwner) { syncing ->
            binding.progressBar.visibility = if (syncing) View.VISIBLE else View.GONE
            binding.btnSync.isEnabled = !syncing
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                requireContext().showToast(it)
                viewModel.clearToast()
            }
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()
        pulseAnimator = ObjectAnimator.ofFloat(binding.viewSessionDot, "alpha", 1f, 0.3f).apply {
            duration = 800L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }
    
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onResume() {
        super.onResume()
        checkLongSessionReminder()
    }

    private fun checkLongSessionReminder() {
        // Only check if checked in and haven't shown reminder yet this session
        if (viewModel.isCheckedIn.value != true || hasShownLongSessionReminder) {
            return
        }
        
        val checkInTime = sessionManager.checkInTime
        if (checkInTime <= 0L) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val durationMillis = currentTime - checkInTime
        val durationHours = durationMillis / (1000 * 60 * 60)
        
        if (durationHours >= longSessionThresholdHours) {
            hasShownLongSessionReminder = true
            val hours = durationHours.toInt()
            val minutes = ((durationMillis / (1000 * 60)) % 60).toInt()
            val durationText = if (minutes > 0) {
                "$hours hours and $minutes minutes"
            } else {
                "$hours hours"
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Long Work Session")
                .setMessage("You've been checked in for $durationText. Don't forget to check out when your shift ends.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPulseAnimation()
        // Reset service started flag when view is destroyed
        // This allows service to restart if fragment is recreated while checked in
        serviceStarted = false
        _binding = null
    }
}
