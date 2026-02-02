package com.fieldtrack.app.data.model

data class Expense(
    val id: String,
    val userId: String,
    val amount: Double,
    val category: String,
    val billImageUrl: String,
    val status: String,
    val timestamp: String
)
