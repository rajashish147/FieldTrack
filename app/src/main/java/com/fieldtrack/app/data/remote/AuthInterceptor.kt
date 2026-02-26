package com.fieldtrack.app.data.remote

import com.fieldtrack.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that attaches Supabase authentication headers to every outgoing request.
 *
 * Headers applied:
 *   apikey        — Supabase anon key, required on every request regardless of auth state.
 *   Content-Type  — application/json for all non-storage requests.
 *   Prefer        — return=representation for TABLE write operations ONLY (see note below).
 *   Authorization — Bearer <accessToken> when the user is signed in.
 *
 * ─── WHY Prefer IS SCOPED TO TABLE OPERATIONS ────────────────────────────────────────────
 *
 * `Prefer: return=representation` instructs PostgREST to return the affected rows after
 * an INSERT / UPDATE / UPSERT on a *table endpoint* (`/rest/v1/<table>`).
 * Without it, those mutation calls return HTTP 204 No Content instead of the created/
 * updated rows — which breaks callers that read the response body.
 *
 * RPC calls (`/rest/v1/rpc/<function>`) are fundamentally different:
 *   • The function always returns whatever its RETURNS clause declares, regardless of
 *     any Prefer header.  The header is therefore meaningless for read-only functions.
 *   • For VOLATILE RETURNS TABLE functions, some PostgREST versions interpret
 *     `Prefer: return=representation` as a signal that the call is a data-mutation
 *     operation and may reject read-only functions with HTTP 400.
 *   • Removing the header from RPC calls does NOT change any behaviour for currently
 *     working RPCs (check_in, check_out, admin_list_open_sessions, admin_force_check_out)
 *     because their return values come from the function body, not from a PostgREST
 *     representation of modified rows.
 *
 * Rule implemented here:
 *   Send `Prefer: return=representation` only when ALL of the following are true:
 *     1. The URL targets a REST table endpoint  (/rest/v1/ but NOT /rest/v1/rpc/)
 *     2. The HTTP method is NOT GET  (write operation)
 * ─────────────────────────────────────────────────────────────────────────────────────────
 */
class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.toString()

        val requestBuilder = originalRequest.newBuilder()
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)

        // Don't override Content-Type for multipart uploads (storage endpoints).
        if (!url.contains("/storage/v1/")) {
            requestBuilder.header("Content-Type", "application/json")
        }

        // ── Prefer: return=representation ────────────────────────────────────
        // Applies ONLY to write operations on table endpoints, never to RPC calls.
        // See class-level KDoc for the full rationale.
        val isTableWrite = url.contains("/rest/v1/")
                && !url.contains("/rest/v1/rpc/")
                && originalRequest.method != "GET"

        if (isTableWrite) {
            requestBuilder.header("Prefer", "return=representation")
        }
        // ─────────────────────────────────────────────────────────────────────

        val token = sessionManager.accessToken
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
