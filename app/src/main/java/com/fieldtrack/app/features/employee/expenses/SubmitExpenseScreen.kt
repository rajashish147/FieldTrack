package com.fieldtrack.app.features.employee.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.fieldtrack.app.core.components.FtCard
import com.fieldtrack.app.core.components.FtTopBar
import com.fieldtrack.app.core.components.InputField
import com.fieldtrack.app.core.components.PrimaryButton
import com.fieldtrack.app.core.designsystem.FtTypography
import com.fieldtrack.app.core.designsystem.Spacing

@Composable
fun SubmitExpenseScreen(
    onSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.submitState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            FtTopBar(
                title = "Submit Expense",
                showBackButton = true,
                onBackClick = onBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.MD),
            verticalArrangement = Arrangement.spacedBy(Spacing.MD),
        ) {
            FtCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Expense Details",
                    style = FtTypography.H4,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.MD))

                InputField(
                    value = state.amount,
                    onValueChange = viewModel::updateAmount,
                    label = "Amount (₹)",
                    placeholder = "0.00",
                    leadingIcon = {
                        Icon(
                            Icons.Default.CurrencyRupee,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.error != null && state.amount.toDoubleOrNull() == null,
                )

                Spacer(Modifier.height(Spacing.MD))

                InputField(
                    value = state.description,
                    onValueChange = viewModel::updateDescription,
                    label = "Description",
                    placeholder = "What was this expense for?",
                    singleLine = false,
                    maxLines = 4,
                    isError = state.error != null && state.description.isBlank(),
                )

                state.error?.let { message ->
                    Spacer(Modifier.height(Spacing.SM))
                    Text(
                        message,
                        style = FtTypography.BodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(Spacing.LG))

                PrimaryButton(
                    text = if (state.success) "Done" else "Submit Expense",
                    onClick = { if (state.success) onSuccess() else viewModel.submitExpense(onSuccess) },
                    loading = state.isLoading,
                    fullWidth = true,
                )
            }
        }
    }
}
