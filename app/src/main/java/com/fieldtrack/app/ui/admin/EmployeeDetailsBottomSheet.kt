package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    }

    private fun bindEmployeeData(user: com.fieldtrack.app.data.model.User) {
        binding.tvEmployeeName.text = user.name.ifBlank { "Unnamed Employee" }
        
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
        
        binding.tvRole.text = user.role.replaceFirstChar { it.uppercase() }
        
        binding.tvStatus.text = if (user.isActive) "Active" else "Inactive"
        binding.tvStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (user.isActive) com.fieldtrack.app.R.color.status_approved 
                else com.fieldtrack.app.R.color.status_rejected
            )
        )
        
        // Show sync health with color-coded indicator
        binding.tvLastSync.text = user.getFormattedLastSync()
        binding.tvLastSync.setTextColor(user.getSyncHealthColor())
        
        // Show sync freshness indicator
        val freshnessIndicator = user.getSyncFreshnessIndicator()
        if (freshnessIndicator != user.getFormattedLastSync()) {
            binding.tvLastSync.text = "${user.getFormattedLastSync()} ($freshnessIndicator)"
        }
        
        binding.tvUserId.text = user.id
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
