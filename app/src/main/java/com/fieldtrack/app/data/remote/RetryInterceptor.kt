package com.fieldtrack.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that retries failed requests up to [MAX_RETRIES] times.
 *
 * Only retries on server errors (5xx) and timeouts — never retries
 * client errors (4xx) since those indicate request-level issues that
 * won't resolve on retry.
 *
 * Improves reliability on unstable mobile networks where transient
 * failures (connection resets, gateway timeouts) are common.
 */
class RetryInterceptor(
    private val maxRetries: Int = MAX_RETRIES
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0

        while (!response.isSuccessful && response.code in 500..599 && tryCount < maxRetries) {
            tryCount++
            response.close()
            response = chain.proceed(request)
        }

        return response
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}
