package com.fieldtrack.app.util

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

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

/** @see TimeUtils.nowUtc */
fun currentIso8601(): String = TimeUtils.nowUtc()

/** @see TimeUtils.todayDateUtc */
fun currentDateString(): String = TimeUtils.todayDateUtc()

/** @see TimeUtils.formatForDisplay */
fun String.toReadableDateTime(): String = TimeUtils.formatForDisplay(this)

/** @see TimeUtils.formatDateOnly */
fun String.toReadableDate(): String = TimeUtils.formatDateOnly(this)

fun Double.formatCurrency(): String = String.format(Locale.US, "₹%.2f", this)

