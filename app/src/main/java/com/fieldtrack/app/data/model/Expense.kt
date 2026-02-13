package com.fieldtrack.app.data.model

data class Expense(
    val id: String,
    val userId: String,
    val amount: Double,
    val category: String,
    val billImageUrl: String,
    val status: String,
    val timestamp: String
) {
    fun matchesSearch(query: String, employeeName: String): Boolean {
        if (query.isBlank()) return true
        val lowerQuery = query.lowercase()
        return employeeName.lowercase().contains(lowerQuery) ||
                category.lowercase().contains(lowerQuery) ||
                amount.toString().contains(query) ||
                status.lowercase().contains(lowerQuery)
    }
}
