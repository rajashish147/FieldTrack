package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.FragmentEmployeeListBinding
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * EmployeeListFragment — shows all employees with their activation status.
 * Admin can approve inactive or deactivate active employees.
 */
class EmployeeListFragment : Fragment() {

    private var _binding: FragmentEmployeeListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()
    private lateinit var adapter: EmployeeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployeeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EmployeeAdapter(
            onApprove = { viewModel.approveEmployee(it.id) },
            onDeactivate = { viewModel.deactivateEmployee(it.id) }
        )

        binding.rvEmployees.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEmployees.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAllEmployees()
        }

        binding.btnLogout.setOnClickListener {
            (activity as? AdminHomeActivity)?.logout()
        }

        observeViewModel()
        viewModel.loadAllEmployees()
    }

    private fun observeViewModel() {
        viewModel.allEmployees.observe(viewLifecycleOwner) { employees ->
            adapter.submitList(employees)
            binding.tvNoEmployees.visibility = if (employees.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.swipeRefresh.isRefreshing = false
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
