// P2.1 - Admin Open Sessions
package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.data.model.OpenSession
import com.fieldtrack.app.databinding.ItemOpenSessionBinding

/**
 * Adapter for displaying open attendance sessions.
 * Shows employee name, email, check-in time, and live duration.
 */
class OpenSessionsAdapter(
    // P2.2 - Force Check-Out callback
    private val onForceCheckOutClick: ((OpenSession) -> Unit)? = null
) : ListAdapter<OpenSession, OpenSessionsAdapter.SessionViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemOpenSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SessionViewHolder(binding, onForceCheckOutClick)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SessionViewHolder(
        private val binding: ItemOpenSessionBinding,
        private val onForceCheckOutClick: ((OpenSession) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: OpenSession) {
            binding.tvEmployeeName.text = session.name
            binding.tvEmail.text = session.email ?: "No email"
            binding.tvCheckInTime.text = session.getFormattedCheckInTime()
            binding.tvDuration.text = session.getFormattedDuration()

            // P2.2 - Force Check-Out button
            binding.btnForceCheckout.setOnClickListener {
                onForceCheckOutClick?.invoke(session)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<OpenSession>() {
            override fun areItemsTheSame(old: OpenSession, new: OpenSession) =
                old.sessionId == new.sessionId

            override fun areContentsTheSame(old: OpenSession, new: OpenSession) =
                old == new
        }
    }
}
