package com.fieldtrack.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class SignUpRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("data")
    val data: Map<String, String>? = null
)

data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("token_type")
    val tokenType: String?,
    @SerializedName("expires_in")
    val expiresIn: Long?,
    @SerializedName("refresh_token")
    val refreshToken: String?,
    @SerializedName("user")
    val user: AuthUser?
) {
    // Supabase returns tokens only after email verification (login).
    // Signup with email confirmation returns user but no tokens.
    val hasSession: Boolean
        get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()
}

data class AuthUser(
    @SerializedName("id")
    val id: String,
    @SerializedName("email")
    val email: String?,
    @SerializedName("email_confirmed_at")
    val emailConfirmedAt: String?,
    @SerializedName("user_metadata")
    val userMetadata: Map<String, Any>? = null
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)

data class SupabaseError(
    @SerializedName("error")
    val error: String?,
    @SerializedName("error_description")
    val errorDescription: String?,
    @SerializedName("msg")
    val msg: String?,
    @SerializedName("message")
    val message: String?
) {
    fun readableMessage(): String {
        return errorDescription
            ?: message
            ?: msg
            ?: error
            ?: "An unknown error occurred"
    }
}
