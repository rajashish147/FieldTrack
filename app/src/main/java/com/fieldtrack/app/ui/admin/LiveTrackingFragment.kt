package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentLiveTrackingBinding
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * LiveTrackingFragment — shows a list of employees with their last sync time.
 * Tapping an employee navigates to EmployeeMapFragment with their location.
 */
class LiveTrackingFragment : Fragment() {

    private var _binding: FragmentLiveTrackingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()
    private lateinit var adapter: TrackingAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TrackingAdapter { emp ->
            val bundle = Bundle().apply {
                putString("userId", emp.userId)
                putString("name", emp.name)
                putFloat("latitude", emp.latitude.toFloat())
                putFloat("longitude", emp.longitude.toFloat())
                putString("lastUpdated", emp.lastUpdated)
            }
            findNavController().navigate(R.id.action_liveTracking_to_employeeMap, bundle)
        }

        binding.rvEmployees.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEmployees.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadEmployeeLocations()
        }

        observeViewModel()
        viewModel.loadEmployeeLocations()
    }

    private fun observeViewModel() {
        viewModel.employeeLocations.observe(viewLifecycleOwner) { employees ->
            adapter.submitList(employees)
            binding.tvNoEmployees.visibility =
                if (employees.isEmpty()) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                requireContext().showToast(it)
                viewModel.clearToast()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
