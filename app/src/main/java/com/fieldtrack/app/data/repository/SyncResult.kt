package com.fieldtrack.app.data.repository

/**
 * Result of a sync operation.
 * Provides visibility into sync outcomes for monitoring and debugging.
 */
data class SyncResult(
    val synced: Int,      // Number of locations successfully synced
    val failed: Int,      // Number of locations that failed (will retry)
    val abandoned: Int    // Number of locations abandoned (too many failures)
)
