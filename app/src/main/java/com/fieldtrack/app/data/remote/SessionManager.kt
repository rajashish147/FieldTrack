package com.fieldtrack.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fieldtrack.app.util.Constants

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        Constants.PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Atomic save of all session tokens — single commit, no partial state
    fun saveSession(accessToken: String, refreshToken: String, userId: String, expiresIn: Long) {
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)
        prefs.edit()
            .putString(Constants.PREF_ACCESS_TOKEN, accessToken)
            .putString(Constants.PREF_REFRESH_TOKEN, refreshToken)
            .putString(Constants.PREF_USER_ID, userId)
            .putLong(Constants.PREF_EXPIRES_AT, expiresAt)
            .apply()
    }

    var accessToken: String?
        get() = prefs.getString(Constants.PREF_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.PREF_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(Constants.PREF_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.PREF_REFRESH_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(Constants.PREF_USER_ID, null)
        set(value) = prefs.edit().putString(Constants.PREF_USER_ID, value).apply()

    val tokenExpiresAt: Long
        get() = prefs.getLong(Constants.PREF_EXPIRES_AT, 0L)

    val isTokenExpired: Boolean
        get() = System.currentTimeMillis() >= tokenExpiresAt

    var userRole: String?
        get() = prefs.getString(Constants.PREF_USER_ROLE, null)
        set(value) = prefs.edit().putString(Constants.PREF_USER_ROLE, value).apply()

    var userName: String?
        get() = prefs.getString(Constants.PREF_USER_NAME, null)
        set(value) = prefs.edit().putString(Constants.PREF_USER_NAME, value).apply()

    var isCheckedIn: Boolean
        get() = prefs.getBoolean(Constants.PREF_IS_CHECKED_IN, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_IS_CHECKED_IN, value).apply()

    var checkInTime: Long
        get() = prefs.getLong(Constants.PREF_CHECK_IN_TIME, 0L)
        set(value) = prefs.edit().putLong(Constants.PREF_CHECK_IN_TIME, value).apply()

    // v1.4 - Session-based location tracking
    // Used to tag locations with attendance session ID
    // NOTE: isCheckedIn is still the primary check-in state flag
    // This field is populated during check-in and cleared during check-out
    var currentSessionId: String?
        get() = prefs.getString(Constants.PREF_CURRENT_SESSION_ID, null)
        set(value) = prefs.edit().putString(Constants.PREF_CURRENT_SESSION_ID, value).apply()

    var lastCheckOutTime: Long
        get() = prefs.getLong(Constants.PREF_LAST_CHECK_OUT_TIME, 0L)
        set(value) = prefs.edit().putLong(Constants.PREF_LAST_CHECK_OUT_TIME, value).apply()

    var isActive: Boolean
        get() = prefs.getBoolean(Constants.PREF_IS_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_IS_ACTIVE, value).apply()

    var profileImageUrl: String?
        get() = prefs.getString(Constants.PREF_PROFILE_IMAGE_URL, null)
        set(value) = prefs.edit().putString(Constants.PREF_PROFILE_IMAGE_URL, value).apply()

    val isLoggedIn: Boolean
        get() = accessToken != null && userId != null

    val isAdmin: Boolean
        get() = userRole == Constants.ROLE_ADMIN

    fun clearSession() {
        prefs.edit().clear().commit()  // Use commit() for synchronous clear
    }
}
