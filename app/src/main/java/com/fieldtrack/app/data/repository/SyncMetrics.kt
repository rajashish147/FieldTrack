package com.fieldtrack.app.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks sync metrics for monitoring and debugging.
 * 
 * Metrics are persisted to SharedPreferences and can be displayed in UI.
 */
class SyncMetrics(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "sync_metrics"
        
        // Location metrics
        private const val KEY_LOCATION_LAST_SYNC = "location_last_sync"
        private const val KEY_LOCATION_TOTAL_SYNCED = "location_total_synced"
        private const val KEY_LOCATION_TOTAL_FAILED = "location_total_failed"
        private const val KEY_LOCATION_TOTAL_ABANDONED = "location_total_abandoned"
        private const val KEY_LOCATION_LAST_RESULT = "location_last_result"
        private const val KEY_LOCATION_LAST_DURATION = "location_last_duration"
        
        // Report metrics
        private const val KEY_REPORT_LAST_SYNC = "report_last_sync"
        private const val KEY_REPORT_TOTAL_SYNCED = "report_total_synced"
        private const val KEY_REPORT_TOTAL_FAILED = "report_total_failed"
        private const val KEY_REPORT_TOTAL_ABANDONED = "report_total_abandoned"
        private const val KEY_REPORT_LAST_RESULT = "report_last_result"
        private const val KEY_REPORT_LAST_DURATION = "report_last_duration"
        
        // Expense metrics
        private const val KEY_EXPENSE_LAST_SYNC = "expense_last_sync"
        private const val KEY_EXPENSE_TOTAL_SYNCED = "expense_total_synced"
        private const val KEY_EXPENSE_TOTAL_FAILED = "expense_total_failed"
        private const val KEY_EXPENSE_TOTAL_ABANDONED = "expense_total_abandoned"
        private const val KEY_EXPENSE_LAST_RESULT = "expense_last_result"
        private const val KEY_EXPENSE_LAST_DURATION = "expense_last_duration"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Entity type for metrics tracking.
     */
    enum class EntityType {
        LOCATION, REPORT, EXPENSE
    }
    
    /**
     * Record a sync result for an entity type.
     * 
     * @param entityType Type of entity synced
     * @param result Sync result
     * @param durationMs Sync duration in milliseconds
     */
    fun recordSync(entityType: EntityType, result: SyncResult, durationMs: Long) {
        val editor = prefs.edit()
        val timestamp = System.currentTimeMillis()
        
        when (entityType) {
            EntityType.LOCATION -> {
                editor.putLong(KEY_LOCATION_LAST_SYNC, timestamp)
                editor.putLong(KEY_LOCATION_LAST_DURATION, durationMs)
                editor.putString(KEY_LOCATION_LAST_RESULT, result.getSummary())
                
                when (result) {
                    is SyncResult.Success -> {
                        editor.putInt(KEY_LOCATION_TOTAL_SYNCED, 
                            prefs.getInt(KEY_LOCATION_TOTAL_SYNCED, 0) + result.synced)
                    }
                    is SyncResult.Partial -> {
                        editor.putInt(KEY_LOCATION_TOTAL_SYNCED, 
                            prefs.getInt(KEY_LOCATION_TOTAL_SYNCED, 0) + result.synced)
                        editor.putInt(KEY_LOCATION_TOTAL_FAILED, 
                            prefs.getInt(KEY_LOCATION_TOTAL_FAILED, 0) + result.failed)
                        editor.putInt(KEY_LOCATION_TOTAL_ABANDONED, 
                            prefs.getInt(KEY_LOCATION_TOTAL_ABANDONED, 0) + result.abandoned)
                    }
                    is SyncResult.Failed -> {
                        editor.putInt(KEY_LOCATION_TOTAL_FAILED, 
                            prefs.getInt(KEY_LOCATION_TOTAL_FAILED, 0) + result.failed)
                    }
                }
            }
            EntityType.REPORT -> {
                editor.putLong(KEY_REPORT_LAST_SYNC, timestamp)
                editor.putLong(KEY_REPORT_LAST_DURATION, durationMs)
                editor.putString(KEY_REPORT_LAST_RESULT, result.getSummary())
                
                when (result) {
                    is SyncResult.Success -> {
                        editor.putInt(KEY_REPORT_TOTAL_SYNCED, 
                            prefs.getInt(KEY_REPORT_TOTAL_SYNCED, 0) + result.synced)
                    }
                    is SyncResult.Partial -> {
                        editor.putInt(KEY_REPORT_TOTAL_SYNCED, 
                            prefs.getInt(KEY_REPORT_TOTAL_SYNCED, 0) + result.synced)
                        editor.putInt(KEY_REPORT_TOTAL_FAILED, 
                            prefs.getInt(KEY_REPORT_TOTAL_FAILED, 0) + result.failed)
                        editor.putInt(KEY_REPORT_TOTAL_ABANDONED, 
                            prefs.getInt(KEY_REPORT_TOTAL_ABANDONED, 0) + result.abandoned)
                    }
                    is SyncResult.Failed -> {
                        editor.putInt(KEY_REPORT_TOTAL_FAILED, 
                            prefs.getInt(KEY_REPORT_TOTAL_FAILED, 0) + result.failed)
                    }
                }
            }
            EntityType.EXPENSE -> {
                editor.putLong(KEY_EXPENSE_LAST_SYNC, timestamp)
                editor.putLong(KEY_EXPENSE_LAST_DURATION, durationMs)
                editor.putString(KEY_EXPENSE_LAST_RESULT, result.getSummary())
                
                when (result) {
                    is SyncResult.Success -> {
                        editor.putInt(KEY_EXPENSE_TOTAL_SYNCED, 
                            prefs.getInt(KEY_EXPENSE_TOTAL_SYNCED, 0) + result.synced)
                    }
                    is SyncResult.Partial -> {
                        editor.putInt(KEY_EXPENSE_TOTAL_SYNCED, 
                            prefs.getInt(KEY_EXPENSE_TOTAL_SYNCED, 0) + result.synced)
                        editor.putInt(KEY_EXPENSE_TOTAL_FAILED, 
                            prefs.getInt(KEY_EXPENSE_TOTAL_FAILED, 0) + result.failed)
                        editor.putInt(KEY_EXPENSE_TOTAL_ABANDONED, 
                            prefs.getInt(KEY_EXPENSE_TOTAL_ABANDONED, 0) + result.abandoned)
                    }
                    is SyncResult.Failed -> {
                        editor.putInt(KEY_EXPENSE_TOTAL_FAILED, 
                            prefs.getInt(KEY_EXPENSE_TOTAL_FAILED, 0) + result.failed)
                    }
                }
            }
        }
        
        editor.apply()
    }
    
    /**
     * Get metrics for an entity type.
     */
    fun getMetrics(entityType: EntityType): EntityMetrics {
        return when (entityType) {
            EntityType.LOCATION -> EntityMetrics(
                lastSyncTimestamp = prefs.getLong(KEY_LOCATION_LAST_SYNC, 0L),
                totalSynced = prefs.getInt(KEY_LOCATION_TOTAL_SYNCED, 0),
                totalFailed = prefs.getInt(KEY_LOCATION_TOTAL_FAILED, 0),
                totalAbandoned = prefs.getInt(KEY_LOCATION_TOTAL_ABANDONED, 0),
                lastResult = prefs.getString(KEY_LOCATION_LAST_RESULT, null),
                lastDurationMs = prefs.getLong(KEY_LOCATION_LAST_DURATION, 0L)
            )
            EntityType.REPORT -> EntityMetrics(
                lastSyncTimestamp = prefs.getLong(KEY_REPORT_LAST_SYNC, 0L),
                totalSynced = prefs.getInt(KEY_REPORT_TOTAL_SYNCED, 0),
                totalFailed = prefs.getInt(KEY_REPORT_TOTAL_FAILED, 0),
                totalAbandoned = prefs.getInt(KEY_REPORT_TOTAL_ABANDONED, 0),
                lastResult = prefs.getString(KEY_REPORT_LAST_RESULT, null),
                lastDurationMs = prefs.getLong(KEY_REPORT_LAST_DURATION, 0L)
            )
            EntityType.EXPENSE -> EntityMetrics(
                lastSyncTimestamp = prefs.getLong(KEY_EXPENSE_LAST_SYNC, 0L),
                totalSynced = prefs.getInt(KEY_EXPENSE_TOTAL_SYNCED, 0),
                totalFailed = prefs.getInt(KEY_EXPENSE_TOTAL_FAILED, 0),
                totalAbandoned = prefs.getInt(KEY_EXPENSE_TOTAL_ABANDONED, 0),
                lastResult = prefs.getString(KEY_EXPENSE_LAST_RESULT, null),
                lastDurationMs = prefs.getLong(KEY_EXPENSE_LAST_DURATION, 0L)
            )
        }
    }
    
    /**
     * Reset metrics for an entity type.
     */
    fun resetMetrics(entityType: EntityType) {
        val editor = prefs.edit()
        
        when (entityType) {
            EntityType.LOCATION -> {
                editor.remove(KEY_LOCATION_LAST_SYNC)
                editor.remove(KEY_LOCATION_TOTAL_SYNCED)
                editor.remove(KEY_LOCATION_TOTAL_FAILED)
                editor.remove(KEY_LOCATION_TOTAL_ABANDONED)
                editor.remove(KEY_LOCATION_LAST_RESULT)
                editor.remove(KEY_LOCATION_LAST_DURATION)
            }
            EntityType.REPORT -> {
                editor.remove(KEY_REPORT_LAST_SYNC)
                editor.remove(KEY_REPORT_TOTAL_SYNCED)
                editor.remove(KEY_REPORT_TOTAL_FAILED)
                editor.remove(KEY_REPORT_TOTAL_ABANDONED)
                editor.remove(KEY_REPORT_LAST_RESULT)
                editor.remove(KEY_REPORT_LAST_DURATION)
            }
            EntityType.EXPENSE -> {
                editor.remove(KEY_EXPENSE_LAST_SYNC)
                editor.remove(KEY_EXPENSE_TOTAL_SYNCED)
                editor.remove(KEY_EXPENSE_TOTAL_FAILED)
                editor.remove(KEY_EXPENSE_TOTAL_ABANDONED)
                editor.remove(KEY_EXPENSE_LAST_RESULT)
                editor.remove(KEY_EXPENSE_LAST_DURATION)
            }
        }
        
        editor.apply()
    }
    
    /**
     * Reset all metrics.
     */
    fun resetAllMetrics() {
        prefs.edit().clear().apply()
    }
}

/**
 * Metrics for a specific entity type.
 */
data class EntityMetrics(
    val lastSyncTimestamp: Long,
    val totalSynced: Int,
    val totalFailed: Int,
    val totalAbandoned: Int,
    val lastResult: String?,
    val lastDurationMs: Long
) {
    val hasNeverSynced: Boolean get() = lastSyncTimestamp == 0L
    val successRate: Float get() {
        val total = totalSynced + totalFailed + totalAbandoned
        return if (total > 0) totalSynced.toFloat() / total else 0f
    }
}
