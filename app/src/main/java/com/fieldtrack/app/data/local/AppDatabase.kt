package com.fieldtrack.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,  // v1.4 Phase 2: Add session_id column to locations
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun reportDao(): ReportDao
    abstract fun expenseDao(): ExpenseDao
    
    companion object {
        /**
         * Migration from version 1 to 2: Add sync state machine fields to locations.
         * Replaces binary isSynced with explicit SyncState enum and tracking fields.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new sync tracking columns
                db.execSQL("""
                    ALTER TABLE locations 
                    ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                """)
                db.execSQL("""
                    ALTER TABLE locations 
                    ADD COLUMN syncAttempts INTEGER NOT NULL DEFAULT 0
                """)
                db.execSQL("""
                    ALTER TABLE locations 
                    ADD COLUMN lastSyncAttemptAt INTEGER
                """)
                db.execSQL("""
                    ALTER TABLE locations 
                    ADD COLUMN syncErrorMessage TEXT
                """)
                db.execSQL("""
                    ALTER TABLE locations 
                    ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0
                """)
                
                // Migrate existing data: isSynced=1 → SYNCED, isSynced=0 → LOCAL_ONLY
                db.execSQL("""
                    UPDATE locations 
                    SET syncState = CASE 
                        WHEN isSynced = 1 THEN 'SYNCED' 
                        ELSE 'LOCAL_ONLY' 
                    END
                """)
                
                // Set createdAt to current time for existing records
                db.execSQL("""
                    UPDATE locations 
                    SET createdAt = ${System.currentTimeMillis()}
                    WHERE createdAt = 0
                """)
                
                // Keep isSynced column for backward compatibility
                // Will be removed in a future migration
            }
        }
        
        /**
         * Migration from version 2 to 3: Add sync state machine fields to reports and expenses.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sync tracking columns to reports
                db.execSQL("""
                    ALTER TABLE reports 
                    ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                """)
                db.execSQL("""
                    ALTER TABLE reports 
                    ADD COLUMN syncAttempts INTEGER NOT NULL DEFAULT 0
                """)
                db.execSQL("""
                    ALTER TABLE reports 
                    ADD COLUMN lastSyncAttempt INTEGER NOT NULL DEFAULT 0
                """)
                db.execSQL("""
                    ALTER TABLE reports 
                    ADD COLUMN syncError TEXT
                """)
                
                // Migrate existing report data
                db.execSQL("""
                    UPDATE reports 
                    SET syncState = CASE 
                        WHEN isSynced = 1 THEN 'SYNCED' 
                        ELSE 'LOCAL_ONLY' 
                    END
                """)
                
                // Add sync tracking columns to expenses
                db.execSQL("""
                    ALTER TABLE expenses 
                    ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                """)
                db.execSQL("""
                    ALTER TABLE expenses 
                    ADD COLUMN syncAttempts INTEGER NOT NULL DEFAULT 0
                """)
                db.execSQL("""
                    ALTER TABLE expenses 
                    ADD COLUMN lastSyncAttempt INTEGER NOT NULL DEFAULT 0
                """)
                db.execSQL("""
                    ALTER TABLE expenses 
                    ADD COLUMN syncError TEXT
                """)
                
                // Migrate existing expense data
                db.execSQL("""
                    UPDATE expenses 
                    SET syncState = CASE 
                        WHEN isSynced = 1 THEN 'SYNCED' 
                        ELSE 'LOCAL_ONLY' 
                    END
                """)
            }
        }
        
        /**
         * Migration from version 3 to 4: Add database indexes for performance optimization.
         * Indexes are created on frequently queried columns to speed up sync operations.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create indexes for locations table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_userId ON locations(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_timestamp ON locations(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_syncState ON locations(syncState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_userId_syncState ON locations(userId, syncState)")
                
                // Create indexes for reports table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reports_userId ON reports(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reports_syncState ON reports(syncState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reports_date ON reports(date)")
                
                // Create indexes for expenses table
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_userId ON expenses(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_syncState ON expenses(syncState)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_status ON expenses(status)")
            }
        }
        
        /**
         * Migration from version 4 to 5: Add session_id column to locations table.
         * 
         * v1.4 Phase 2 - Session-Based Location Tracking
         * 
         * SAFETY GUARANTEES:
         * 1. Column is nullable (TEXT) - no NOT NULL constraint
         * 2. Existing rows automatically get NULL value (SQLite default)
         * 3. No data deletion or modification
         * 4. Index created AFTER column exists (prevents constraint violation)
         * 5. IF NOT EXISTS prevents crash on re-run (idempotent)
         * 6. Forward-only migration (no rollback needed)
         * 
         * PRODUCTION CONSIDERATIONS:
         * - Users upgrading from v1.3 with existing location data: SAFE
         * - Fresh installs: Column created with schema, no migration runs
         * - Offline mode: Migration runs on next app start, no network required
         * - Corrupt data: NULL is valid, no validation enforced
         * - App crash during migration: Room retries on next start
         * 
         * WHAT THIS DOES NOT DO (by design):
         * - Does NOT backfill session IDs for existing rows
         * - Does NOT enforce non-null constraint
         * - Does NOT modify LocationTrackingService
         * - Does NOT change sync behavior
         * - Does NOT add foreign key constraints
         * 
         * BACKEND COMPATIBILITY:
         * - Backend already supports nullable session_id column
         * - Existing rows with NULL session_id are valid
         * - No API changes required
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // STEP 1: Add nullable session_id column
                // SQLite automatically sets NULL for existing rows
                // No DEFAULT clause needed - NULL is the implicit default
                db.execSQL("""
                    ALTER TABLE locations 
                    ADD COLUMN sessionId TEXT
                """)
                
                // STEP 2: Create index on sessionId for future query performance
                // Index is created AFTER column exists to prevent errors
                // IF NOT EXISTS makes this idempotent (safe to re-run)
                // Partial index would be more efficient, but requires API 30+
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_locations_sessionId 
                    ON locations(sessionId)
                """)
                
                // VERIFICATION (optional, for debugging only):
                // Query to verify column exists and has correct type
                // This does NOT run in production - only useful for manual testing
                // db.execSQL("SELECT sessionId FROM locations LIMIT 1")
            }
        }
    }
}