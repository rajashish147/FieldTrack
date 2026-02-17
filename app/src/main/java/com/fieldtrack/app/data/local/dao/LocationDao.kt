package com.fieldtrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fieldtrack.app.data.local.entity.LocationEntity
import com.fieldtrack.app.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<LocationEntity>)

    // ========== NEW SYNC STATE QUERIES ==========
    
    /**
     * Get locations ready for sync.
     * Excludes SYNCING (prevents race conditions) and ABANDONED (too many failures).
     * Orders by timestamp to sync oldest first.
     */
    @Query("""
        SELECT * FROM locations 
        WHERE userId = :userId 
        AND syncState IN ('LOCAL_ONLY', 'FAILED')
        AND syncAttempts < :maxAttempts
        ORDER BY timestamp ASC
        LIMIT :batchSize
    """)
    suspend fun getSyncableLocations(
        userId: String,
        maxAttempts: Int = 5,
        batchSize: Int = 100
    ): List<LocationEntity>

    /**
     * Atomic state update (prevents race conditions).
     */
    @Query("UPDATE locations SET syncState = :state WHERE id IN (:ids)")
    suspend fun updateSyncState(ids: List<String>, state: SyncState)

    /**
     * Mark locations as successfully synced.
     */
    @Transaction
    suspend fun markAsSyncedNew(ids: List<String>) {
        val now = System.currentTimeMillis()
        ids.forEach { id ->
            updateSyncSuccess(id, now)
        }
    }

    @Query("""
        UPDATE locations 
        SET syncState = 'SYNCED',
            lastSyncAttemptAt = :attemptedAt
        WHERE id = :id
    """)
    suspend fun updateSyncSuccess(id: String, attemptedAt: Long)

    /**
     * Record sync failure with error details.
     */
    @Query("""
        UPDATE locations 
        SET syncState = :state,
            syncAttempts = :attempts,
            syncErrorMessage = :errorMessage,
            lastSyncAttemptAt = :attemptedAt
        WHERE id = :id
    """)
    suspend fun updateSyncFailure(
        id: String,
        state: SyncState,
        attempts: Int,
        errorMessage: String,
        attemptedAt: Long
    )

    /**
     * Reset stale SYNCING states (crash recovery).
     * If a location has been in SYNCING state for too long, reset it to LOCAL_ONLY.
     */
    @Query("""
        UPDATE locations 
        SET syncState = 'LOCAL_ONLY'
        WHERE syncState = 'SYNCING' 
        AND lastSyncAttemptAt < :timeoutThreshold
    """)
    suspend fun resetStaleSyncingState(timeoutThreshold: Long)

    /**
     * Get count of unsynced locations (for UI badge).
     * Matches getSyncableLocations criteria - only counts locations that will actually be synced.
     */
    @Query("""
        SELECT COUNT(*) FROM locations 
        WHERE userId = :userId 
        AND syncState IN ('LOCAL_ONLY', 'FAILED')
        AND syncAttempts < 5
    """)
    suspend fun getUnsyncedCount(userId: String): Int

    /**
     * Get abandoned locations (for debugging/admin).
     */
    @Query("""
        SELECT * FROM locations 
        WHERE userId = :userId 
        AND syncState = 'ABANDONED'
        ORDER BY timestamp DESC
    """)
    suspend fun getAbandonedLocations(userId: String): List<LocationEntity>

    /**
     * Retry abandoned locations (manual intervention).
     */
    @Query("""
        UPDATE locations 
        SET syncState = 'LOCAL_ONLY', 
            syncAttempts = 0,
            syncErrorMessage = NULL
        WHERE id IN (:ids)
    """)
    suspend fun retryAbandonedLocations(ids: List<String>)

    // ========== LEGACY QUERIES (kept for compatibility) ==========

    @Query("SELECT * FROM locations WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLocationsByUser(userId: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE userId = :userId AND date(timestamp) = :date ORDER BY timestamp ASC")
    fun getLocationsByUserAndDate(userId: String, date: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE userId = :userId AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getLocationsSince(userId: String, sinceTimestamp: String): Flow<List<LocationEntity>>

    /**
     * Legacy query - now uses syncState instead of isSynced.
     * Kept for backward compatibility during migration.
     */
    @Query("SELECT * FROM locations WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedLocations(userId: String): List<LocationEntity>

    @Query("DELETE FROM locations WHERE timestamp < :beforeTimestamp AND syncState = 'SYNCED' AND userId = :userId")
    suspend fun deleteOldSyncedLocations(beforeTimestamp: String, userId: String)

    @Query("DELETE FROM locations WHERE userId = :userId")
    suspend fun deleteAll(userId: String)
    
    // ========== PHASE 2 BETA: SESSION ROUTE QUERIES (READ-ONLY) ==========
    
    /**
     * Phase 2 Beta: Get locations for a specific session, ordered by timestamp.
     * 
     * READ-ONLY query for route visualization.
     * Returns empty list if sessionId is null (graceful degradation).
     * 
     * Performance: Uses sessionId index (created in Phase 2 migration).
     * Expected: <10ms for typical session (50-200 locations).
     * 
     * Ordering: Uses createdAt (numeric) instead of timestamp (string) for guaranteed
     * monotonic ordering. createdAt is set to System.currentTimeMillis() when location
     * is captured, ensuring correct chronological order.
     * 
     * @param sessionId The attendance session ID
     * @param userId User ID for RLS compliance
     * @return Ordered list of locations for the session
     */
    @Query("""
        SELECT * FROM locations 
        WHERE sessionId = :sessionId 
        AND userId = :userId
        ORDER BY createdAt ASC
    """)
    suspend fun getLocationsBySessionId(
        sessionId: String,
        userId: String
    ): List<LocationEntity>
}
