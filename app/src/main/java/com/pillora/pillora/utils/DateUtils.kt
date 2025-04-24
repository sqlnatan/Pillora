package com.pillora.pillora.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Date.isFutureDate(): Boolean {
    val now = Calendar.getInstance().time
    return this.after(now)
}

fun String.toDate(format: String = "dd/MM/yyyy"): Date? {
    val dateFormat = SimpleDateFormat(format, Locale.getDefault())
    return try {
        dateFormat.parse(this)
    } catch (e: Exception) {
        null
    }
}