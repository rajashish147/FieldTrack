package com.fieldtrack.app.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.fieldtrack.app.databinding.ActivityAdminBinding
import com.fieldtrack.app.ui.auth.LoginActivity
import com.fieldtrack.app.data.remote.SessionManager
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Legacy Admin Dashboard activity.
 * NOTE: The primary admin UI now uses [AdminHomeActivity] with fragments.
 *       This activity is kept for the manifest declaration and could be
 *       removed once fully migrated.
 */
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
        viewModel.loadAllEmployees()
    }

    private fun setupUI() {
        binding.toolbar.title = "Admin Dashboard"

        employeeAdapter = EmployeeAdapter(
            onApprove = { user -> viewModel.approveEmployee(user.id) },
            onDeactivate = { user -> viewModel.deactivateEmployee(user.id) }
        )

        binding.rvEmployees.apply {
            adapter = employeeAdapter
            layoutManager = LinearLayoutManager(this@AdminActivity)
        }

        binding.btnLogout.setOnClickListener {
            sessionManager.clearSession()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadAllEmployees()
        }
    }

    private fun observeViewModel() {
        viewModel.allEmployees.observe(this) { employees ->
            employeeAdapter.submitList(employees)
            binding.swipeRefresh.isRefreshing = false
            binding.tvNoEmployees.visibility = if (employees.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Show/hide progress if needed
            binding.swipeRefresh.isRefreshing = isLoading
        }

        viewModel.toastMessage.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.clearToast()
            }
        }
    }
}
