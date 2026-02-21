package com.fieldtrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fieldtrack.app.data.local.entity.ReportEntity
import com.fieldtrack.app.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity)

    @Update
    suspend fun update(report: ReportEntity)

    @Query("SELECT * FROM reports WHERE userId = :userId ORDER BY date DESC")
    fun getReportsByUser(userId: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getReportById(id: String): ReportEntity?

    @Query("SELECT * FROM reports WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedReports(userId: String): List<ReportEntity>

    @Query("UPDATE reports SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Query("SELECT * FROM reports WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun getReportByUserAndDate(userId: String, date: String): ReportEntity?

    @Query("DELETE FROM reports WHERE userId = :userId")
    suspend fun deleteAll(userId: String)
    
    // Hardened sync methods
    @Query("""
        SELECT * FROM reports 
        WHERE userId = :userId 
        AND syncState IN ('LOCAL_ONLY', 'FAILED')
        AND syncAttempts < :maxAttempts
        LIMIT :batchSize
    """)
    suspend fun getSyncableReports(userId: String, maxAttempts: Int, batchSize: Int): List<ReportEntity>
    
    @Query("UPDATE reports SET syncState = :state WHERE id IN (:ids)")
    suspend fun updateSyncState(ids: List<String>, state: SyncState)
    
    @Query("""
        UPDATE reports 
        SET syncState = 'SYNCED', isSynced = 1
        WHERE id IN (:ids)
    """)
    suspend fun markAsSyncedNew(ids: List<String>)
    
    @Query("""
        UPDATE reports 
        SET syncState = :state, 
            syncAttempts = :attempts,
            syncError = :errorMessage,
            lastSyncAttempt = :attemptedAt
        WHERE id = :id
    """)
    suspend fun updateSyncFailure(
        id: String,
        state: SyncState,
        attempts: Int,
        errorMessage: String,
        attemptedAt: Long
    )
    
    @Query("""
        UPDATE reports 
        SET syncState = 'FAILED'
        WHERE syncState = 'SYNCING' 
        AND lastSyncAttempt < :timeoutThreshold
    """)
    suspend fun resetStaleSyncingState(timeoutThreshold: Long)
    
    @Query("SELECT * FROM reports WHERE userId = :userId AND syncState = 'ABANDONED'")
    suspend fun getAbandonedReports(userId: String): List<ReportEntity>
    
    @Query("""
        UPDATE reports 
        SET syncState = 'LOCAL_ONLY', syncAttempts = 0, syncError = null
        WHERE id IN (:reportIds)
    """)
    suspend fun retryAbandonedReports(reportIds: List<String>)
}
