package com.fieldtrack.app.features.employee.expenses

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fieldtrack.app.core.components.ListItemCard
import com.fieldtrack.app.core.components.StatusBadge
import com.fieldtrack.app.core.components.toBadgeVariant
import com.fieldtrack.app.core.designsystem.Spacing
import com.fieldtrack.app.core.network.dto.Expense

@Composable
internal fun ExpenseItem(expense: Expense) {
    ListItemCard(
        title = expense.description,
        subtitle = "₹${"%.2f".format(expense.amount)} · ${expense.submittedAt.take(10)}",
        trailing = {
            StatusBadge(
                text = expense.status.lowercase().replaceFirstChar { it.uppercase() },
                variant = expense.status.toBadgeVariant(),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.Micro),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
