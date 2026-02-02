package com.fieldtrack.app.util

object Constants {

    // Notification Channels
    const val NOTIFICATION_CHANNEL_LOCATION = "location_tracking_channel"
    const val NOTIFICATION_CHANNEL_GENERAL = "general_channel"
    const val NOTIFICATION_ID_LOCATION = 1001

    // Location Tracking
    const val LOCATION_INTERVAL_MS = 2 * 60 * 1000L       // 2 minutes
    const val LOCATION_MAX_INTERVAL_MS = 5 * 60 * 1000L   // 5 minutes
    const val LOCATION_MIN_DISPLACEMENT_M = 10f            // 10 meters

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
