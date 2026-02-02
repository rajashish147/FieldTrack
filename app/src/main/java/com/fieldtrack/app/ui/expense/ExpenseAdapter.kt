package com.fieldtrack.app.ui.expense

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldtrack.app.R
import com.fieldtrack.app.data.model.Expense
import com.fieldtrack.app.databinding.ItemExpenseBinding
import com.fieldtrack.app.util.Constants
import com.fieldtrack.app.util.formatCurrency
import com.fieldtrack.app.util.toReadableDateTime

class ExpenseAdapter : ListAdapter<Expense, ExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExpenseViewHolder(
        private val binding: ItemExpenseBinding
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
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Expense, newItem: Expense) = oldItem == newItem
    }
}
