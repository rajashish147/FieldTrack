package com.fieldtrack.app.data.model

/**
 * Type-safe user role enum.
 * Matches Supabase ENUM values (uppercase).
 * Fails fast on unknown roles — never silently downgrades privileges.
 *
 * For Supabase REST API filters, always use [name] (e.g. "eq.EMPLOYEE").
 * Never use lowercase — Supabase ENUM filtering is case-sensitive.
 */
enum class UserRole {
    ADMIN,
    EMPLOYEE;

    companion object {
        /**
         * Parse a backend role string strictly.
         * Handles any casing ("ADMIN", "admin", "Admin") via uppercase conversion.
         * Throws on unknown roles — fail fast, never silently downgrade.
         */
        fun fromString(role: String): UserRole =
            valueOf(role.uppercase())
    }
}
