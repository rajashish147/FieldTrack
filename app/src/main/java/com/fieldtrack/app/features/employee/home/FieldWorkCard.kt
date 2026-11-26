package com.fieldtrack.app.features.employee.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldtrack.app.core.components.FtCard
import com.fieldtrack.app.core.network.dto.FieldSite
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Currency

@Composable
fun WorkLocationsCard(state: HomeUiState, onReach: (FieldSite) -> Unit) {
    val setup = state.fieldSetup ?: return
    val currency = currencyFormat(setup.settings.currency)
    FtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Preset work locations", style = MaterialTheme.typography.titleMedium)
            val sites = setup.sites.sortedBy { it.name }
            if (sites.isEmpty()) Text("No active locations configured.")
            sites.forEach { site ->
                if (site != sites.first()) HorizontalDivider()
                Text(site.name, style = MaterialTheme.typography.titleSmall)
                if (site.address.isNotBlank()) Text(site.address, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${currency.pay(site.visitRateMinor)} per visit · Arrival within ${site.arrivalRadiusM} m",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.arrivalReceipts[site.id]?.let { receipt ->
                    val time = formatFieldTime(receipt.arrivedAt, setup.settings.timezone)
                    val detail = if (receipt.payable == false) "No additional visit pay" else currencyFormat(receipt.currency).pay(receipt.amountMinor)
                    Text("GPS verified at $time · $detail", style = MaterialTheme.typography.bodySmall)
                }
                if (state.activeSession != null) {
                    OutlinedButton(
                        onClick = { onReach(site) },
                        enabled = state.confirmingSiteId == null && !state.isCheckingOut,
                    ) {
                        Text(if (state.confirmingSiteId == site.id) "Checking GPS…" else "I’ve reached ${site.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun FieldWorkCard(state: HomeUiState, onPeriod: (String) -> Unit) {
    FtCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Attendance & earnings", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.fieldPeriod == "day", onClick = { onPeriod("day") }, label = { Text("Today") })
                FilterChip(selected = state.fieldPeriod == "week", onClick = { onPeriod("week") }, label = { Text("Week") })
                FilterChip(selected = state.fieldPeriod == "month", onClick = { onPeriod("month") }, label = { Text("Month") })
                TextButton(onClick = { onPeriod(state.fieldPeriod) }, enabled = !state.fieldLoading) { Text("Refresh") }
            }
            if (state.fieldLoading) Text("Loading report…")
            state.fieldError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.fieldSetup == null && !state.fieldLoading && state.fieldError == null) {
                Text("Your manager has yet to configure field payroll.")
            }
            state.fieldReport?.let { report ->
                val currency = currencyFormat(report.currency)
                val inclusiveEnd = LocalDate.parse(report.toExclusive).minusDays(1).toString()
                Text(
                    if (report.from == inclusiveEnd) report.from else "${report.from} to $inclusiveEnd",
                    style = MaterialTheme.typography.bodySmall,
                )
                val row = report.employees.firstOrNull()
                if (row == null) {
                    Text("No activity for this period.")
                } else {
                    val attendance = row.attendanceCount?.let { "$it check-ins · " }.orEmpty()
                    Text("${row.attendanceDays} attendance days · ${attendance}${row.visitCount} visits")
                    row.workedSeconds?.let { Text("Hours worked · ${hours(it)}") }
                    if (row.hasOpenShift) Text("A shift is still open.", style = MaterialTheme.typography.bodySmall)
                    Text("Visit pay · ${currency.pay(row.visitPayMinor)}")
                    if (state.fieldPeriod == "month") {
                        Text("Monthly basic pay · ${currency.pay(row.basicPayMinor)}")
                        Text(
                            if (row.salaryConfigured) "Monthly total · ${currency.pay(row.totalPayMinor)}" else "Ask your manager to configure basic pay.",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    val days = row.days.orEmpty()
                    if (state.fieldPeriod != "day" && days.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Daily totals", style = MaterialTheme.typography.titleSmall)
                        days.forEach { day ->
                            Text(
                                "${day.date} · ${hours(day.workedSeconds)} · ${day.attendanceCount} check-ins · ${day.visitCount} visits · ${currency.pay(day.visitPayMinor)}" +
                                    if (day.hasOpenShift) " · Open shift" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (row.visits.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Verified visits", style = MaterialTheme.typography.titleSmall)
                        row.visits.forEach { visit ->
                            Text(
                                "${visit.siteName} · ${currency.pay(visit.rateMinor)} · ${formatFieldTime(visit.arrivedAt, report.timezone)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hours(seconds: Long): String = "${seconds / 3600}h ${(seconds % 3600) / 60}m"

private fun currencyFormat(code: String): NumberFormat =
    NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code) }

private fun NumberFormat.pay(amount: Long?): String =
    amount?.let { format(java.math.BigDecimal.valueOf(it, 2)) } ?: "—"
