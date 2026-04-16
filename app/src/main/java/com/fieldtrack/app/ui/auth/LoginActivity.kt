package com.fieldtrack.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.fieldtrack.app.data.repository.LoginResult
import com.fieldtrack.app.data.repository.SignUpResult
import com.fieldtrack.app.databinding.ActivityLoginBinding
import com.fieldtrack.app.data.model.UserRole
import com.fieldtrack.app.ui.admin.AdminHomeActivity
import com.fieldtrack.app.ui.employee.EmployeeHomeActivity
import com.fieldtrack.app.util.ConfigValidator
import com.fieldtrack.app.util.SecurityUtils
import com.fieldtrack.app.util.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModel()
    private val configValidator: ConfigValidator by inject()

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Block startup if required config is missing (no crash)
        if (configValidator.isMissingConfig()) {
            showConfigError()
            return
        }

        if (viewModel.isLoggedIn()) {
            setLoadingState(true)
            viewModel.validateSession()
            observeSessionValidation()
            return
        }

        setupUI()
        observeViewModel()
    }

    private fun showConfigError() {
        setLoadingState(false)
        MaterialAlertDialogBuilder(this)
            .setTitle("Configuration Error")
            .setMessage(configValidator.getMissingConfigMessage())
            .setCancelable(false)
            .setPositiveButton("Close") { _, _ -> finish() }
            .show()
    }

    /**
     * Observes session validation and routes based on LoginResult:
     * - Success -> Employee or Admin home
     * - PendingApproval -> PendingApprovalActivity
     * - Error -> Show login form
     */
    private fun observeSessionValidation() {
        viewModel.sessionValidation.observe(this) { result ->
            when (result) {
                is LoginResult.Success -> navigateToHome()
                is LoginResult.PendingApproval -> navigateToPendingApproval()
                is LoginResult.Error -> {
                    setLoadingState(false)
                    setupUI()
                    observeViewModel()
                }
                null -> { /* initial state */ }
            }
        }
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            clearErrors()
            val email = SecurityUtils.sanitizeInput(binding.etEmail.text.toString())
            val password = binding.etPassword.text.toString().trim()

            if (isLoginMode) {
                val validation = viewModel.validateLogin(email, password)
                if (validation.isValid) {
                    viewModel.login(email, password)
                } else {
                    showFieldErrors(validation)
                }
            } else {
                val name = SecurityUtils.sanitizeInput(binding.etName.text.toString())
                val validation = viewModel.validateSignUp(email, password, name)
                if (validation.isValid) {
                    viewModel.signUp(email, password, name)
                } else {
                    showFieldErrors(validation)
                }
            }
        }

        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            clearErrors()
            updateUIMode()
        }
    }

    private fun updateUIMode() {
        if (isLoginMode) {
            binding.tilName.visibility = View.GONE
            binding.btnLogin.text = "Login"
            binding.tvToggleMode.text = "Don't have an account? Sign Up"
            binding.tvTitle.text = "Login"
        } else {
            binding.tilName.visibility = View.VISIBLE
            binding.btnLogin.text = "Sign Up"
            binding.tvToggleMode.text = "Already have an account? Login"
            binding.tvTitle.text = "Sign Up"
        }
    }

    private fun showFieldErrors(validation: LoginViewModel.ValidationResult) {
        binding.tilEmail.error = validation.emailError
        binding.tilPassword.error = validation.passwordError
        binding.tilName.error = validation.nameError
    }

    private fun clearErrors() {
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilName.error = null
    }

    private fun setLoadingState(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.etName.isEnabled = !loading
        binding.tvToggleMode.isEnabled = !loading
    }

    /**
     * Observes login and signup results.
     * Login now returns LoginResult which supports PendingApproval.
     */
    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is LoginResult.Success -> {
                    setLoadingState(false)
                    navigateToHome()
                }
                is LoginResult.PendingApproval -> {
                    setLoadingState(false)
                    navigateToPendingApproval()
                }
                is LoginResult.Error -> {
                    setLoadingState(false)
                    showToast(result.message)
                }
                null -> { /* initial state */ }
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            setLoadingState(loading)
        }

        viewModel.signUpResult.observe(this) { result ->
            when (result) {
                is SignUpResult.NeedsEmailVerification -> {
                    // Show email verification banner, switch back to login mode
                    showEmailVerificationState()
                }
                is SignUpResult.Error -> {
                    showToast(result.message)
                }
                null -> { /* initial state */ }
            }
        }
    }

    private fun showEmailVerificationState() {
        // Switch to login mode and show verification message
        isLoginMode = true
        updateUIMode()
        binding.tvTitle.text = "Verify Your Email"
        binding.tvToggleMode.text = "A verification link has been sent to your email.\nPlease verify, then login to set up your profile."
        binding.tvToggleMode.isEnabled = false
        binding.btnLogin.text = "Login"
    }

    /**
     * Routes to appropriate home screen based on user role.
     */
    private fun navigateToHome() {
        val intent = when (viewModel.getUserRole()) {
            UserRole.ADMIN -> Intent(this, AdminHomeActivity::class.java)
            UserRole.EMPLOYEE -> Intent(this, EmployeeHomeActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Routes to the pending approval screen when is_active = false.
     * If user has no profile image, route to ProfileSetupActivity first.
     */
    private fun navigateToPendingApproval() {
        // Check if user needs to set up profile image
        val needsProfileSetup = viewModel.needsProfileSetup()
        
        val intent = if (needsProfileSetup) {
            Intent(this, ProfileSetupActivity::class.java)
        } else {
            Intent(this, PendingApprovalActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
