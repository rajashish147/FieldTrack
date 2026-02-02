package com.fieldtrack.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fieldtrack.app.data.local.dao.ExpenseDao
import com.fieldtrack.app.data.local.dao.LocationDao
import com.fieldtrack.app.data.local.dao.ReportDao
import com.fieldtrack.app.data.local.entity.ExpenseEntity
import com.fieldtrack.app.data.local.entity.LocationEntity
import com.fieldtrack.app.data.local.entity.ReportEntity

@Database(
    entities = [
        LocationEntity::class,
        ReportEntity::class,
        ExpenseEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun reportDao(): ReportDao
    abstract fun expenseDao(): ExpenseDao
}
