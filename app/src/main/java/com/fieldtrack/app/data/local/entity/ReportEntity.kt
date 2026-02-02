package com.fieldtrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fieldtrack.app.data.model.Report
import java.util.UUID

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val date: String,
    val summary: String,
    val optionalImageUrl: String?,
    val isSynced: Boolean = false
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
                isSynced = synced
            )
    }
}
