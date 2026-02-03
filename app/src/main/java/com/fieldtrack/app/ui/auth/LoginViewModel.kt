package com.fieldtrack.app.ui.auth

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.data.remote.NetworkResult
import com.fieldtrack.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _loginResult = MutableLiveData<NetworkResult<User>>()
    val loginResult: LiveData<NetworkResult<User>> = _loginResult

    private val _signUpResult = MutableLiveData<NetworkResult<User>>()
    val signUpResult: LiveData<NetworkResult<User>> = _signUpResult

    private val _sessionValid = MutableLiveData<Boolean?>()
    val sessionValid: LiveData<Boolean?> = _sessionValid

    data class ValidationResult(
        val isValid: Boolean,
        val emailError: String? = null,
        val passwordError: String? = null,
        val nameError: String? = null
    )

    fun login(email: String, password: String) {
        val validation = validateLogin(email, password)
        if (!validation.isValid) {
            _loginResult.value = NetworkResult.Error(
                validation.emailError ?: validation.passwordError ?: "Invalid input"
            )
            return
        }
        _loginResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _loginResult.value = authRepository.login(email, password)
        }
    }

    fun signUp(email: String, password: String, name: String) {
        val validation = validateSignUp(email, password, name)
        if (!validation.isValid) {
            _signUpResult.value = NetworkResult.Error(
                validation.nameError ?: validation.emailError ?: validation.passwordError ?: "Invalid input"
            )
            return
        }
        _signUpResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _signUpResult.value = authRepository.signUp(email, password, name)
        }
    }

    fun validateSession() {
        viewModelScope.launch {
            val valid = authRepository.validateSession()
            _sessionValid.value = valid
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
}
