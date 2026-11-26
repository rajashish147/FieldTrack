package com.fieldtrack.app.features.admin.employees

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.components.BadgeVariant
import com.fieldtrack.app.core.components.ListItemCard
import com.fieldtrack.app.core.components.StatusBadge
import com.fieldtrack.app.core.designsystem.FtShape
import com.fieldtrack.app.core.designsystem.FtTypography
import com.fieldtrack.app.core.network.dto.Employee

@Composable
internal fun EmployeeListItem(employee: Employee, onClick: () -> Unit) {
    ListItemCard(
        title = employee.name,
        subtitle = "Code: ${employee.employeeCode} · ${if (employee.isActive) "Active" else "Inactive"}",
        leading = {
            Surface(
                shape = FtShape.Pill,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        employee.name.first().uppercase(),
                        style = FtTypography.LabelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailing = {
            StatusBadge(
                if (employee.isActive) "Active" else "Inactive",
                if (employee.isActive) BadgeVariant.SUCCESS else BadgeVariant.NEUTRAL,
            )
        },
        onClick = onClick,
    )
}
