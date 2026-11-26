package com.fieldtrack.app.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ─── AppDatabaseMigrations ────────────────────────────────────────────────────
// Production-safe schema migrations.
// Each migration APPENDS to the schema — no destructive operations.
//
// v1: location_queue only
// v2: + pending_expenses table
// ─────────────────────────────────────────────────────────────────────────────

object AppDatabaseMigrations {

    /**
     * v1 → v2: add pending_expenses table for crash-safe expense submission.
     * location_queue table is UNCHANGED — all existing data preserved.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_expenses` (
                    `id`          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `amount`      REAL    NOT NULL,
                    `description` TEXT    NOT NULL,
                    `receipt_url` TEXT,
                    `created_at`  TEXT    NOT NULL,
                    `retries`     INTEGER NOT NULL DEFAULT 0,
                    `confirmed`   INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE location_queue ADD COLUMN owner TEXT")
            db.execSQL("CREATE INDEX index_location_queue_owner_synced_id ON location_queue (owner, synced, id)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE location_queue ADD COLUMN failure TEXT")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pending_expenses ADD COLUMN owner TEXT")
            db.execSQL("ALTER TABLE pending_expenses ADD COLUMN idempotency_key TEXT")
            db.execSQL("ALTER TABLE pending_expenses ADD COLUMN failure TEXT")
            db.execSQL("CREATE INDEX index_pending_expenses_owner_confirmed_id ON pending_expenses (owner, confirmed, id)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
