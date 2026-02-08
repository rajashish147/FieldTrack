package com.fieldtrack.app.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fieldtrack.app.data.model.Report
import com.fieldtrack.app.databinding.ItemAdminReportBinding
import com.fieldtrack.app.util.toReadableDate

class AdminReportAdapter(
    private val employeeNames: Map<String, String>
) : ListAdapter<Report, AdminReportAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminReportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAdminReportBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(report: Report) {
            binding.tvEmployeeName.text = employeeNames[report.userId] ?: report.userId
            binding.tvDate.text = report.date.toReadableDate()
            binding.tvSummary.text = report.summary

            if (!report.optionalImageUrl.isNullOrBlank()) {
                binding.ivReportImage.visibility = View.VISIBLE
                binding.ivReportImage.load(report.optionalImageUrl) { crossfade(true) }
            } else {
                binding.ivReportImage.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Report>() {
        override fun areItemsTheSame(oldItem: Report, newItem: Report) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Report, newItem: Report) = oldItem == newItem
    }
}
