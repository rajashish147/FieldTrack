// v1.4 - Admin Session History
package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.AdminSession
import com.fieldtrack.app.databinding.ItemAdminSessionBinding
import com.fieldtrack.app.util.toReadableDateTime

/**
 * RecyclerView adapter for the employee session history list.
 *
 * Displays all sessions (open + closed) returned by the
 * admin_list_sessions_for_user() RPC, ordered newest-first.
 *
 * Each item shows:
 *   - Session date + OPEN / CLOSED status chip
 *   - Check-in timestamp
 *   - Check-out timestamp (or "Still Active" for open sessions)
 *   - Pre-computed duration (server-side)
 *   - "View Route" action button
 *
 * @param onViewRouteClick Invoked when the user taps "View Route" on a session.
 *   Called with the tapped [AdminSession]. The caller is responsible for
 *   navigating to SessionRouteFragment using session.id as the sessionId argument.
 */
class AdminSessionAdapter(
    private val onViewRouteClick: (AdminSession) -> Unit
) : ListAdapter<AdminSession, AdminSessionAdapter.SessionViewHolder>(DIFF_CALLBACK) {

    // ── Adapter overrides ────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemAdminSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding, onViewRouteClick)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ── ViewHolder ───────────────────────────────────────────────────────────

    class SessionViewHolder(
        private val binding: ItemAdminSessionBinding,
        private val onViewRouteClick: (AdminSession) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: AdminSession) {
            val context = binding.root.context

            // ── Date label (extracted from check-in timestamp) ───────────────
            // toReadableDateTime() returns "Today, 8:30 AM", "15 Jan 2024, 8:30 AM", etc.
            // We display only the date portion here; times are shown separately below.
            binding.tvSessionDate.text = extractDateLabel(session.checkInAt)

            // ── Status chip ──────────────────────────────────────────────────
            if (session.isOpen) {
                binding.tvStatusChip.text = "Open"
                binding.tvStatusChip.setTextColor(
                    ContextCompat.getColor(context, R.color.status_approved)
                )
                binding.tvStatusChip.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.session_active_tint)
                    )
            } else {
                binding.tvStatusChip.text = "Closed"
                binding.tvStatusChip.setTextColor(
                    ContextCompat.getColor(context, R.color.on_surface_variant)
                )
                binding.tvStatusChip.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.divider)
                    )
            }

            // ── Check-in time ────────────────────────────────────────────────
            binding.tvCheckInTime.text = session.checkInAt.toReadableDateTime()

            // ── Check-out time ───────────────────────────────────────────────
            if (session.checkOutAt != null) {
                binding.tvCheckOutTime.text = session.checkOutAt.toReadableDateTime()
                binding.tvCheckOutTime.setTextColor(
                    ContextCompat.getColor(context, R.color.on_surface)
                )
            } else {
                // Session still open — no check-out time yet
                binding.tvCheckOutTime.text = context.getString(R.string.session_still_active)
                binding.tvCheckOutTime.setTextColor(
                    ContextCompat.getColor(context, R.color.session_active_accent)
                )
            }

            // ── Duration ─────────────────────────────────────────────────────
            binding.tvDuration.text = session.formatDuration()
            // Open sessions show an "active" accent to signal the value is live
            binding.tvDuration.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (session.isOpen) R.color.session_active_accent else R.color.on_surface_variant
                )
            )

            // ── View Route button ────────────────────────────────────────────
            binding.btnViewRoute.setOnClickListener {
                onViewRouteClick(session)
            }
        }

        /**
         * Extracts a human-readable date label from an ISO 8601 timestamp string.
         *
         * Examples:
         *   "2024-02-15T08:30:00+00:00" → "15 Feb 2024"  (or "Today" / "Yesterday")
         *
         * Falls back to the raw timestamp string if parsing fails.
         */
        private fun extractDateLabel(isoTimestamp: String): String {
            return try {
                val instant = java.time.Instant.parse(
                    // Normalise offset format so Instant.parse handles both
                    // "+00:00" and "Z" suffixes correctly.
                    isoTimestamp.replace("+00:00", "Z")
                )
                val dateTime = java.time.LocalDateTime.ofInstant(
                    instant,
                    java.time.ZoneId.systemDefault()
                )
                val date = dateTime.toLocalDate()
                val today = java.time.LocalDate.now()
                val yesterday = today.minusDays(1)

                when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> dateTime.format(
                        java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
                    )
                }
            } catch (e: Exception) {
                // Robust fallback: show raw string rather than crashing
                isoTimestamp
            }
        }
    }

    // ── DiffUtil ─────────────────────────────────────────────────────────────

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AdminSession>() {
            override fun areItemsTheSame(old: AdminSession, new: AdminSession): Boolean =
                old.id == new.id

            /**
             * Contents are the same when every field matches.
             * For OPEN sessions [durationMinutes] changes on every RPC call, so
             * re-submitting the list after a refresh will correctly rebind the row.
             */
            override fun areContentsTheSame(old: AdminSession, new: AdminSession): Boolean =
                old == new
        }
    }
}
