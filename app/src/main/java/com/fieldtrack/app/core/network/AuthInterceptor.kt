package com.fieldtrack.app.core.network

import com.fieldtrack.app.BuildConfig
import com.fieldtrack.app.core.data.SessionManager
import com.fieldtrack.app.core.data.StoredSession
import com.fieldtrack.app.core.network.dto.LoginResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class EmployeeRequestOwner(val id: String)

@Singleton
class AuthInterceptor @Inject constructor(private val sessionManager: SessionManager) : Interceptor {
    private val refreshLock = Any()
    private val refreshClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val gson = Gson()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.endsWith("/auth/login")) return chain.proceed(request)

        val session = runBlocking { sessionManager.getSession() }
        request.tag(EmployeeRequestOwner::class.java)?.let { owner ->
            if (session?.owner != owner.id || session.role != "EMPLOYEE") {
                throw IOException("Employee account changed")
            }
        }
        if (session == null) return chain.proceed(request)

        val response = handleInactiveEmployee(chain.proceed(request.withToken(session.accessToken)), session)
        if (response.code != 401) return response

        val replacement = synchronized(refreshLock) { refresh(session) }
        if (replacement == null) return response
        response.close()
        val retry = handleInactiveEmployee(chain.proceed(request.withToken(replacement.accessToken)), replacement)
        if (retry.code == 401) runBlocking { sessionManager.clearIfCurrent(replacement) }
        return retry
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private fun handleInactiveEmployee(response: Response, session: StoredSession): Response {
        if (response.code == 403 && session.role == "EMPLOYEE") {
            val errorCode = runCatching {
                JsonParser.parseString(response.peekBody(4096).string()).asJsonObject["code"]?.asString
            }.getOrNull()
            if (errorCode == "EMPLOYEE_INACTIVE") runBlocking { sessionManager.clearIfCurrent(session) }
        }
        return response
    }

    private fun refresh(rejected: StoredSession): StoredSession? = runBlocking {
        val current = sessionManager.getSession() ?: return@runBlocking null
        val owner = rejected.owner
        if (owner == null || current.owner != owner) return@runBlocking null
        if (current.accessToken != rejected.accessToken) return@runBlocking current
        val refreshToken = current.refreshToken
        if (refreshToken.isNullOrBlank()) {
            sessionManager.clearIfCurrent(current)
            return@runBlocking null
        }

        val url = checkNotNull(BuildConfig.API_BASE_URL.toHttpUrl().resolve("auth/refresh"))
        val body = JsonObject().apply { addProperty("refresh_token", refreshToken) }
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        try {
            refreshClient.newCall(request).execute().use { response ->
                if (response.code == 400 || response.code == 401 || response.code == 403) {
                    sessionManager.clearIfCurrent(current)
                    return@use null
                }
                if (!response.isSuccessful) return@use null
                val credentials = gson.fromJson(response.body?.charStream(), LoginResponse::class.java)?.data
                    ?: return@use null
                sessionManager.replaceTokens(current, credentials.accessToken, credentials.refreshToken)
                sessionManager.getSession()?.takeIf { it.owner == owner && it.accessToken == credentials.accessToken }
            }
        } catch (_: IOException) {
            null
        } catch (_: com.google.gson.JsonParseException) {
            null
        }
    }
}
