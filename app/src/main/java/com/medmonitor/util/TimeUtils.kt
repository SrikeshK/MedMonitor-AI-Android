package com.medmonitor.util

import android.content.Context
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun getRelativeTimeSpanString(time: Long, context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - time

    if (time > now) {
        val minutesUntil = (time - now) / 60000
        return when {
            minutesUntil <= 0 -> "Just now" // Should ideally not happen if time > now
            minutesUntil < 60 -> "In $minutesUntil min"
            minutesUntil < (24 * 60) -> { // Within 24 hours
                val hoursUntil = minutesUntil / 60
                "In $hoursUntil hour${if(hoursUntil > 1) "s" else ""}"
            }
            else -> {
                val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                sdf.format(Date(time))
            }
        }
    }

    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
            sdf.format(Date(time))
        }
    }
}

fun getTimeUntilDue(millisecondsUntilDue: Long, context: Context): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millisecondsUntilDue)
    val hours = TimeUnit.MILLISECONDS.toHours(millisecondsUntilDue)

    return when {
        minutes <= 0 -> "Now"
        minutes < 60 -> "Due in $minutes min"
        hours < 24 -> "Due in $hours hour${if(hours > 1) "s" else ""}"
        else -> "Upcoming" // For anything beyond 24 hours
    }
}
