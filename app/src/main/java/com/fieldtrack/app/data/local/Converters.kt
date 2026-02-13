package com.fieldtrack.app.data.local

import androidx.room.TypeConverter
import com.fieldtrack.app.data.local.entity.SyncState

/**
 * Room TypeConverters for custom types.
 */
class Converters {
    
    @TypeConverter
    fun fromSyncState(value: SyncState): String {
        return value.name
    }
    
    @TypeConverter
    fun toSyncState(value: String): SyncState {
        return try {
            SyncState.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Fallback for unknown states (future-proofing)
            SyncState.LOCAL_ONLY
        }
    }
}
