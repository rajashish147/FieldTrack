package com.fieldtrack.app.core.data.local

import androidx.room.*

// ══════════════════════════════════════════════════════════════════════════════
// ENTITIES
// ══════════════════════════════════════════════════════════════════════════════

// ─── LocationQueueEntity ──────────────────────────────────────────────────────
// Offline queue for GPS points pending upload to /locations/batch.
@Entity(tableName = "location_queue", indices = [Index(value = ["owner", "synced", "id"])])
data class LocationQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")  val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,    // ISO-8601 UTC string
    val synced: Boolean = false,
    val owner: String? = null,
    val failure: String? = null,
)

// ─── PendingExpenseEntity ─────────────────────────────────────────────────────
// Tracks expenses that were submitted but may not have received a confirmed
// server response (crash / network drop mid-submit). Allows retry on next launch.

@Entity(tableName = "pending_expenses", indices = [Index(value = ["owner", "confirmed", "id"])])
data class PendingExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val description: String,
    @ColumnInfo(name = "receipt_url")  val receiptUrl: String?,
    @ColumnInfo(name = "created_at")   val createdAt: String,    // ISO-8601 UTC
    val retries: Int = 0,
    val confirmed: Boolean = false,     // true once server acknowledged
    val owner: String? = null,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String? = null,
    val failure: String? = null,
)

// ══════════════════════════════════════════════════════════════════════════════
// DAOs
// ══════════════════════════════════════════════════════════════════════════════

@Dao
interface LocationQueueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: LocationQueueEntity)

    @Query("SELECT * FROM location_queue WHERE owner = :owner AND synced = 0 AND failure IS NULL ORDER BY id ASC LIMIT :limit")
    suspend fun getUnsynced(owner: String, limit: Int): List<LocationQueueEntity>

    @Query("DELETE FROM location_queue WHERE owner = :owner AND id IN (:ids)")
    suspend fun deleteAcknowledged(owner: String, ids: List<Long>)

    @Query("UPDATE location_queue SET failure = :failure WHERE owner = :owner AND id IN (:ids)")
    suspend fun markRejected(owner: String, ids: List<Long>, failure: String)

    @Query("SELECT COUNT(*) FROM location_queue WHERE owner = :owner AND session_id = :sessionId AND failure IS NULL AND synced = 0")
    fun observePending(owner: String, sessionId: String): kotlinx.coroutines.flow.Flow<Int>

    @Query("DELETE FROM location_queue WHERE synced = 1")
    suspend fun clearSynced()

    // An authenticated active-shift response establishes ownership for legacy rows.
    @Query("UPDATE location_queue SET owner = :owner WHERE owner IS NULL AND session_id = :sessionId")
    suspend fun assignVerifiedSession(owner: String, sessionId: String)

    @Query("DELETE FROM location_queue WHERE julianday(recorded_at) < julianday(:cutoff)")
    suspend fun purgeBefore(cutoff: String)
}

@Dao
interface PendingExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingExpenseEntity): Long

    @Query("SELECT * FROM pending_expenses WHERE owner = :owner AND idempotency_key IS NOT NULL AND confirmed = 0 AND failure IS NULL ORDER BY id ASC LIMIT 50")
    suspend fun getUnconfirmed(owner: String): List<PendingExpenseEntity>

    /** Remove orphan confirmed entries — cleanup */
    @Query("DELETE FROM pending_expenses WHERE confirmed = 1")
    suspend fun clearConfirmed()

    @Query("DELETE FROM pending_expenses WHERE id = :id AND owner = :owner")
    suspend fun delete(id: Long, owner: String)

    @Query("UPDATE pending_expenses SET failure = :reason WHERE id = :id AND owner = :owner")
    suspend fun markRejected(id: Long, owner: String, reason: String)
}

// ══════════════════════════════════════════════════════════════════════════════
// DATABASE
// ══════════════════════════════════════════════════════════════════════════════

@Database(
    entities = [LocationQueueEntity::class, PendingExpenseEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationQueueDao(): LocationQueueDao
    abstract fun pendingExpenseDao(): PendingExpenseDao
}
