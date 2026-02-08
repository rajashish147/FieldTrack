package com.fieldtrack.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.R
import com.fieldtrack.app.databinding.ActivityAdminBinding
import com.fieldtrack.app.ui.auth.LoginActivity
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.util.gone
import com.fieldtrack.app.util.showToast
import com.fieldtrack.app.util.visible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private val viewModel: AdminViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    private lateinit var employeeAdapter: EmployeeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
        viewModel.loadEmployees()
    }

    private fun setupUI() {
        binding.toolbar.title = "Admin Dashboard"

        employeeAdapter = EmployeeAdapter { user ->
            showToast("Selected: ${user.name}")
        }

        binding.rvEmployees.apply {
            adapter = employeeAdapter
            layoutManager = LinearLayoutManager(this@AdminActivity)
        }

        binding.btnViewReports.setOnClickListener {
            viewModel.loadAllReports()
        }

        binding.btnViewExpenses.setOnClickListener {
            viewModel.loadAllExpenses()
        }

        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadEmployees()
        }
    }

    private fun showReportsDialog() {
        val names = viewModel.employeeNames.value ?: emptyMap()
        val reports = viewModel.allReports.value ?: emptyList()

        if (reports.isEmpty()) {
            showToast("No reports found")
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_list, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rv_admin_list)
        val reportAdapter = AdminReportAdapter(names)
        rv.adapter = reportAdapter
        rv.layoutManager = LinearLayoutManager(this)
        reportAdapter.submitList(reports)

        MaterialAlertDialogBuilder(this)
            .setTitle("All Reports (${reports.size})")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showExpensesDialog() {
        val names = viewModel.employeeNames.value ?: emptyMap()
        val expenses = viewModel.allExpenses.value ?: emptyList()

        if (expenses.isEmpty()) {
            showToast("No expenses found")
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_list, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rv_admin_list)
        val expenseAdapter = AdminExpenseAdapter(
            employeeNames = names,
            onApprove = { expense -> viewModel.approveExpense(expense.id) },
            onReject = { expense -> viewModel.rejectExpense(expense.id) }
        )
        rv.adapter = expenseAdapter
        rv.layoutManager = LinearLayoutManager(this)
        expenseAdapter.submitList(expenses)

        MaterialAlertDialogBuilder(this)
            .setTitle("All Expenses (${expenses.size})")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.employees.observe(this) { employees ->
            employeeAdapter.submitList(employees)
            binding.swipeRefresh.isRefreshing = false
            binding.tvNoEmployees.visibility = if (employees.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        viewModel.allReports.observe(this) { reports ->
            if (reports != null && reports.isNotEmpty()) {
                showReportsDialog()
            }
        }

        viewModel.allExpenses.observe(this) { expenses ->
            if (expenses != null && expenses.isNotEmpty()) {
                showExpensesDialog()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) binding.progressBar.visible() else binding.progressBar.gone()
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                showToast(it)
                viewModel.clearError()
            }
        }

        viewModel.statusUpdateResult.observe(this) { msg ->
            msg?.let {
                showToast(it)
                viewModel.clearStatusUpdateResult()
            }
        }
    }
}
