package com.fieldtrack.app.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Centralized, production-grade time handling for FieldTrack.
 *
 * Single source of truth for ALL timestamp parsing, formatting, and duration
 * calculations. Uses java.time exclusively — no SimpleDateFormat.
 *
 * All display output is in Asia/Kolkata (IST, GMT+5:30).
 * All stored timestamps are UTC ISO-8601 (Supabase standard).
 *
 * Every public function is null-safe and crash-proof.
 */
object TimeUtils {

    /** Target display timezone — all user-facing output uses this zone. */
    private val INDIA_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    // ── Pre-allocated formatters (thread-safe) ──────────────────────────────

    private val TIME_ONLY = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val DATE_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val DATE_FULL = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.US)
    private val DATE_SIMPLE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
    private val ISO_OUTPUT = DateTimeFormatter.ISO_INSTANT

    // ═══════════════════════════════════════════════════════════════════════
    // 1. PARSE — UTC string → ZonedDateTime (in INDIA_ZONE)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Parses a UTC ISO-8601 timestamp and converts it to Asia/Kolkata.
     *
     * Handles all Supabase formats:
     *   - `2026-03-01T06:43:13.551374+00:00`
     *   - `2026-03-01T06:43:13Z`
     *   - `2026-03-01T06:43:13.551374Z`
     *
     * @return ZonedDateTime in Asia/Kolkata, or null if input is null/invalid.
     */
    fun parseUtc(utc: String?): ZonedDateTime? {
        if (utc.isNullOrBlank()) return null
        return try {
            // Supabase sends "+00:00" offset — normalise for Instant.parse
            val normalised = utc.trim().replace("+00:00", "Z")
            val instant = Instant.parse(normalised)
            instant.atZone(INDIA_ZONE)
        } catch (_: DateTimeParseException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. DISPLAY — user-friendly relative date + time
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Formats a UTC timestamp for display with relative date.
     *
     * Examples:
     *   - `"Today, 12:13 PM"`
     *   - `"Yesterday, 9:45 AM"`
     *   - `"28 Feb 2026, 5:45 AM"`
     *
     * @return Formatted string, or `"—"` if input is null/invalid.
     */
    fun formatForDisplay(utc: String?): String {
        val zdt = parseUtc(utc) ?: return "—"
        val date = zdt.toLocalDate()
        val today = LocalDate.now(INDIA_ZONE)
        val timePart = zdt.format(TIME_ONLY)

        return when (date) {
            today -> "Today, $timePart"
            today.minusDays(1) -> "Yesterday, $timePart"
            else -> zdt.format(DATE_TIME)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. TIME ONLY — "5:45 AM"
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Extracts only the time portion from a UTC timestamp.
     *
     * @return Time string like `"5:45 AM"`, or `"—"` if invalid.
     */
    fun formatTimeOnly(utc: String?): String {
        val zdt = parseUtc(utc) ?: return "—"
        return zdt.format(TIME_ONLY)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. DATE ONLY — "28 Feb 2026"
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Formats only the date portion of a UTC timestamp.
     *
     * Also handles plain date strings like `"2026-02-28"`.
     *
     * @return Date string like `"28 Feb 2026"`, or `"—"` if invalid.
     */
    fun formatDateOnly(utc: String?): String {
        if (utc.isNullOrBlank()) return "—"

        // Try as ISO-8601 timestamp first
        val zdt = parseUtc(utc)
        if (zdt != null) return zdt.format(DATE_FULL)

        // Fallback: parse as plain date string (yyyy-MM-dd)
        return try {
            val date = LocalDate.parse(utc)
            date.format(DATE_SIMPLE)
        } catch (_: Exception) {
            utc
        }
    }

    /**
     * Extracts a date label from a UTC timestamp, with relative Today/Yesterday.
     *
     * Examples:
     *   - `"Today"`
     *   - `"Yesterday"`
     *   - `"15 Feb 2026"`
     *
     * @return Date label, or `"—"` if invalid.
     */
    fun formatDateLabel(utc: String?): String {
        val zdt = parseUtc(utc) ?: return "—"
        val date = zdt.toLocalDate()
        val today = LocalDate.now(INDIA_ZONE)

        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> zdt.format(DATE_FULL)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. SESSION DURATION — human-readable elapsed time
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculates and formats the duration between two UTC timestamps.
     *
     * If [endUtc] is null, calculates elapsed time from [startUtc] to now.
     *
     * Examples: `"5m"`, `"32m"`, `"2h 15m"`, `"8h"`.
     *
     * @return Duration string, or `"—"` if start is invalid.
     */
    fun formatDuration(startUtc: String?, endUtc: String? = null): String {
        val start = parseUtc(startUtc) ?: return "—"
        val end = endUtc?.let { parseUtc(it) } ?: ZonedDateTime.now(INDIA_ZONE)

        val duration = Duration.between(start, end)
        if (duration.isNegative) return "—"

        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    /**
     * Formats a pre-computed duration in minutes (from server-side calculation).
     *
     * Examples: `"0m"`, `"45m"`, `"1h 30m"`, `"10h 5m"`.
     */
    fun formatDurationMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /**
     * Formats a pre-computed duration in minutes as zero-padded HH:mm.
     *
     * Examples: `"00:00"`, `"00:45"`, `"01:30"`, `"10:05"`.
     */
    fun formatDurationHHmm(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. RELATIVE TIME — "Just now", "5 min ago"
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Formats a UTC timestamp as a relative time string.
     *
     * Examples:
     *   - `"Just now"`      (< 1 min)
     *   - `"5 min ago"`     (1–59 min)
     *   - `"2 hr ago"`      (1–23 hr)
     *   - `"Yesterday"`     (1 day)
     *   - `"3 days ago"`    (2–6 days)
     *   - `"28 Feb 2026"`   (7+ days)
     *
     * @return Relative string, or `"—"` if invalid.
     */
    fun formatRelativeTime(utc: String?): String {
        val zdt = parseUtc(utc) ?: return "—"
        val now = ZonedDateTime.now(INDIA_ZONE)
        val duration = Duration.between(zdt, now)

        if (duration.isNegative) return "Just now"

        val minutes = duration.toMinutes()
        val hours = duration.toHours()
        val days = duration.toDays()

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hr ago"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> zdt.format(DATE_FULL)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. UTC OUTPUT — for Supabase inserts
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the current time as an ISO-8601 UTC string.
     *
     * Output format: `2026-03-01T10:15:30.123Z`
     * Compatible with Supabase timestamptz columns.
     */
    fun nowUtc(): String = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString()

    /**
     * Returns today's date as a `yyyy-MM-dd` string in UTC.
     *
     * Compatible with Supabase date columns.
     */
    fun todayDateUtc(): String = LocalDate.now(ZoneId.of("UTC")).toString()

    // ═══════════════════════════════════════════════════════════════════════
    // 8. DURATION MINUTES — numeric helper for calculations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Calculates the duration in minutes between two UTC timestamps.
     *
     * If [endUtc] is null, calculates from [startUtc] to now.
     *
     * @return Duration in minutes, or 0 if either timestamp is invalid.
     */
    fun durationMinutes(startUtc: String?, endUtc: String? = null): Long {
        val start = parseUtc(startUtc) ?: return 0L
        val end = endUtc?.let { parseUtc(it) } ?: ZonedDateTime.now(INDIA_ZONE)

        val duration = Duration.between(start, end)
        return if (duration.isNegative) 0L else duration.toMinutes()
    }
}
