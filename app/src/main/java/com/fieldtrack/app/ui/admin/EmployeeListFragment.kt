package com.fieldtrack.app.ui.admin

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.FragmentEmployeeListBinding
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * EmployeeListFragment — shows all approved employees with search functionality.
 * Tap an employee to view details in a BottomSheet.
 */
class EmployeeListFragment : Fragment() {

    private var _binding: FragmentEmployeeListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()
    private lateinit var adapter: EmployeeAdapter

    // Store original status bar state for restoration
    private var originalStatusBarColor: Int? = null
    private var originalLightStatusBars: Boolean? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmployeeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        setupLogout()
        observeViewModel()
        
        viewModel.loadAllEmployees()
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarOverride()
    }

    override fun onPause() {
        super.onPause()
        restoreStatusBar()
    }

    private fun applyStatusBarOverride() {
        activity?.window?.let { window ->
            // Store original state
            if (originalStatusBarColor == null) {
                originalStatusBarColor = window.statusBarColor
            }
            
            // Apply white status bar with dark icons
            window.statusBarColor = Color.WHITE
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                if (originalLightStatusBars == null) {
                    originalLightStatusBars = insetsController.isAppearanceLightStatusBars
                }
                insetsController.isAppearanceLightStatusBars = true
            }
        }
    }

    private fun restoreStatusBar() {
        activity?.window?.let { window ->
            // Restore original state
            originalStatusBarColor?.let { window.statusBarColor = it }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                originalLightStatusBars?.let { lightBars ->
                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = lightBars
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = EmployeeAdapter(
            onEmployeeClick = { employee ->
                viewModel.selectEmployee(employee)
                showEmployeeDetailsBottomSheet()
            }
        )

        binding.rvEmployees.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEmployees.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateSearchQuery(s?.toString() ?: "")
            }
        })
        
        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAllEmployees()
        }
    }

    private fun setupLogout() {
        binding.btnLogout.setOnClickListener {
            (activity as? AdminHomeActivity)?.logout()
        }
    }

    private fun observeViewModel() {
        viewModel.allEmployees.observe(viewLifecycleOwner) { employees ->
            adapter.submitList(employees)
            
            val isEmpty = employees.isEmpty()
            binding.tvNoEmployees.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvEmployees.visibility = if (isEmpty) View.GONE else View.VISIBLE
            
            val searchQuery = viewModel.searchQuery.value ?: ""
            binding.tvNoEmployees.text = if (searchQuery.isNotBlank()) {
                "No employees match \"$searchQuery\""
            } else {
                "No employees found"
            }
        }

        viewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            binding.ivClearSearch.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
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

    private fun showEmployeeDetailsBottomSheet() {
        val bottomSheet = EmployeeDetailsBottomSheet()
        bottomSheet.show(childFragmentManager, EmployeeDetailsBottomSheet.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
