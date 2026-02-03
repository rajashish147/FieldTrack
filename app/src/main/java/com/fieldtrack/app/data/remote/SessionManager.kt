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

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        Constants.PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var accessToken: String?
        get() = prefs.getString(Constants.PREF_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.PREF_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(Constants.PREF_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.PREF_REFRESH_TOKEN, value).apply()

    var userId: String?
        get() = prefs.getString(Constants.PREF_USER_ID, null)
        set(value) = prefs.edit().putString(Constants.PREF_USER_ID, value).apply()

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

    var lastCheckOutTime: Long
        get() = prefs.getLong(Constants.PREF_LAST_CHECK_OUT_TIME, 0L)
        set(value) = prefs.edit().putLong(Constants.PREF_LAST_CHECK_OUT_TIME, value).apply()

    val isLoggedIn: Boolean
        get() = accessToken != null && userId != null

    val isAdmin: Boolean
        get() = userRole == Constants.ROLE_ADMIN

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
