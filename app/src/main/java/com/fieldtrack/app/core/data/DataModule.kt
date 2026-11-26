package com.fieldtrack.app.core.data

import android.content.Context
import androidx.room.Room
import com.fieldtrack.app.core.data.local.AppDatabase
import com.fieldtrack.app.core.data.local.AppDatabaseMigrations
import com.fieldtrack.app.core.data.local.LocationQueueDao
import com.fieldtrack.app.core.data.local.PendingExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fieldtrack.db")
            // Production migration — preserves all existing data
            .addMigrations(*AppDatabaseMigrations.ALL)
            // NO fallbackToDestructiveMigration — data loss is unacceptable
            .build()

    @Provides
    @Singleton
    fun provideLocationQueueDao(db: AppDatabase): LocationQueueDao =
        db.locationQueueDao()

    @Provides
    @Singleton
    fun providePendingExpenseDao(db: AppDatabase): PendingExpenseDao =
        db.pendingExpenseDao()
}
