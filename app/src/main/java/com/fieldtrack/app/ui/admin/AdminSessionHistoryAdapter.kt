// v1.4 - Admin Session History
package com.fieldtrack.app.ui.admin

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.AdminSession
import com.fieldtrack.app.databinding.ItemAdminSessionHistoryBinding
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RecyclerView adapter for [AdminSessionHistoryFragment].
 *
 * Displays one card per [AdminSession], ordered newest-first as returned by
 * admin_list_sessions_for_user(). Each card shows:
 *
 *   - Date derived from [AdminSession.checkInAt]  ("Today · 8:30 AM",
 *     "Yesterday · 2:00 PM", or "15 Feb 2024 · 9:00 AM")
 *   - Duration pre-computed by the server, formatted as HH:mm
 *     (e.g. 90 minutes → "01:30", 8 h 45 m → "08:45")
 *   - Status chip: "OPEN" (green tint) or "CLOSED" (neutral)
 *
 * The entire card is the click target. [onSessionClick] is called with the
 * tapped [AdminSession]; the fragment is responsible for navigating to
 * AdminSessionRouteFragment.
 *
 * @param onSessionClick Invoked on card tap. Receives the tapped [AdminSession].
 */
class AdminSessionHistoryAdapter(
    private val onSessionClick: (AdminSession) -> Unit
) : ListAdapter<AdminSession, AdminSessionHistoryAdapter.ViewHolder>(DiffCallback) {

    // ── Adapter overrides ────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminSessionHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ── ViewHolder ───────────────────────────────────────────────────────────

    inner class ViewHolder(
        private val binding: ItemAdminSessionHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: AdminSession) {
            binding.tvDate.text = formatDate(session.checkInAt)
            binding.tvDuration.text = formatDurationHHmm(session.durationMinutes)
            bindStatusChip(session)
            binding.root.setOnClickListener { onSessionClick(session) }
        }

        // ── Status chip ──────────────────────────────────────────────────────

        private fun bindStatusChip(session: AdminSession) {
            val context = binding.root.context

            if (session.isOpen) {
                binding.tvStatus.text = "OPEN"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.status_approved)
                )
                binding.tvStatus.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.session_active_tint)
                )
            } else {
                binding.tvStatus.text = "CLOSED"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.on_surface_variant)
                )
                binding.tvStatus.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.divider)
                )
            }
        }

        // ── Formatters ───────────────────────────────────────────────────────

        /**
         * Converts an ISO 8601 timestamp to a human-readable date + time label.
         *
         * Output examples:
         *   "Today · 8:30 AM"
         *   "Yesterday · 2:15 PM"
         *   "15 Feb 2024 · 9:00 AM"
         *
         * Both "+00:00" and "Z" offset suffixes are handled. Falls back to the
         * raw string if parsing fails so the UI never crashes or shows blank.
         */
        private fun formatDate(isoTimestamp: String): String {
            return try {
                val instant = Instant.parse(isoTimestamp.replace("+00:00", "Z"))
                val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                val date = dateTime.toLocalDate()
                val today = LocalDate.now()

                val timePart = dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))

                when (date) {
                    today -> "Today · $timePart"
                    today.minusDays(1) -> "Yesterday · $timePart"
                    else -> dateTime.format(
                        DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a")
                    )
                }
            } catch (_: Exception) {
                isoTimestamp   // safe fallback — never blank, never crashes
            }
        }

        /**
         * Formats a duration given in whole minutes as a zero-padded HH:mm string.
         *
         * Examples:
         *   0   → "00:00"
         *   45  → "00:45"
         *   60  → "01:00"
         *   90  → "01:30"
         *   605 → "10:05"
         *
         * Hours are not capped — a 25-hour session renders "25:00".
         */
        private fun formatDurationHHmm(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return "%02d:%02d".format(h, m)
        }
    }

    // ── DiffUtil ─────────────────────────────────────────────────────────────

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<AdminSession>() {

            /** Identity check — sessions are uniquely identified by their UUID. */
            override fun areItemsTheSame(
                old: AdminSession,
                new: AdminSession
            ): Boolean = old.id == new.id

            /**
             * Content check — data class equality covers all fields.
             *
             * For OPEN sessions [AdminSession.durationMinutes] is recomputed on
             * every server call, so re-submitting the list after a pull-to-refresh
             * will correctly trigger a rebind for any session whose duration changed.
             */
            override fun areContentsTheSame(
                old: AdminSession,
                new: AdminSession
            ): Boolean = old == new
        }
    }
}
