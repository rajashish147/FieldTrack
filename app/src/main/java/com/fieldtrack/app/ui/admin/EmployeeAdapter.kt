package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.User
import com.fieldtrack.app.databinding.ItemEmployeeBinding

class EmployeeAdapter(
    private val onItemClick: (User) -> Unit
) : ListAdapter<User, EmployeeAdapter.EmployeeViewHolder>(EmployeeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmployeeViewHolder {
        val binding = ItemEmployeeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EmployeeViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: EmployeeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EmployeeViewHolder(
        private val binding: ItemEmployeeBinding,
        private val onItemClick: (User) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvEmployeeName.text = user.name
            binding.tvCheckInStatus.text = if (user.isCheckedIn) "Checked In" else "Checked Out"
            binding.tvCheckInStatus.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (user.isCheckedIn) R.color.status_approved else R.color.status_rejected
                )
            )
            binding.root.setOnClickListener { onItemClick(user) }
        }
    }

    class EmployeeDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
