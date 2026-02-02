package com.fieldtrack.app.ui.expense

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentExpenseBinding
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExpenseFragment : Fragment() {

    private var _binding: FragmentExpenseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseViewModel by viewModel()
    private lateinit var expenseAdapter: ExpenseAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupUI()
        observeViewModel()
        viewModel.loadExpenses()
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter()
        binding.rvExpenses.apply {
            adapter = expenseAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupUI() {
        binding.fabAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }
    }

    private fun showAddExpenseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.et_amount)
        val actvCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.actv_category)

        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Constants.EXPENSE_CATEGORIES
        )
        actvCategory.setAdapter(categoryAdapter)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Submit Expense")
            .setView(dialogView)
            .setPositiveButton("Submit") { _, _ ->
                val amountStr = etAmount.text.toString().trim()
                val category = actvCategory.text.toString().trim()

                if (amountStr.isEmpty() || category.isEmpty()) {
                    requireContext().showToast("All fields are required")
                    return@setPositiveButton
                }

                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    requireContext().showToast("Enter a valid amount")
                    return@setPositiveButton
                }

                viewModel.submitExpense(amount, category, "")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            expenseAdapter.submitList(expenses)
            binding.tvNoExpenses.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.submitResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                requireContext().showToast("Expense submitted")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
