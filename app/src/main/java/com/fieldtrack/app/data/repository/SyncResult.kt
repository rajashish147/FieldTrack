package com.fieldtrack.app.data.repository

/**
 * Result of a sync operation.
 * 
 * This sealed class enables deterministic sync handling and ensures
 * all sync outcomes are handled exhaustively.
 * 
 * Usage:
 * ```kotlin
 * when (val result = repository.syncUnsynced()) {
 *     is SyncResult.Success -> showSuccess(result.synced)
 *     is SyncResult.Partial -> showPartialSuccess(result.synced, result.failed)
 *     is SyncResult.Failed -> showError(result.reason)
 * }
 * ```
 */
sealed class SyncResult {
    /**
     * All items synced successfully.
     * 
     * @property synced Number of items successfully synced
     * @property timestamp When the sync completed (milliseconds since epoch)
     */
    data class Success(
        val synced: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncResult()
    
    /**
     * Some items synced, some failed.
     * 
     * This is a partial success - some items were synced successfully,
     * but others failed and will be retried on next sync.
     * 
     * @property synced Number of items successfully synced
     * @property failed Number of items that failed (will retry)
     * @property abandoned Number of items abandoned (too many failures)
     * @property errors List of error messages (max 10, truncated if more)
     * @property timestamp When the sync completed (milliseconds since epoch)
     */
    data class Partial(
        val synced: Int,
        val failed: Int,
        val abandoned: Int = 0,
        val errors: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncResult() {
        val total: Int get() = synced + failed + abandoned
        val successRate: Float get() = if (total > 0) synced.toFloat() / total else 0f
    }
    
    /**
     * All items failed to sync.
     * 
     * This is a complete failure - no items were synced successfully.
     * Items will be retried on next sync.
     * 
     * @property reason Primary failure reason
     * @property failed Number of items that failed
     * @property errors List of error messages (max 10, truncated if more)
     * @property timestamp When the sync failed (milliseconds since epoch)
     */
    data class Failed(
        val reason: String,
        val failed: Int,
        val errors: List<String> = emptyList(),
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncResult()
    
    /**
     * Returns true if any items were synced successfully.
     */
    fun hasSuccess(): Boolean = when (this) {
        is Success -> synced > 0
        is Partial -> synced > 0
        is Failed -> false
    }
    
    /**
     * Returns the number of items synced successfully.
     */
    fun getSyncedCount(): Int = when (this) {
        is Success -> synced
        is Partial -> synced
        is Failed -> 0
    }
    
    /**
     * Returns the number of items that failed.
     */
    fun getFailedCount(): Int = when (this) {
        is Success -> 0
        is Partial -> failed + abandoned
        is Failed -> failed
    }
    
    /**
     * Returns a human-readable summary of the sync result.
     */
    fun getSummary(): String = when (this) {
        is Success -> "Synced $synced items successfully"
        is Partial -> "Synced $synced items, $failed failed, $abandoned abandoned"
        is Failed -> "Sync failed: $reason ($failed items)"
    }
}
