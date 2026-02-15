package com.fieldtrack.app.util

object SecurityUtils {

    private const val MIN_PASSWORD_LENGTH = 6
    private const val MAX_INPUT_LENGTH = 500
    private const val RATE_LIMIT_MS = 5000L // 5 seconds between actions
    
    // Velocity calculation constants
    private const val MILLIS_PER_SECOND = 1000.0
    private const val MPS_TO_KMH_MULTIPLIER = 3.6
    private const val MAX_REASONABLE_SPEED_KMH = 200.0 // Highway driving

    private val lastActionTimestamps = mutableMapOf<String, Long>()

    fun sanitizeInput(input: String): String {
        return input
            .trim()
            .replace(Regex("[<>\"';&|]"), "")
            .take(MAX_INPUT_LENGTH)
    }

    fun isValidAmount(amountStr: String): Boolean {
        val amount = amountStr.toDoubleOrNull() ?: return false
        return amount > 0 && amount < 1_000_000
    }

    fun isPasswordStrong(password: String): Boolean {
        return password.length >= MIN_PASSWORD_LENGTH
    }

    fun isRateLimited(actionKey: String): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastActionTimestamps[actionKey] ?: 0L
        return if (now - lastTime < RATE_LIMIT_MS) {
            true
        } else {
            lastActionTimestamps[actionKey] = now
            false
        }
    }

    fun clearRateLimits() {
        lastActionTimestamps.clear()
    }

    fun isVelocityReasonable(
        lat1: Double, lon1: Double, timestamp1: Long,
        lat2: Double, lon2: Double, timestamp2: Long
    ): Boolean {
        val timeDiffSeconds = (timestamp2 - timestamp1) / MILLIS_PER_SECOND
        if (timeDiffSeconds <= 0) return false

        val distanceMeters = LocationUtils.calculateDistance(lat1, lon1, lat2, lon2)
        val speedMps = distanceMeters / timeDiffSeconds
        val speedKmh = speedMps * MPS_TO_KMH_MULTIPLIER

        return speedKmh <= MAX_REASONABLE_SPEED_KMH
    }
}
