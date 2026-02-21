package com.fieldtrack.app.data.remote

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Enterprise-grade secure logging interceptor for OkHttp.
 * 
 * Security Policy (Safe-By-Default):
 * - NEVER logs sensitive data (tokens, passwords, API keys)
 * - Uses ALLOWLIST strategy (only logs known-safe headers)
 * - Redacts ALL headers except explicitly safe ones
 * - Sanitizes URLs (removes query parameters)
 * - Does NOT log request/response bodies
 * - Applies to ALL build variants (DEBUG and RELEASE)
 * 
 * This ensures sensitive data never appears in logs, even during development,
 * and protects against future accidental exposure from new headers or query params.
 */
class SecureLoggingInterceptor : Interceptor {

    companion object {
        private const val TAG = "SecureHttpLog"
        
        /**
         * SAFE-BY-DEFAULT: Only these headers are logged.
         * All other headers are redacted as ***REDACTED***.
         * 
         * This prevents accidental exposure of:
         * - X-Auth-Token
         * - Cookie / Set-Cookie
         * - X-Client-Secret
         * - Custom authentication headers
         * - Future headers added by APIs
         */
        private val SAFE_HEADERS = setOf(
            "content-type",
            "accept",
            "accept-encoding",
            "accept-language",
            "user-agent",
            "content-length",
            "connection",
            "cache-control"
        )
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        // Log request (method + sanitized URL only, no body)
        logRequest(request)

        // Execute request
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val sanitizedUrl = sanitizeUrl(request.url.toString())
            Log.e(TAG, "Request failed: ${request.method} $sanitizedUrl (${duration}ms)", e)
            throw e
        }

        // Log response (status code + duration only, no body)
        val duration = System.currentTimeMillis() - startTime
        logResponse(response, duration)

        return response
    }

    private fun logRequest(request: okhttp3.Request) {
        val method = request.method
        val sanitizedUrl = sanitizeUrl(request.url.toString())
        
        Log.d(TAG, "→ $method $sanitizedUrl")
        
        // Log headers with SAFE-BY-DEFAULT strategy
        request.headers.forEach { (name, value) ->
            if (name.lowercase() in SAFE_HEADERS) {
                // Only log explicitly safe headers
                Log.d(TAG, "  $name: $value")
            } else {
                // Redact ALL other headers (including future unknown headers)
                Log.d(TAG, "  $name: ***REDACTED***")
            }
        }
        
        // NEVER log request body (may contain passwords, tokens)
    }

    private fun logResponse(response: Response, duration: Long) {
        val code = response.code
        val message = response.message
        val sanitizedUrl = sanitizeUrl(response.request.url.toString())
        
        val statusEmoji = when (code) {
            in 200..299 -> "✓"
            in 400..499 -> "✗"
            in 500..599 -> "✗✗"
            else -> "?"
        }
        
        Log.d(TAG, "← $statusEmoji $code $message (${duration}ms) $sanitizedUrl")
        
        // NEVER log response body (may contain tokens, user data)
    }

    /**
     * Sanitize URL by removing query parameters.
     * 
     * Prevents exposure of sensitive data in query params:
     * - /users?email=test@gmail.com → /users
     * - /auth?token=secret123 → /auth
     * - /api?key=abc123 → /api
     * 
     * This protects against accidental PII exposure in URLs.
     */
    private fun sanitizeUrl(url: String): String {
        return try {
            val urlObj = url.toHttpUrlOrNull()
            if (urlObj != null) {
                // Remove all query parameters
                urlObj.newBuilder()
                    .query(null)
                    .build()
                    .toString()
            } else {
                // Fallback: remove everything after '?'
                url.substringBefore('?')
            }
        } catch (e: Exception) {
            // Fallback: remove everything after '?'
            url.substringBefore('?')
        }
    }
}
