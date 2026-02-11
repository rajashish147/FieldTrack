package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.databinding.ItemEmployeeTrackingBinding
import com.fieldtrack.app.util.toReadableDateTime

/**
 * RecyclerView adapter for the admin tracking list.
 * Displays each employee's name and last location sync time.
 */
class TrackingAdapter(
    private val onItemClick: (EmployeeLocation) -> Unit
) : ListAdapter<EmployeeLocation, TrackingAdapter.TrackingViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackingViewHolder {
        val binding = ItemEmployeeTrackingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TrackingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TrackingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TrackingViewHolder(
        private val binding: ItemEmployeeTrackingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: EmployeeLocation) {
            binding.tvEmployeeName.text = item.name.ifBlank { "Unnamed Employee" }
            binding.tvLastSync.text = if (item.hasLocation) {
                "Last synced: ${item.lastUpdated.toReadableDateTime()}"
            } else {
                "Never synced"
            }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EmployeeLocation>() {
            override fun areItemsTheSame(old: EmployeeLocation, new: EmployeeLocation) =
                old.userId == new.userId
            override fun areContentsTheSame(old: EmployeeLocation, new: EmployeeLocation) =
                old == new
        }
    }
}
