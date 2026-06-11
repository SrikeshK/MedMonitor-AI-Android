package com.medmonitor.data.model

data class DailyStats(
    val taken: Int = 0,
    val missed: Int = 0,
    val outOfStock: Int = 0,
    val adherence: Int = 0,
    val avgDelay: Long = 0L
)

data class WeeklyStats(
    val daily: Map<String, DailyStats> = emptyMap()
)

data class AdvancedStats(
    val streak: Int = 0,
    val avgDelayMinutes: Int = 0,
    val mostMissedSlot: String = ""
)
