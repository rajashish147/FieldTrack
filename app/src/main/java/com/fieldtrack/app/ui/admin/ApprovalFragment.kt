package com.fieldtrack.app.ui.admin

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.FragmentApprovalBinding
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * ApprovalFragment — shows only employees with is_active = false.
 * Admin can approve them here.
 */
class ApprovalFragment : Fragment() {

    private var _binding: FragmentApprovalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()
    private lateinit var adapter: PendingEmployeeAdapter

    // Store original status bar state for restoration
    private var originalStatusBarColor: Int? = null
    private var originalLightStatusBars: Boolean? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApprovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PendingEmployeeAdapter(
            onApprove = { viewModel.approveEmployee(it.id) },
            onDeactivate = { viewModel.deactivateEmployee(it.id) }
        )

        binding.rvPending.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPending.adapter = adapter

        setupSearch()

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPendingEmployees()
        }

        observeViewModel()
        viewModel.loadPendingEmployees()
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

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updatePendingSearchQuery(s?.toString() ?: "")
            }
        })
        
        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun observeViewModel() {
        viewModel.pendingEmployees.observe(viewLifecycleOwner) { pending ->
            adapter.submitList(pending)
            val isEmpty = pending.isEmpty()
            binding.tvNoPending.visibility = if (isEmpty) View.VISIBLE else View.GONE
            
            val searchQuery = viewModel.pendingSearchQuery.value ?: ""
            binding.tvNoPending.text = if (searchQuery.isNotBlank()) {
                "No approvals match \"$searchQuery\""
            } else {
                "No pending approvals"
            }
        }

        viewModel.pendingSearchQuery.observe(viewLifecycleOwner) { query ->
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
