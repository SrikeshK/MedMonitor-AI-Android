package com.medmonitor.data.model

data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "REMINDER", // "REMINDER", "MISSED_DOSE", "REFILL_ALERT", "FAMILY_ALERT"
    val isRead: Boolean = false
)
