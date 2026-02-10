package com.fieldtrack.app.util

import com.fieldtrack.app.BuildConfig

// Validates that required build config values are present.
// Used by LoginActivity to show a blocking error instead of crashing.
class ConfigValidator {

    fun isMissingConfig(): Boolean {
        return BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()
    }

    fun getMissingConfigMessage(): String {
        val missing = mutableListOf<String>()
        if (BuildConfig.SUPABASE_URL.isBlank()) missing.add("SUPABASE_URL")
        if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) missing.add("SUPABASE_ANON_KEY")
        return "Missing configuration: ${missing.joinToString(", ")}.\n\nAdd them to local.properties and rebuild."
    }
}
