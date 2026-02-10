package com.fieldtrack.app.data.repository

import com.fieldtrack.app.data.model.User

/**
 * Distinct result type for the login flow.
 * Separates "success + active", "success but pending approval", and "error".
 */
sealed class LoginResult {
    /** User authenticated and is_active = true — proceed to home */
    data class Success(val user: User) : LoginResult()

    /** User authenticated but is_active = false — show pending screen */
    data object PendingApproval : LoginResult()

    /** Authentication or profile fetch failed */
    data class Error(val message: String) : LoginResult()
}
