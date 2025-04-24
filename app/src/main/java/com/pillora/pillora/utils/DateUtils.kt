package com.pillora.pillora.utils


import java.util.Calendar
import java.util.Date


fun Date.isFutureDate(): Boolean {
    val now = Calendar.getInstance().time
    return this.after(now)
}
