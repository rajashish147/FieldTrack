package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
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
            // Avatar
            if (!user.profileImageUrl.isNullOrBlank()) {
                binding.ivAvatar.load(user.profileImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_people)
                    error(R.drawable.ic_people)
                    transformations(CircleCropTransformation())
                }
                binding.ivAvatar.imageTintList = null
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_people)
                binding.ivAvatar.imageTintList = ContextCompat.getColorStateList(
                    binding.root.context, R.color.on_surface_variant
                )
            }
            
            binding.tvEmployeeName.text = user.name.ifBlank { "Unnamed Employee" }
            
            if (user.isCheckedIn) {
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
            
            // PHASE C - Sync Health UI: Show health dot and relative time
            updateSyncHealthDisplay(user)
            
            binding.root.setOnClickListener { onEmployeeClick(user) }
        }
        
        // PHASE C - Sync Health UI: Update sync health indicator with colored dot and relative time
        private fun updateSyncHealthDisplay(user: User) {
            val healthStatus = user.getSyncHealthStatus()
            
            // Set dot color based on health status
            val dotDrawable = when (healthStatus) {
                com.fieldtrack.app.data.model.SyncHealthStatus.HEALTHY -> R.drawable.bg_dot_green
                com.fieldtrack.app.data.model.SyncHealthStatus.STALE -> R.drawable.bg_dot_yellow
                com.fieldtrack.app.data.model.SyncHealthStatus.VERY_STALE -> R.drawable.bg_dot_red
                com.fieldtrack.app.data.model.SyncHealthStatus.NEVER_SYNCED -> R.drawable.bg_dot_gray
                com.fieldtrack.app.data.model.SyncHealthStatus.UNKNOWN -> R.drawable.bg_dot_gray
            }
            binding.viewSyncHealthDot.setBackgroundResource(dotDrawable)
            
            // Use existing getSyncFreshnessIndicator() for consistent formatting
            val syncText = user.getSyncFreshnessIndicator()
            binding.tvLastSync.text = syncText
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(old: User, new: User) = old.id == new.id
            override fun areContentsTheSame(old: User, new: User) = old == new
        }
    }
}
