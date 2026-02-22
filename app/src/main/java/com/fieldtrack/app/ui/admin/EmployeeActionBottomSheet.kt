package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.BottomSheetEmployeeActionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EmployeeActionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEmployeeActionBinding? = null
    private val binding get() = _binding!!

    private val userId: String by lazy { arguments?.getString(ARG_USER_ID) ?: "" }
    private val name: String by lazy { arguments?.getString(ARG_NAME) ?: "" }
    private val latitude: Float by lazy { arguments?.getFloat(ARG_LATITUDE) ?: 0f }
    private val longitude: Float by lazy { arguments?.getFloat(ARG_LONGITUDE) ?: 0f }
    private val lastUpdated: String by lazy { arguments?.getString(ARG_LAST_UPDATED) ?: "" }
    private val activeSessionId: String? by lazy { arguments?.getString(ARG_ACTIVE_SESSION_ID) }

    companion object {
        private const val ARG_USER_ID = "userId"
        private const val ARG_NAME = "name"
        private const val ARG_LATITUDE = "latitude"
        private const val ARG_LONGITUDE = "longitude"
        private const val ARG_LAST_UPDATED = "lastUpdated"
        private const val ARG_ACTIVE_SESSION_ID = "activeSessionId"

        fun newInstance(
            userId: String,
            name: String,
            latitude: Float,
            longitude: Float,
            lastUpdated: String,
            activeSessionId: String?
        ): EmployeeActionBottomSheet {
            return EmployeeActionBottomSheet().apply {
                arguments = bundleOf(
                    ARG_USER_ID to userId,
                    ARG_NAME to name,
                    ARG_LATITUDE to latitude,
                    ARG_LONGITUDE to longitude,
                    ARG_LAST_UPDATED to lastUpdated,
                    ARG_ACTIVE_SESSION_ID to activeSessionId
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEmployeeActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewLiveLocation()
        setupViewRoute()
    }

    private fun setupViewLiveLocation() {
        binding.btnViewLiveLocation.setOnClickListener {
            val bundle = bundleOf(
                "userId" to userId,
                "name" to name,
                "latitude" to latitude,
                "longitude" to longitude,
                "lastUpdated" to lastUpdated
            )
            dismiss()
            // Navigate using parent fragment's NavController
            parentFragment?.findNavController()?.navigate(
                R.id.action_liveTracking_to_employeeMap,
                bundle
            )
        }
    }

    private fun setupViewRoute() {
        if (activeSessionId.isNullOrBlank()) {
            binding.btnViewRoute.isEnabled = false
            binding.tvNoSession.visibility = View.VISIBLE
        } else {
            binding.btnViewRoute.isEnabled = true
            binding.tvNoSession.visibility = View.GONE

            binding.btnViewRoute.setOnClickListener {
                val bundle = bundleOf("sessionId" to activeSessionId)
                dismiss()
                // Navigate using parent fragment's NavController
                parentFragment?.findNavController()?.navigate(
                    R.id.sessionRouteFragment,
                    bundle
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
