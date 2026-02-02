package com.fieldtrack.app.ui.auth

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

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _loginResult.value = NetworkResult.Error("Email and password are required")
            return
        }
        _loginResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _loginResult.value = authRepository.login(email, password)
        }
    }

    fun signUp(email: String, password: String, name: String) {
        if (email.isBlank() || password.isBlank() || name.isBlank()) {
            _signUpResult.value = NetworkResult.Error("All fields are required")
            return
        }
        if (password.length < 6) {
            _signUpResult.value = NetworkResult.Error("Password must be at least 6 characters")
            return
        }
        _signUpResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _signUpResult.value = authRepository.signUp(email, password, name)
        }
    }

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun isAdmin(): Boolean = authRepository.isAdmin()
}
