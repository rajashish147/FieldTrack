package com.fieldtrack.app.ui.expense

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
import com.fieldtrack.app.databinding.ItemExpenseBinding
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.formatCurrency
import com.fieldtrack.app.util.toReadableDateTime

class ExpenseAdapter(
    private val authToken: String
) : ListAdapter<Expense, ExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ExpenseViewHolder(binding, authToken)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExpenseViewHolder(
        private val binding: ItemExpenseBinding,
        private val authToken: String
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            binding.tvAmount.text = expense.amount.formatCurrency()
            binding.tvCategory.text = expense.category
            binding.tvTimestamp.text = expense.timestamp.toReadableDateTime()
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
            } else {
                binding.ivBillImage.visibility = View.GONE
            }
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Expense, newItem: Expense) = oldItem == newItem
    }
}
