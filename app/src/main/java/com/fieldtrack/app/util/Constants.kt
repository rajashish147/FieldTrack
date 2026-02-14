package com.fieldtrack.app.util

object Constants {

    // Notification Channels
    const val NOTIFICATION_CHANNEL_LOCATION = "location_tracking_channel"
    const val NOTIFICATION_CHANNEL_GENERAL = "general_channel"
    const val NOTIFICATION_ID_LOCATION = 1001

    // Location Tracking (TEMPORARILY LOWERED FOR TESTING — revert before production)
    const val LOCATION_INTERVAL_MS = 30 * 1000L              // 30 seconds
    const val LOCATION_MAX_INTERVAL_MS = 60 * 1000L           // 1 minute
    const val LOCATION_MIN_DISPLACEMENT_M = 0f                // fire on any movement
    const val LOCATION_ACCURACY_THRESHOLD_M = 100f            // accept indoor GPS
    const val LOCATION_SYNC_BATCH_SIZE = 2                    // sync after every 2 locations
    const val LOCATION_CLEANUP_AGE_DAYS = 7                // delete synced data older than 7 days
    const val SYNC_PERIODIC_INTERVAL_MIN = 15L             // WorkManager periodic sync interval

    // Supabase Tables
    const val TABLE_USERS = "users"
    const val TABLE_LOCATIONS = "locations"
    const val TABLE_REPORTS = "reports"
    const val TABLE_EXPENSES = "expenses"

    // SharedPreferences
    const val PREFS_NAME = "field_track_prefs"
    const val PREF_ACCESS_TOKEN = "access_token"
    const val PREF_REFRESH_TOKEN = "refresh_token"
    const val PREF_USER_ID = "user_id"
    const val PREF_USER_ROLE = "user_role"
    const val PREF_USER_NAME = "user_name"
    const val PREF_IS_CHECKED_IN = "is_checked_in"
    const val PREF_CHECK_IN_TIME = "check_in_time"
    const val PREF_LAST_CHECK_OUT_TIME = "last_check_out_time"
    const val PREF_IS_ACTIVE = "is_active"
    const val PREF_EXPIRES_AT = "token_expires_at"
    const val PREF_PROFILE_IMAGE_URL = "profile_image_url"

    // Intent Extras
    const val EXTRA_USER_ID = "extra_user_id"

    // Work Manager Tags
    const val WORK_SYNC_LOCATIONS = "sync_locations"
    const val WORK_SYNC_REPORTS = "sync_reports"
    const val WORK_SYNC_EXPENSES = "sync_expenses"

    // Expense Categories
    val EXPENSE_CATEGORIES = listOf(
        "Travel",
        "Food",
        "Accommodation",
        "Communication",
        "Equipment",
        "Other"
    )

    // Expense Status
    const val EXPENSE_STATUS_PENDING = "pending"
    const val EXPENSE_STATUS_APPROVED = "approved"
    const val EXPENSE_STATUS_REJECTED = "rejected"

    // User Roles
    const val ROLE_EMPLOYEE = "employee"
    const val ROLE_ADMIN = "admin"
}
