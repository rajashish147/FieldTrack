package com.fieldtrack.app.data.remote.dto

import com.fieldtrack.app.data.model.Expense
import com.google.gson.annotations.SerializedName

data class ExpenseDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("category")
    val category: String,
    @SerializedName("bill_image_url")
    val billImageUrl: String? = "",
    @SerializedName("status")
    val status: String,
    @SerializedName("timestamp")
    val timestamp: String,
    /** Relational expansion — populated by GET select, excluded from POST via Map body */
    @SerializedName("user")
    val user: UserRelationDto? = null
) {
    fun toModel(): Expense = Expense(
        id = id,
        userId = userId,
        amount = amount,
        category = category,
        billImageUrl = billImageUrl ?: "",
        status = status,
        timestamp = timestamp,
        employeeName = user?.name ?: userId
    )

    companion object {
        fun fromModel(model: Expense): ExpenseDto = ExpenseDto(
            id = model.id,
            userId = model.userId,
            amount = model.amount,
            category = model.category,
            billImageUrl = model.billImageUrl,
            status = model.status,
            timestamp = model.timestamp
        )
    }
}
