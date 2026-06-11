package com.medmonitor.util

import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import java.util.*

object StreakCalculator {
    /**
     * ✅ SAFE PATIENT STREAK ENGINE FIX
     * Strict consecutive calendar day logic.
     * 
     * RULES:
     * 1. VALID DAY: taken > 0 AND missed == 0 -> streak++
     * 2. INVALID DAY: missed > 0 -> break immediately
     * 3. EMPTY PAST DAY: break immediately
     * 4. TODAY SAFETY: If no logs yet today, do not break (preserve previous streak)
     * 5. SUPPLY NEUTRAL: out_of_stock days (without other logs) do not break or increment.
     */
    fun calculateStreak(logs: List<DoseLog>): Int {
        var streak = 0
        for (i in 0..30) {
            val dayStart = getStartOfDayMinus(i)
            val dayEnd = getEndOfDayMinus(i)

            val dayLogs = logs.filter {
                val logTime = try { it.timestamp.toDate().time } catch (e: Exception) { 0L }
                logTime in dayStart..dayEnd
            }

            val taken = dayLogs.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED }
            val missed = dayLogs.count { it.status == DoseStatus.MISSED }
            val outOfStock = dayLogs.count { it.status == DoseStatus.OUT_OF_STOCK }

            if (taken > 0 && missed == 0) {
                // Perfect adherence day: increment streak
                streak++
            } else if (missed > 0) {
                // Any missed dose breaks the streak immediately
                break
            } else if (i == 0 && dayLogs.isEmpty()) {
                // Today has no logs yet: safe preservation of previous streak
                continue
            } else if (outOfStock > 0 && taken == 0) {
                // Supply issues: neutral (bridge). Only if no misses.
                continue
            } else {
                // Past empty day: break streak (strict consecutive rule)
                break
            }
        }
        return streak
    }

    private fun getStartOfDayMinus(days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDayMinus(days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
