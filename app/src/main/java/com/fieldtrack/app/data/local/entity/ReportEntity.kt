package com.fieldtrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fieldtrack.app.data.model.Report
import java.util.UUID

@Entity(
    tableName = "reports",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["syncState"]),
        Index(value = ["date"])
    ]
)
data class ReportEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val date: String,
    val summary: String,
    val optionalImageUrl: String?,
    val isSynced: Boolean = false,
    
    // Sync state tracking
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long = 0L,
    val syncError: String? = null
) {
    fun toModel(): Report = Report(
        id = id,
        userId = userId,
        date = date,
        summary = summary,
        optionalImageUrl = optionalImageUrl
    )

    companion object {
        fun fromModel(model: Report, synced: Boolean = false): ReportEntity =
            ReportEntity(
                id = model.id,
                userId = model.userId,
                date = model.date,
                summary = model.summary,
                optionalImageUrl = model.optionalImageUrl,
                isSynced = synced,
                syncState = if (synced) SyncState.SYNCED else SyncState.LOCAL_ONLY
            )
    }
}
