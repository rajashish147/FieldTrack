package com.fieldtrack.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.databinding.ActivityLoginBinding
import com.fieldtrack.app.ui.admin.AdminActivity
import com.fieldtrack.app.ui.dashboard.DashboardActivity
import com.fieldtrack.app.util.SecurityUtils
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModel()

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.isLoggedIn()) {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setLoadingState(true)
            viewModel.validateSession()
            observeSessionValidation()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun observeSessionValidation() {
        viewModel.sessionValid.observe(this) { valid ->
            if (valid == true) {
                navigateToHome()
            } else {
                setLoadingState(false)
                setupUI()
                observeViewModel()
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

    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> setLoadingState(true)
                is NetworkResult.Success -> {
                    setLoadingState(false)
                    navigateToHome()
                }
                is NetworkResult.Error -> {
                    setLoadingState(false)
                    showToast(result.message)
                }
            }
        }

        viewModel.signUpResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> setLoadingState(true)
                is NetworkResult.Success -> {
                    setLoadingState(false)
                    navigateToHome()
                }
                is NetworkResult.Error -> {
                    setLoadingState(false)
                    showToast(result.message)
                }
            }
        }
    }

    private fun navigateToHome() {
        val intent = if (viewModel.isAdmin()) {
            Intent(this, AdminActivity::class.java)
        } else {
            Intent(this, DashboardActivity::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
