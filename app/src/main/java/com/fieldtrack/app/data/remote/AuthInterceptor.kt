package com.fieldtrack.app.data.remote

import com.fieldtrack.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        val requestBuilder = originalRequest.newBuilder()
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)

        // Don't override Content-Type for multipart uploads (storage endpoints)
        if (!url.contains("/storage/v1/")) {
            requestBuilder.header("Content-Type", "application/json")
        }

        // Only send Prefer header on write operations (POST/PATCH/PUT/DELETE)
        if (url.contains("/rest/v1/") && originalRequest.method != "GET") {
            requestBuilder.header("Prefer", "return=representation")
        }

        val token = sessionManager.accessToken
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
