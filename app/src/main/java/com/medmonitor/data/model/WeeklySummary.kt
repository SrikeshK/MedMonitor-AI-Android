package com.medmonitor.data.model

data class WeeklySummary(
    val totalTaken: Int = 0,
    val totalMissed: Int = 0,
    val totalDelayed: Int = 0,
    val totalOutOfStock: Int = 0,
    val adherencePercent: Int = 0,
    val logs: List<DoseLog> = emptyList()
)
