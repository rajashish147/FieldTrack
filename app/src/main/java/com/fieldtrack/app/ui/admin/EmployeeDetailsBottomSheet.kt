package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import coil.load
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.BottomSheetEmployeeDetailsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class EmployeeDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEmployeeDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEmployeeDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.selectedEmployee.observe(viewLifecycleOwner) { employee ->
            employee?.let { bindEmployeeData(it) }
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // v1.4 - View Session History: navigate to per-employee session history
        binding.btnViewSessionHistory.setOnClickListener {
            val employee = viewModel.selectedEmployee.value ?: return@setOnClickListener
            dismiss()
            parentFragment?.findNavController()?.navigate(
                R.id.action_employeeList_to_sessionHistory,
                bundleOf(
                    EmployeeSessionHistoryFragment.ARG_USER_ID       to employee.id,
                    EmployeeSessionHistoryFragment.ARG_EMPLOYEE_NAME to employee.name
                )
            )
        }
    }

    private fun bindEmployeeData(user: com.fieldtrack.app.data.model.User) {
        // Profile Header
        binding.tvEmployeeName.text = user.name.ifBlank { "Unnamed Employee" }
        binding.tvRole.text = user.role.name.lowercase().replaceFirstChar { it.uppercase() }

        // Active/Inactive Status Badge
        binding.tvStatus.text = if (user.isActive) "Active" else "Inactive"
        binding.tvStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (user.isActive) com.fieldtrack.app.R.color.status_approved
                else com.fieldtrack.app.R.color.status_rejected
            )
        )

        // Load profile image
        if (!user.profileImageUrl.isNullOrBlank()) {
            binding.ivProfileImage.load(user.profileImageUrl) {
                crossfade(true)
                placeholder(com.fieldtrack.app.R.drawable.bg_image_placeholder)
                error(com.fieldtrack.app.R.drawable.bg_image_placeholder)
                addHeader("Authorization", "Bearer ${com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY}")
                addHeader("apikey", com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY)
            }
            binding.ivProfileImage.visibility = View.VISIBLE

            // Click to view full size
            binding.ivProfileImage.setOnClickListener {
                showFullImage(user.profileImageUrl)
            }
        } else {
            binding.ivProfileImage.setImageResource(com.fieldtrack.app.R.drawable.bg_image_placeholder)
            binding.ivProfileImage.visibility = View.VISIBLE
        }

        // Contact Information
        if (user.email.isNullOrBlank()) {
            binding.llEmail.visibility = View.GONE
        } else {
            binding.llEmail.visibility = View.VISIBLE
            binding.tvEmail.text = user.email
        }

        if (user.phone.isNullOrBlank()) {
            binding.llPhone.visibility = View.GONE
        } else {
            binding.llPhone.visibility = View.VISIBLE
            binding.tvPhone.text = user.phone
        }

        // Check-in Status
        binding.tvCheckInStatus.text = if (user.isCheckedIn) "✓ Checked In" else "✗ Checked Out"
        binding.tvCheckInStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (user.isCheckedIn) com.fieldtrack.app.R.color.status_approved
                else com.fieldtrack.app.R.color.on_surface_variant
            )
        )

        // Sync Health
        val syncHealthStatus = user.getSyncHealthStatus()
        binding.tvSyncHealthLabel.text = when (syncHealthStatus) {
            com.fieldtrack.app.data.model.SyncHealthStatus.HEALTHY -> "Healthy"
            com.fieldtrack.app.data.model.SyncHealthStatus.STALE -> "Stale"
            com.fieldtrack.app.data.model.SyncHealthStatus.VERY_STALE -> "Very Stale"
            com.fieldtrack.app.data.model.SyncHealthStatus.NEVER_SYNCED -> "Never Synced"
            com.fieldtrack.app.data.model.SyncHealthStatus.UNKNOWN -> "Unknown"
        }

        // Sync health color indicator
        val syncColor = user.getSyncHealthColor()
        binding.viewSyncIndicator.setBackgroundColor(syncColor)

        // Last sync timestamp with freshness
        val formattedSync = user.getFormattedLastSync()
        val freshnessIndicator = user.getSyncFreshnessIndicator()

        binding.tvLastSync.text = if (freshnessIndicator != formattedSync) {
            "$formattedSync ($freshnessIndicator)"
        } else {
            formattedSync
        }
        binding.tvLastSync.setTextColor(syncColor)

        // Context-aware actions
        // View on Map button - only show if employee has location data
        val hasLocation = user.lastSyncedAt?.isNotBlank() == true
        binding.btnViewMap.visibility = if (hasLocation) View.VISIBLE else View.GONE

        if (hasLocation) {
            binding.btnViewMap.setOnClickListener {
                // Navigate to map - implementation depends on navigation setup
                // For now, just dismiss and let parent handle navigation
                dismiss()
            }
        }

        // v1.4 - View Session History button is always visible for active employees
        // (even employees with zero sessions will show the empty state gracefully)
        binding.btnViewSessionHistory.isEnabled = user.isActive
        binding.btnViewSessionHistory.alpha = if (user.isActive) 1f else 0.4f
    }

    private fun showFullImage(imageUrl: String) {
        val imageView = android.widget.ImageView(requireContext()).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        imageView.load(imageUrl) {
            crossfade(true)
            // Add Supabase auth headers for private storage
            addHeader("Authorization", "Bearer ${com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY}")
            addHeader("apikey", com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY)
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(imageView)
            .setPositiveButton("Close", null)
            .create()
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.clearSelectedEmployee()
        _binding = null
    }

    companion object {
        const val TAG = "EmployeeDetailsBottomSheet"
    }
}
