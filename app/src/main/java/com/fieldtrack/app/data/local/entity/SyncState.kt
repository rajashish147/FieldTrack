package com.fieldtrack.app.data.local.entity

/**
 * Explicit sync states for location tracking.
 * Replaces binary isSynced flag with deterministic state machine.
 */
enum class SyncState {
    LOCAL_ONLY,    // Never attempted sync
    SYNCING,       // Sync in progress (prevents concurrent syncs)
    SYNCED,        // Successfully synced to server
    FAILED,        // Sync failed, will retry
    ABANDONED      // Too many failures, manual intervention needed
}
