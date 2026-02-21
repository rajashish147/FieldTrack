package com.fieldtrack.app.di

import com.fieldtrack.app.data.repository.AdminRepository
import com.fieldtrack.app.data.repository.AuthRepository
import com.fieldtrack.app.data.repository.ExpenseRepository
import com.fieldtrack.app.data.repository.LocationRepository
import com.fieldtrack.app.data.repository.ReportRepository
import org.koin.dsl.module

val repositoryModule = module {

    single { AuthRepository(get(), get(), get()) }
    single { LocationRepository(get(), get(), get(), get()) }
    single { ReportRepository(get(), get(), get()) }
    single { ExpenseRepository(get(), get(), get()) }
    // P2.1 - Admin Open Sessions: Inject AttendanceApi into AdminRepository
    single { AdminRepository(get(), get()) }
    // Inject LocationRepository for coordinated checkout
    single { com.fieldtrack.app.data.repository.AttendanceSessionRepository(get(), get(), get()) }
    
    // Phase 2 Beta: Session route visualization
    single { com.fieldtrack.app.data.repository.SessionRouteRepository(get(), get()) }
}
