package com.fieldtrack.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fieldtrack.app.data.remote.SessionManager
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.LoginResult
import com.fieldtrack.app.databinding.ActivityPendingApprovalBinding
import com.fieldtrack.app.data.model.UserRole
import com.fieldtrack.app.ui.employee.EmployeeHomeActivity
import com.fieldtrack.app.ui.admin.AdminHomeActivity
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.showToast
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Shown when a user has authenticated successfully but their
 * profile has is_active = false (pending admin approval).
 *
 * Offers:
 * - Retry: re-validates the session and checks if admin has approved
 * - Logout: clears session and returns to login
 */
class PendingApprovalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPendingApprovalBinding
    private val authRepository: AuthRepository by inject()
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingApprovalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.setOnClickListener {
            checkApprovalStatus()
        }

        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                authRepository.logout()
                navigateToLogin()
            }
        }
    }

    private fun checkApprovalStatus() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRetry.isEnabled = false

        lifecycleScope.launch {
            when (val result = authRepository.validateSession()) {
                is LoginResult.Success -> {
                    // Admin approved — route based on role
                    when (sessionManager.userRole) {
                        UserRole.ADMIN -> navigateToAdmin()
                        UserRole.EMPLOYEE -> navigateToEmployee()
                    }
                }
                is LoginResult.PendingApproval -> {
                    showToast("Still waiting for admin approval")
                }
                is LoginResult.Error -> {
                    showToast(result.message)
                    navigateToLogin()
                }
            }
            binding.progressBar.visibility = View.GONE
            binding.btnRetry.isEnabled = true
        }
    }

    private fun navigateToEmployee() {
        startActivity(Intent(this, EmployeeHomeActivity::class.java))
        finish()
    }

    private fun navigateToAdmin() {
        startActivity(Intent(this, AdminHomeActivity::class.java))
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
