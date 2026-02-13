package com.fieldtrack.app.di

import androidx.room.Room
import com.fieldtrack.app.data.local.AppDatabase
import com.fieldtrack.app.data.remote.SessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {

    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "fieldtrack_db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)  // Add sync state machine migration
            .build()
    }

    single { get<AppDatabase>().locationDao() }
    single { get<AppDatabase>().reportDao() }
    single { get<AppDatabase>().expenseDao() }

    single { SessionManager(androidContext()) }
}
