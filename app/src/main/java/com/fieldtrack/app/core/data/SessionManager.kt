package com.fieldtrack.app.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

// ─── SessionManager ───────────────────────────────────────────────────────────
// Persists JWT token and user identity using DataStore Preferences.
// Used by AuthInterceptor and navigation guard.
// ─────────────────────────────────────────────────────────────────────────────

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ft_session")

data class StoredSession(
    val accessToken: String,
    val refreshToken: String?,
    val role: String?,
    val userId: String?,
    val orgId: String?,
) {
    val owner: String?
        get() = if (!orgId.isNullOrBlank() && !userId.isNullOrBlank()) "$orgId/$userId" else null
}

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val KEY_TOKEN   = stringPreferencesKey("access_token")
    private val KEY_ROLE    = stringPreferencesKey("role")
    private val KEY_USER_ID = stringPreferencesKey("user_id")
    private val KEY_ORG_ID  = stringPreferencesKey("org_id")
    private val KEY_EMAIL   = stringPreferencesKey("email")
    private val KEY_REFRESH = stringPreferencesKey("refresh_token")
    private val KEY_ENCRYPTED_REFRESH = stringPreferencesKey("encrypted_refresh_token")

    val sessions = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN]?.let { token ->
            StoredSession(token, readRefreshToken(prefs), prefs[KEY_ROLE], prefs[KEY_USER_ID], prefs[KEY_ORG_ID])
        }
    }

    suspend fun saveSession(
        token: String,
        role: String,
        userId: String,
        orgId: String,
        email: String?,
        refreshToken: String? = null,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN]   = token
            prefs[KEY_ROLE]    = role
            prefs[KEY_USER_ID] = userId
            prefs[KEY_ORG_ID]  = orgId
            prefs[KEY_EMAIL]   = email ?: ""
            writeRefreshToken(prefs, refreshToken)
        }
    }

    suspend fun getSession(): StoredSession? = sessions.firstOrNull()

    suspend fun replaceTokens(expected: StoredSession, token: String, refreshToken: String?) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_TOKEN] == expected.accessToken && readRefreshToken(prefs) == expected.refreshToken &&
                prefs[KEY_USER_ID] == expected.userId && prefs[KEY_ORG_ID] == expected.orgId
            ) {
                prefs[KEY_TOKEN] = token
                writeRefreshToken(prefs, refreshToken)
            }
        }
    }

    suspend fun clearIfCurrent(expected: StoredSession) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_TOKEN] == expected.accessToken && readRefreshToken(prefs) == expected.refreshToken &&
                prefs[KEY_USER_ID] == expected.userId && prefs[KEY_ORG_ID] == expected.orgId
            ) prefs.clear()
        }
    }

    suspend fun getToken(): String? =
        context.dataStore.data.map { it[KEY_TOKEN] }.firstOrNull()

    suspend fun getRole(): String? =
        context.dataStore.data.map { it[KEY_ROLE] }.firstOrNull()

    suspend fun getUserId(): String? =
        context.dataStore.data.map { it[KEY_USER_ID] }.firstOrNull()

    suspend fun getOrgId(): String? =
        context.dataStore.data.map { it[KEY_ORG_ID] }.firstOrNull()

    suspend fun getEmail(): String? =
        context.dataStore.data.map { it[KEY_EMAIL] }.firstOrNull()

    suspend fun isLoggedIn(): Boolean = getToken() != null

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    private fun readRefreshToken(prefs: Preferences): String? {
        val encrypted = prefs[KEY_ENCRYPTED_REFRESH] ?: return prefs[KEY_REFRESH]
        return runCatching {
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, refreshKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun writeRefreshToken(prefs: androidx.datastore.preferences.core.MutablePreferences, token: String?) {
        prefs.remove(KEY_REFRESH)
        if (token == null) {
            prefs.remove(KEY_ENCRYPTED_REFRESH)
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, refreshKey())
        prefs[KEY_ENCRYPTED_REFRESH] = Base64.encodeToString(
            cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP,
        )
    }

    @Synchronized
    private fun refreshKey(): SecretKey {
        val alias = "fieldtrack_session_refresh"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
    }
}
