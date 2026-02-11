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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        viewModel.loadAllExpenses()
    }

    private fun setupRecyclerView() {
        // Adapter is recreated when employee names arrive
        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAllExpenses()
        }
    }

    private fun observeViewModel() {
        // Rebuild adapter when employee names are available
        viewModel.employeeNames.observe(viewLifecycleOwner) { names ->
            expenseAdapter = AdminExpenseAdapter(
                employeeNames = names,
                onApprove = { expense -> viewModel.approveExpense(expense.id) },
                onReject = { expense -> viewModel.rejectExpense(expense.id) },
                onImageClick = { imageUrl -> showBillImage(imageUrl) }
            )
            binding.rvExpenses.adapter = expenseAdapter
            // Re-submit current list if already loaded
            viewModel.expenses.value?.let { expenseAdapter?.submitList(it) }
        }

        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            expenseAdapter?.submitList(expenses)
            binding.tvNoExpenses.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
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

    private fun showBillImage(imageUrl: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_bill, null)
        val iv = dialogView.findViewById<ImageView>(R.id.iv_full_bill)
        iv.load(imageUrl) { crossfade(true) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bill Image")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
}
