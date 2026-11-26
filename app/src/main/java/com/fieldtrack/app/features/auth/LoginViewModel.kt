package com.fieldtrack.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── LoginViewModel ───────────────────────────────────────────────────────────
// Handles login form state + triggers AuthRepository.login().
// NO business logic in UI layer — all logic lives in AuthRepository.
// ─────────────────────────────────────────────────────────────────────────────

sealed class LoginUiState {
    data object Idle    : LoginUiState()
    data object Loading : LoginUiState()
    data class  Success(val role: String) : LoginUiState()
    data class  Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            _uiState.value = when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> LoginUiState.Success(result.role)
                is AuthResult.Error   -> LoginUiState.Error(result.message)
            }
        }
    }

    fun resetError() {
        _uiState.value = LoginUiState.Idle
    }
}
