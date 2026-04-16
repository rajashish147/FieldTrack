package com.fieldtrack.app.ui.auth

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.LoginResult
import com.fieldtrack.app.data.repository.SignUpResult
import com.fieldtrack.app.data.model.UserRole
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _loginResult = MutableLiveData<LoginResult?>()
    val loginResult: LiveData<LoginResult?> = _loginResult

    private val _signUpResult = MutableLiveData<SignUpResult?>()
    val signUpResult: LiveData<SignUpResult?> = _signUpResult

    /** Session validation now returns LoginResult for proper routing */
    private val _sessionValidation = MutableLiveData<LoginResult?>()
    val sessionValidation: LiveData<LoginResult?> = _sessionValidation

    data class ValidationResult(
        val isValid: Boolean,
        val emailError: String? = null,
        val passwordError: String? = null,
        val nameError: String? = null
    )

    fun login(email: String, password: String) {
        val validation = validateLogin(email, password)
        if (!validation.isValid) {
            _loginResult.value = LoginResult.Error(
                validation.emailError ?: validation.passwordError ?: "Invalid input"
            )
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            _loginResult.value = authRepository.login(email, password)
            _isLoading.value = false
        }
    }

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun signUp(email: String, password: String, name: String) {
        val validation = validateSignUp(email, password, name)
        if (!validation.isValid) {
            _signUpResult.value = SignUpResult.Error(
                validation.nameError ?: validation.emailError ?: validation.passwordError ?: "Invalid input"
            )
            return
        }
        _isLoading.value = true
        viewModelScope.launch {
            _signUpResult.value = authRepository.signUp(email, password, name)
            _isLoading.value = false
        }
    }

    /**
     * Validates the current session and returns LoginResult.
     * Used on app launch to check if user is approved, pending, or expired.
     */
    fun validateSession() {
        viewModelScope.launch {
            _sessionValidation.value = authRepository.validateSession()
        }
    }

    fun validateLogin(email: String, password: String): ValidationResult {
        val emailError = when {
            email.isBlank() -> "Email is required"
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Enter a valid email address"
            else -> null
        }
        val passwordError = when {
            password.isBlank() -> "Password is required"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        return ValidationResult(
            isValid = emailError == null && passwordError == null,
            emailError = emailError,
            passwordError = passwordError
        )
    }

    fun validateSignUp(email: String, password: String, name: String): ValidationResult {
        val nameError = when {
            name.isBlank() -> "Name is required"
            name.length < 2 -> "Name must be at least 2 characters"
            else -> null
        }
        val loginValidation = validateLogin(email, password)
        return ValidationResult(
            isValid = nameError == null && loginValidation.isValid,
            emailError = loginValidation.emailError,
            passwordError = loginValidation.passwordError,
            nameError = nameError
        )
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun isAdmin(): Boolean = authRepository.isAdmin()

    fun getUserRole(): UserRole = authRepository.getUserRole()
    
    fun needsProfileSetup(): Boolean = authRepository.needsProfileSetup()
}
