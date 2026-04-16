package com.fieldtrack.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fieldtrack.app.data.model.Expense
import java.util.UUID

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["syncState"]),
        Index(value = ["status"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val amount: Double,
    val category: String,
    val billImageUrl: String,
    val status: String,
    val timestamp: String,
    val isSynced: Boolean = false,
    
    // Sync state tracking
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    val syncAttempts: Int = 0,
    val lastSyncAttempt: Long = 0L,
    val syncError: String? = null
) {
    fun toModel(): Expense = Expense(
        id = id,
        userId = userId,
        amount = amount,
        category = category,
        billImageUrl = billImageUrl,
        status = status,
        timestamp = timestamp,
        employeeName = ""
    )

    companion object {
        fun fromModel(model: Expense, synced: Boolean = false): ExpenseEntity =
            ExpenseEntity(
                id = model.id,
                userId = model.userId,
                amount = model.amount,
                category = model.category,
                billImageUrl = model.billImageUrl,
                status = model.status,
                timestamp = model.timestamp,
                isSynced = synced,
                syncState = if (synced) SyncState.SYNCED else SyncState.LOCAL_ONLY
            )
    }
}
