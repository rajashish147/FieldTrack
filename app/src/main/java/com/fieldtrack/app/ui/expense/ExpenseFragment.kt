package com.fieldtrack.app.ui.expense

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.FragmentExpenseBinding
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.showSnackbar
import com.fieldtrack.app.util.showToast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExpenseFragment : Fragment() {

    private var _binding: FragmentExpenseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpenseViewModel by viewModel()
    private lateinit var expenseAdapter: ExpenseAdapter

    private var selectedBillUri: Uri? = null
    private var dialogBillPreview: ImageView? = null
    private var dialogRemoveText: TextView? = null
    private var dialogAttachButton: MaterialButton? = null

    private val billPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedBillUri = it
            dialogBillPreview?.let { iv ->
                iv.visibility = View.VISIBLE
                iv.load(it) { crossfade(true) }
            }
            dialogRemoveText?.visibility = View.VISIBLE
            dialogAttachButton?.text = "Change Image"
        }
    }

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
        selectedBillUri = null
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.et_amount)
        val actvCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.actv_category)
        val btnAttach = dialogView.findViewById<MaterialButton>(R.id.btn_attach_bill)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.iv_bill_preview)
        val tvRemove = dialogView.findViewById<TextView>(R.id.tv_remove_bill)

        dialogBillPreview = ivPreview
        dialogRemoveText = tvRemove
        dialogAttachButton = btnAttach

        val categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Constants.EXPENSE_CATEGORIES
        )
        actvCategory.setAdapter(categoryAdapter)

        btnAttach.setOnClickListener {
            billPickerLauncher.launch("image/*")
        }

        tvRemove.setOnClickListener {
            selectedBillUri = null
            ivPreview.visibility = View.GONE
            tvRemove.visibility = View.GONE
            btnAttach.text = "Attach Bill Image"
        }

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

                viewModel.submitExpense(requireContext(), amount, category, selectedBillUri)
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
            success?.let {
                if (it) {
                    requireContext().showToast("Expense submitted")
                } else {
                    requireContext().showToast("Failed to submit expense")
                }
                viewModel.clearSubmitResult()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.fabAddExpense.isEnabled = !loading
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let {
                binding.root.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialogBillPreview = null
        dialogRemoveText = null
        dialogAttachButton = null
        _binding = null
    }
}
