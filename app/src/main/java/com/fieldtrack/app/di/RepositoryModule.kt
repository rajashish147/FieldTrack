package com.fieldtrack.app.di

import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import org.koin.dsl.module

val repositoryModule = module {

    single { AuthRepository(get(), get(), get()) }
    single { LocationRepository(get(), get()) }
    single { ReportRepository(get(), get()) }
    single { ExpenseRepository(get(), get()) }
}
