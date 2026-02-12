package com.fieldtrack.app.util

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun currentIso8601(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

fun currentDateString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

fun String.toReadableDateTime(): String {
    return try {
        // Parse ISO 8601 timestamp (handles both Z and +00:00 formats)
        val timestamp = if (this.contains("+") || this.endsWith("Z")) {
            java.time.Instant.parse(this)
        } else {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(this) ?: return this
            date.toInstant()
        }
        
        val dateTime = java.time.LocalDateTime.ofInstant(timestamp, java.time.ZoneId.systemDefault())
        val now = java.time.LocalDateTime.now()
        
        val date = dateTime.toLocalDate()
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)
        
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
        val timeStr = dateTime.format(timeFormatter)
        
        when (date) {
            today -> "Today, $timeStr"
            yesterday -> "Yesterday, $timeStr"
            else -> {
                val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
                dateTime.format(dateFormatter)
            }
        }
    } catch (e: Exception) {
        this
    }
}

fun String.toReadableDate(): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = inputFormat.parse(this) ?: return this
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        outputFormat.format(date)
    } catch (e: Exception) {
        this
    }
}

fun Double.formatCurrency(): String = String.format(Locale.US, "₹%.2f", this)
