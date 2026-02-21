package com.fieldtrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fieldtrack.app.data.local.entity.ExpenseEntity
import com.fieldtrack.app.data.local.entity.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY timestamp DESC")
    fun getExpensesByUser(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE isSynced = 0 AND userId = :userId")
    suspend fun getUnsyncedExpenses(userId: String): List<ExpenseEntity>

    @Query("UPDATE expenses SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Query("SELECT * FROM expenses WHERE userId = :userId AND status = :status ORDER BY timestamp DESC")
    fun getExpensesByStatus(userId: String, status: String): Flow<List<ExpenseEntity>>

    @Query("DELETE FROM expenses WHERE userId = :userId")
    suspend fun deleteAll(userId: String)
    
    // Hardened sync methods
    @Query("""
        SELECT * FROM expenses 
        WHERE userId = :userId 
        AND syncState IN ('LOCAL_ONLY', 'FAILED')
        AND syncAttempts < :maxAttempts
        LIMIT :batchSize
    """)
    suspend fun getSyncableExpenses(userId: String, maxAttempts: Int, batchSize: Int): List<ExpenseEntity>
    
    @Query("UPDATE expenses SET syncState = :state WHERE id IN (:ids)")
    suspend fun updateSyncState(ids: List<String>, state: SyncState)
    
    @Query("""
        UPDATE expenses 
        SET syncState = 'SYNCED', isSynced = 1
        WHERE id IN (:ids)
    """)
    suspend fun markAsSyncedNew(ids: List<String>)
    
    @Query("""
        UPDATE expenses 
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
        UPDATE expenses 
        SET syncState = 'FAILED'
        WHERE syncState = 'SYNCING' 
        AND lastSyncAttempt < :timeoutThreshold
    """)
    suspend fun resetStaleSyncingState(timeoutThreshold: Long)
    
    @Query("SELECT * FROM expenses WHERE userId = :userId AND syncState = 'ABANDONED'")
    suspend fun getAbandonedExpenses(userId: String): List<ExpenseEntity>
    
    @Query("""
        UPDATE expenses 
        SET syncState = 'LOCAL_ONLY', syncAttempts = 0, syncError = null
        WHERE id IN (:expenseIds)
    """)
    suspend fun retryAbandonedExpenses(expenseIds: List<String>)
}
