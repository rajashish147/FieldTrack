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
import com.fieldtrack.app.data.model.Expense
import com.fieldtrack.app.databinding.ItemAdminExpenseBinding
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.formatCurrency
import com.fieldtrack.app.util.toReadableDateTime

class AdminExpenseAdapter(
    private val employeeNames: Map<String, String>,
    private val authToken: String,
    private val onApprove: (Expense) -> Unit,
    private val onReject: (Expense) -> Unit,
    private val onImageClick: (String) -> Unit = {}
) : ListAdapter<Expense, AdminExpenseAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAdminExpenseBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            binding.tvAmount.text = expense.amount.formatCurrency()
            binding.tvCategory.text = expense.category
            binding.tvTimestamp.text = expense.timestamp.toReadableDateTime()
            binding.tvEmployeeName.text = employeeNames[expense.userId] ?: expense.userId
            binding.tvStatus.text = expense.status.replaceFirstChar { it.uppercase() }

            val statusColor = when (expense.status) {
                Constants.EXPENSE_STATUS_APPROVED -> R.color.status_approved
                Constants.EXPENSE_STATUS_REJECTED -> R.color.status_rejected
                else -> R.color.status_pending
            }
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(binding.root.context, statusColor)
            )

            if (expense.billImageUrl.isNotBlank()) {
                binding.ivBillImage.visibility = View.VISIBLE
                val imageUrl = "https://xtmbtgkeyhnlvgqibngj.supabase.co/storage/v1/object/authenticated/${expense.billImageUrl}"
                binding.ivBillImage.load(imageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image_placeholder)
                    error(R.drawable.ic_image_error)
                    memoryCacheKey(expense.billImageUrl)
                    diskCacheKey(expense.billImageUrl)
                    addHeader("Authorization", "Bearer $authToken")
                }
                binding.ivBillImage.setOnClickListener { onImageClick(expense.billImageUrl) }
                binding.tvViewBill.visibility = View.VISIBLE
                binding.tvViewBill.setOnClickListener { onImageClick(expense.billImageUrl) }
            } else {
                binding.ivBillImage.visibility = View.GONE
                binding.tvViewBill.visibility = View.GONE
            }

            if (expense.status == Constants.EXPENSE_STATUS_PENDING) {
                binding.llActions.visibility = View.VISIBLE
                binding.btnApprove.setOnClickListener { onApprove(expense) }
                binding.btnReject.setOnClickListener { onReject(expense) }
            } else {
                binding.llActions.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Expense, newItem: Expense) = oldItem == newItem
    }
}
