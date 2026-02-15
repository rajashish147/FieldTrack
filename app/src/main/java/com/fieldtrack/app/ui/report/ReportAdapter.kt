package com.fieldtrack.app.ui.report

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fieldtrack.app.data.model.Report
import com.fieldtrack.app.databinding.ItemReportBinding
import com.fieldtrack.app.util.toReadableDate

class ReportAdapter(
    private val authToken: String
) : ListAdapter<Report, ReportAdapter.ReportViewHolder>(ReportDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val binding = ItemReportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ReportViewHolder(binding, authToken)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ReportViewHolder(
        private val binding: ItemReportBinding,
        private val authToken: String
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(report: Report) {
            binding.tvDate.text = report.date.toReadableDate()
            binding.tvSummary.text = report.summary

            if (!report.optionalImageUrl.isNullOrBlank()) {
                binding.ivReportImage.visibility = View.VISIBLE
                val imageUrl = "https://xtmbtgkeyhnlvgqibngj.supabase.co/storage/v1/object/authenticated/${report.optionalImageUrl}"
                binding.ivReportImage.load(imageUrl) {
                    crossfade(true)
                    memoryCacheKey(report.optionalImageUrl)
                    diskCacheKey(report.optionalImageUrl)
                    addHeader("Authorization", "Bearer $authToken")
                }
            } else {
                binding.ivReportImage.visibility = View.GONE
            }
        }
    }

    class ReportDiffCallback : DiffUtil.ItemCallback<Report>() {
        override fun areItemsTheSame(oldItem: Report, newItem: Report) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Report, newItem: Report) = oldItem == newItem
    }
}
