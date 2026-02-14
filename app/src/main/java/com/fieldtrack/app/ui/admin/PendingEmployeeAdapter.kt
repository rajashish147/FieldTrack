package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.databinding.ItemEmployeeApprovalBinding

/**
 * Adapter for pending employees requiring approval.
 * Shows approve/deactivate action buttons and profile image.
 */
class PendingEmployeeAdapter(
    private val onApprove: (User) -> Unit,
    private val onDeactivate: (User) -> Unit
) : ListAdapter<User, PendingEmployeeAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEmployeeApprovalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemEmployeeApprovalBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvEmployeeName.text = user.name.ifBlank { "Unnamed Employee" }

            // Load profile image
            if (!user.profileImageUrl.isNullOrBlank()) {
                binding.ivProfileImage.load(user.profileImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_image_placeholder)
                    error(R.drawable.bg_image_placeholder)
                    // Add Supabase auth headers for private storage
                    addHeader("Authorization", "Bearer ${com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY}")
                    addHeader("apikey", com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY)
                }
                binding.ivProfileImage.visibility = View.VISIBLE
            } else {
                binding.ivProfileImage.visibility = View.GONE
            }

            if (user.isActive) {
                binding.tvStatus.text = "Active"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.status_approved)
                )
                binding.btnApprove.isEnabled = false
                binding.btnDeactivate.isEnabled = true
            } else {
                binding.tvStatus.text = "Pending"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.status_pending)
                )
                binding.btnApprove.isEnabled = true
                binding.btnDeactivate.isEnabled = false
            }

            binding.btnApprove.setOnClickListener { onApprove(user) }
            binding.btnDeactivate.setOnClickListener { onDeactivate(user) }
            
            // Click on image to view full size
            binding.ivProfileImage.setOnClickListener {
                if (!user.profileImageUrl.isNullOrBlank()) {
                    showFullImage(user.profileImageUrl)
                }
            }
        }
        
        private fun showFullImage(imageUrl: String) {
            // Show full image in a dialog using ImageView
            val imageView = android.widget.ImageView(binding.root.context).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            
            imageView.load(imageUrl) {
                crossfade(true)
                // Add Supabase auth headers for private storage
                addHeader("Authorization", "Bearer ${com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY}")
                addHeader("apikey", com.fieldtrack.app.BuildConfig.SUPABASE_ANON_KEY)
            }
            
            val dialog = android.app.AlertDialog.Builder(binding.root.context)
                .setView(imageView)
                .setPositiveButton("Close", null)
                .create()
            dialog.show()
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(old: User, new: User) = old.id == new.id
            override fun areContentsTheSame(old: User, new: User) = old == new
        }
    }
}
