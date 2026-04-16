package com.fieldtrack.app.data.remote

import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.data.remote.dto.AuthResponse
import com.fieldtrack.app.data.remote.dto.RefreshTokenRequest
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val sessionManager: SessionManager
) : Authenticator {

    private val gson = Gson()
    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val currentToken = sessionManager.accessToken
        
        synchronized(this) {
            val latestToken = sessionManager.accessToken
            
            if (currentToken != latestToken && latestToken != null) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }
        }
        
        if (sessionManager.isCheckoutInProgress()) {
            val refreshToken = sessionManager.refreshToken ?: return null
            val newToken = runBlocking {
                refreshMutex.withLock {
                    refreshAccessToken(refreshToken)
                }
            } ?: return null
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
        
        if (responseCount(response) >= 2) {
            sessionManager.clearSession()
            return null
        }

        val refreshToken = sessionManager.refreshToken ?: run {
            sessionManager.clearSession()
            return null
        }

        val newToken = runBlocking {
            refreshMutex.withLock {
                refreshAccessToken(refreshToken)
            }
        } ?: run {
            sessionManager.clearSession()
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun refreshAccessToken(refreshToken: String): String? {
        return try {
            val body = gson.toJson(RefreshTokenRequest(refreshToken))
                .toRequestBody("application/json".toMediaType())

            val client = OkHttpClient.Builder().build()
            val request = Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}auth/v1/token?grant_type=refresh_token")
                .post(body)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val authResponse = gson.fromJson(
                    response.body?.string(),
                    AuthResponse::class.java
                )
                // Persist full session atomically — not just access token
                if (authResponse.accessToken != null && authResponse.refreshToken != null) {
                    sessionManager.saveSession(
                        accessToken = authResponse.accessToken,
                        refreshToken = authResponse.refreshToken,
                        userId = sessionManager.userId ?: "",
                        expiresIn = authResponse.expiresIn ?: 3600L
                    )
                }
                authResponse.accessToken
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
