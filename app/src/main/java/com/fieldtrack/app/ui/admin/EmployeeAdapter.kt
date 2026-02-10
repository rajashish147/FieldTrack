package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.databinding.ItemEmployeeApprovalBinding

/**
 * RecyclerView adapter for displaying employees in admin list/approval views.
 * Uses ListAdapter with DiffUtil for efficient updates.
 */
class EmployeeAdapter(
    private val onApprove: (User) -> Unit,
    private val onDeactivate: (User) -> Unit
) : ListAdapter<User, EmployeeAdapter.EmployeeViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmployeeViewHolder {
        val binding = ItemEmployeeApprovalBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EmployeeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EmployeeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EmployeeViewHolder(
        private val binding: ItemEmployeeApprovalBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvEmployeeName.text = user.name.ifBlank { "Unnamed Employee" }

            // Status badge
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
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(old: User, new: User) = old.id == new.id
            override fun areContentsTheSame(old: User, new: User) = old == new
        }
    }
}
