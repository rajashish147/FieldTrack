package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.databinding.ItemEmployeeBinding

/**
 * Adapter for displaying approved employees in a clean list.
 * No action buttons shown - tap to view details.
 */
class EmployeeAdapter(
    private val onEmployeeClick: (User) -> Unit
) : ListAdapter<User, EmployeeAdapter.EmployeeViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmployeeViewHolder {
        val binding = ItemEmployeeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EmployeeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EmployeeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EmployeeViewHolder(
        private val binding: ItemEmployeeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvEmployeeName.text = user.name.ifBlank { "Unnamed Employee" }
            
            if (user.isActive) {
                binding.tvStatus.text = "Active"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.status_approved)
                )
            } else {
                binding.tvStatus.text = "Inactive"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.status_rejected)
                )
            }
            
            // Show sync time with health-based color
            binding.tvLastSync.text = user.getFormattedLastSync()
            binding.tvLastSync.setTextColor(user.getSyncHealthColor())
            
            // Show sync indicator if synced within the last hour (HEALTHY status)
            val isRecentlySync = user.lastSyncedAt != null && try {
                val syncTime = java.time.Instant.parse(user.lastSyncedAt)
                val now = java.time.Instant.now()
                val hourAgo = now.minusSeconds(3600)
                syncTime.isAfter(hourAgo)
            } catch (e: Exception) {
                false
            }
            
            binding.ivSyncIndicator.visibility = if (isRecentlySync) View.VISIBLE else View.GONE
            
            binding.root.setOnClickListener { onEmployeeClick(user) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(old: User, new: User) = old.id == new.id
            override fun areContentsTheSame(old: User, new: User) = old == new
        }
    }
}
