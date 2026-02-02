package com.fieldtrack.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.databinding.ActivityLoginBinding
import com.fieldtrack.app.ui.admin.AdminActivity
import com.fieldtrack.app.ui.dashboard.DashboardActivity
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.showToast
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModel()

    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.isLoggedIn()) {
            navigateToHome()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (isLoginMode) {
                viewModel.login(email, password)
            } else {
                val name = binding.etName.text.toString().trim()
                viewModel.signUp(email, password, name)
            }
        }

        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
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

    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    navigateToHome()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    showToast(result.message)
                }
            }
        }

        viewModel.signUpResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    navigateToHome()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
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
