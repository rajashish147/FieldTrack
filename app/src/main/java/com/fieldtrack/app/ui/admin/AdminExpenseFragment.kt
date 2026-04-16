package com.fieldtrack.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentAdminExpenseBinding
import com.fieldtrack.app.util.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.androidx.viewmodel.ext.android.activityViewModel

class AdminExpenseFragment : Fragment() {

    private var _binding: FragmentAdminExpenseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by activityViewModel()
    private var expenseAdapter: AdminExpenseAdapter? = null
    
    private var showPendingOnly: Boolean = false

    companion object {
        private const val ARG_SHOW_PENDING_ONLY = "show_pending_only"
        
        fun newInstance(showPendingOnly: Boolean = false): AdminExpenseFragment {
            return AdminExpenseFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SHOW_PENDING_ONLY, showPendingOnly)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Read argument once in onCreate to avoid reapplying on config changes
        showPendingOnly = arguments?.getBoolean(ARG_SHOW_PENDING_ONLY, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupStatusFilter()
        setupSearch()
        setupSwipeRefresh()
        observeViewModel()
        
        // Apply pending-only mode if enabled (only on initial setup, not on resume)
        if (showPendingOnly && savedInstanceState == null) {
            viewModel.updateExpenseStatusFilter("pending")
            binding.chipGroupStatus.visibility = View.GONE
        } else if (showPendingOnly) {
            // On config change, just hide chips (filter state preserved by ViewModel)
            binding.chipGroupStatus.visibility = View.GONE
        }
        
        viewModel.loadAllExpenses()
    }

    private fun setupRecyclerView() {
        val authToken = viewModel.getAuthToken() ?: ""
        expenseAdapter = AdminExpenseAdapter(
            authToken = authToken,
            onApprove = { expense -> viewModel.approveExpense(expense.id) },
            onReject = { expense -> viewModel.rejectExpense(expense.id) },
            onImageClick = { imageUrl -> showBillImage(imageUrl) }
        )
        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExpenses.adapter = expenseAdapter
    }

    private fun setupStatusFilter() {
        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedStatus = when (checkedIds.firstOrNull()) {
                R.id.chip_all -> "all"
                R.id.chip_pending -> "pending"
                R.id.chip_approved -> "approved"
                R.id.chip_rejected -> "rejected"
                else -> "all"
            }
            viewModel.updateExpenseStatusFilter(selectedStatus)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updateExpenseSearchQuery(s?.toString() ?: "")
            }
        })
        
        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAllExpenses()
        }
    }

    private fun observeViewModel() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            expenseAdapter?.submitList(expenses)
            val isEmpty = expenses.isEmpty()
            binding.tvNoExpenses.visibility = if (isEmpty) View.VISIBLE else View.GONE
            
            updateEmptyStateText()
        }

        viewModel.expenseSearchQuery.observe(viewLifecycleOwner) { query ->
            binding.ivClearSearch.visibility = if (query.isNotBlank()) View.VISIBLE else View.GONE
            updateEmptyStateText()
        }

        viewModel.expenseStatusFilter.observe(viewLifecycleOwner) {
            updateEmptyStateText()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = false
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
        expenseAdapter = null
        _binding = null
    }

    private fun updateEmptyStateText() {
        val searchQuery = viewModel.expenseSearchQuery.value ?: ""
        val statusFilter = viewModel.expenseStatusFilter.value ?: "all"
        
        binding.tvNoExpenses.text = when {
            searchQuery.isNotBlank() && statusFilter != "all" -> {
                "No ${statusFilter} expenses match \"$searchQuery\""
            }
            searchQuery.isNotBlank() -> {
                "No expenses match \"$searchQuery\""
            }
            statusFilter != "all" -> {
                "No ${statusFilter} expenses"
            }
            else -> {
                "No expenses submitted yet"
            }
        }
    }

    private fun showBillImage(imageUrl: String) {
        val fullImageUrl = "${com.fieldtrack.app.BuildConfig.SUPABASE_URL}/storage/v1/object/authenticated/$imageUrl"
        val authToken = viewModel.getAuthToken() ?: ""
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_bill, null)
        val iv = dialogView.findViewById<ImageView>(R.id.iv_full_bill)
        iv.load(fullImageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_error)
            memoryCacheKey(imageUrl)
            diskCacheKey(imageUrl)
            addHeader("Authorization", "Bearer $authToken")
            listener(
                onError = { _, _ ->
                    requireContext().showToast("Failed to load image")
                }
            )
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bill Image")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
}
