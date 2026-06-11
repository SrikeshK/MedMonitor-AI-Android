package com.medmonitor.util

import android.content.Context
import android.util.Log
import com.medmonitor.data.model.Medicine
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

object MedicineStatusUtil {

    // PART 10 — ENGINE TRACE
    fun normalizeSlot(slot: String): String {
        val result = slot.trim().uppercase()
        return result
    }

    fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    /**
     * ✅ SAFE STATUS ENGINE FIX — SLOT-ISOLATED VALIDATION
     * 
     * RESOLVES: (E) Shared timestamp contamination
     * Removes global reliance on isSameDay(lastUpdatedTime, now) which caused 
     * stale "TAKEN" states to resurface for all slots once any single dose was taken.
     * 
     * UPDATED: Added createdAt for First-Day Protection
     */
    fun getSlotStatus(slotTime: String, slotState: String?, now: Long, lastUpdatedTime: Long, createdAt: Long = 0L): String {
        // 1. Parse slot time to today's context
        val slotMillis = parseTimeToTodayMillis(slotTime, now)
        val buffer = 30 * 60 * 1000L

        // 2. Determine if slot belongs to TODAY (Slot-isolated validation)
        // Verify if the TAKEN status was recorded within or after this specific slot's window.
        val isSameDayVal = isSameDay(lastUpdatedTime, slotMillis)
        val isTodaySlot = isSameDayVal && lastUpdatedTime >= (slotMillis - buffer)

        // 3. Handle "TAKEN" state safely
        if (slotState == "TAKEN") {
            if (isTodaySlot) {
                return "COMPLETED"
            }
            // Else: Old TAKEN from previous day or different slot -> treat as PENDING
        }

        // 4. SAFE FIRST-DAY PROTECTION
        // If medicine was created today and the creation time is AFTER this slot's time,
        // we don't mark it MISSED for today.
        if (createdAt > 0L && isSameDay(createdAt, now)) {
            if (createdAt > slotMillis) {
                // For past slots on creation day, we return UPCOMING instead of MISSED
                if (now > (slotMillis + buffer)) {
                    return "UPCOMING"
                }
            }
        }

        // 5. CONTINUE NORMAL FLOW
        return when {
            now < slotMillis -> "UPCOMING"
            now <= (slotMillis + buffer) -> "DUE_NOW"
            else -> "MISSED"
        }
    }

    private fun isWithinDueWindow(slotMillis: Long, now: Long): Boolean {
        val buffer = 30 * 60 * 1000L
        return now >= slotMillis && now <= (slotMillis + buffer)
    }

    fun parseTimeToTodayMillis(timeStr: String, now: Long): Long {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return try {
            val date = sdf.parse(timeStr) ?: return 0L
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val timeCal = Calendar.getInstance().apply { time = date }
            
            calendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    // Deprecated - kept for compatibility if needed elsewhere
    private fun parseTimeOfDayToMillis(timeStr: String): Long {
        return parseTimeToTodayMillis(timeStr, System.currentTimeMillis())
    }

    fun isMultiDose(medicine: Medicine): Boolean {
        return medicine.scheduleTimes.size > 1
    }

    fun shouldResetSlots(medicine: Medicine): Boolean {
        if (medicine.lastUpdatedTime == 0L) return true
        val result = !isSameDay(medicine.lastUpdatedTime, System.currentTimeMillis())
        return result
    }

    fun getTodayScheduledTime(originalTime: Long): Long {
        if (originalTime == 0L) return 0L

        val nowCal = Calendar.getInstance()
        val medCal = Calendar.getInstance().apply { timeInMillis = originalTime }

        val hour = medCal.get(Calendar.HOUR_OF_DAY)
        val minute = medCal.get(Calendar.MINUTE)

        nowCal.set(Calendar.HOUR_OF_DAY, hour)
        nowCal.set(Calendar.MINUTE, minute)
        nowCal.set(Calendar.SECOND, 0)
        nowCal.set(Calendar.MILLISECOND, 0)

        return nowCal.timeInMillis
    }

    fun getMedicineStatus(medicine: Medicine, context: Context): String {
        val now = System.currentTimeMillis()
        
        if (isMultiDose(medicine)) {
            val statuses = medicine.scheduleTimes.map { (slotName, slotTime) ->
                getSlotStatus(slotTime, medicine.slotStatus[normalizeSlot(slotName)], now, medicine.lastUpdatedTime, medicine.createdAt)
            }

            val hasDue = statuses.contains("DUE_NOW")
            val hasMissed = statuses.contains("MISSED")
            val hasCompleted = statuses.contains("COMPLETED")
            val allCompleted = statuses.all { it == "COMPLETED" }

            return when {
                hasDue -> "DUE_NOW"
                hasCompleted && hasMissed -> "PARTIAL"
                allCompleted -> "COMPLETED"
                hasMissed -> "MISSED"
                else -> "UPCOMING"
            }
        }

        val prefs = context.getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
        val prefStatus = prefs.getString("status_${medicine.id}", "")
        val isToday = isSameDay(medicine.completedTime, now)

        if (prefStatus == "TAKEN" && isToday) return "COMPLETED"
        if ((medicine.isCompleted || medicine.status == "COMPLETED") && isToday) return "COMPLETED"
        if (prefStatus == "MISSED" && isToday) return "MISSED"

        val todayScheduledTime = getTodayScheduledTime(medicine.scheduledTime)
        val isFutureDay = medicine.scheduledTime > now && !isSameDay(medicine.scheduledTime, now)

        if (isFutureDay) return "UPCOMING"

        val buffer = 30 * 60 * 1000L

        return when {
            todayScheduledTime == 0L -> "UPCOMING"
            
            // ✅ FIRST-DAY PROTECTION (Single Dose)
            medicine.createdAt > 0L && isSameDay(medicine.createdAt, now) && 
                medicine.createdAt > todayScheduledTime && now > (todayScheduledTime + buffer) -> "UPCOMING"

            now < todayScheduledTime -> "UPCOMING"
            now <= (todayScheduledTime + buffer) -> "DUE_NOW"
            else -> "MISSED"
        }
    }

    fun isSameDay(time1: Long, time2: Long): Boolean {
        if (time1 == 0L || time2 == 0L) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    fun parseTimeToMillis(timeStr: String): Long {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return try {
            val date = sdf.parse(timeStr) ?: return (System.currentTimeMillis() + 60 * 60 * 1000)
            val calendar = Calendar.getInstance()
            val timeCal = Calendar.getInstance().apply { time = date }
            
            calendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            if (calendar.timeInMillis < System.currentTimeMillis() - (30 * 60 * 1000)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            calendar.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis() + 60 * 60 * 1000
        }
    }
}
