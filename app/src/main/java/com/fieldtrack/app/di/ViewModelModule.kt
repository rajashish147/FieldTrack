package com.fieldtrack.app.di

import com.fieldtrack.app.ui.admin.AdminViewModel
import com.fieldtrack.app.ui.auth.LoginViewModel
import com.fieldtrack.app.ui.dashboard.DashboardViewModel
import com.fieldtrack.app.ui.expense.ExpenseViewModel
import com.fieldtrack.app.ui.map.MapViewModel
import com.fieldtrack.app.ui.report.ReportViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel { LoginViewModel(get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get()) }
    viewModel { MapViewModel(get()) }
    viewModel { ReportViewModel(get(), get()) }
    viewModel { ExpenseViewModel(get(), get()) }
    viewModel { AdminViewModel(get(), get(), get(), get()) }
}
