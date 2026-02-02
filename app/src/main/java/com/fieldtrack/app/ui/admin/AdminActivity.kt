package com.fieldtrack.app.ui.admin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.ActivityAdminBinding
import com.fieldtrack.app.ui.auth.LoginActivity
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.util.gone
import com.fieldtrack.app.util.showToast
import com.fieldtrack.app.util.visible
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

    private fun observeViewModel() {
        viewModel.employees.observe(this) { employees ->
            employeeAdapter.submitList(employees)
            binding.swipeRefresh.isRefreshing = false
            binding.tvNoEmployees.visibility = if (employees.isEmpty()) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) binding.progressBar.visible() else binding.progressBar.gone()
        }

        viewModel.error.observe(this) { error ->
            error?.let { showToast(it) }
        }
    }
}
