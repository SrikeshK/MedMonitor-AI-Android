package com.medmonitor.ui.analytics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.data.model.AdvancedStats
import com.medmonitor.data.model.DailyStats
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.WeeklyStats
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.StreakCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class AnalyticsViewModel : ViewModel() {

    private val repository = MedicineRepository()
    private val _authenticatedUserId = MutableStateFlow<String?>(null)

    private val _dailyStats = MutableStateFlow(DailyStats())
    val dailyStats: StateFlow<DailyStats> = _dailyStats

    private val _weeklyStats = MutableStateFlow(WeeklyStats())
    val weeklyStats: StateFlow<WeeklyStats> = _weeklyStats

    private val _advancedStats = MutableStateFlow(AdvancedStats())
    val advancedStats: StateFlow<AdvancedStats> = _advancedStats

    private var cachedMedicineMap: Map<String, Medicine>? = null

    init {
        stabilizeAuthAndStart()
    }

    private fun stabilizeAuthAndStart() {
        viewModelScope.launch {
            val auth = FirebaseAuth.getInstance()
            var user = auth.currentUser
            
            // 🧩 SAFE AUTH WAIT: Wait up to 2 seconds for Firebase session to stabilize
            if (user == null) {
                for (i in 1..20) {
                    delay(100)
                    user = auth.currentUser
                    if (user != null) break
                }
            }

            user?.uid?.let { uid ->
                _authenticatedUserId.value = uid
                start(uid)
            }
        }
    }

    private fun start(uid: String) {
        viewModelScope.launch {
            repository.getRecentDoseLogsFlow(uid).collect { logs ->
                if (cachedMedicineMap == null) {
                    cachedMedicineMap = repository.getAllMedicinesOnce(uid).associateBy { it.id }
                }
                processLogs(logs)
            }
        }
    }

    private fun processLogs(logs: List<DoseLog>) {
        val todayStart = getStartOfDay()
        val todayEnd = getEndOfDay()

        val todayLogs = logs.filter {
            // 🧩 SAFE TIMESTAMP ACCESS
            val logTime = try { it.timestamp.toDate().time } catch (e: Exception) { 0L }
            logTime in todayStart..todayEnd
        }

        // 🧩 TAKEN COUNT FIX: Include DELAYED
        val taken = todayLogs.count { 
            it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED 
        }
        val missed = todayLogs.count { it.status == DoseStatus.MISSED }
        // 🧩 PHASE 3: OUT_OF_STOCK counted separately, doesn't penalize adherence
        val outOfStock = todayLogs.count { it.status == DoseStatus.OUT_OF_STOCK }

        Log.d("ANALYTICS_DEBUG", "AnalyticsViewModel -> logs.size: ${logs.size}, taken: $taken, missed: $missed")

        val delays = todayLogs.filter { 
            it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED 
        }.mapNotNull { log ->
            val med = cachedMedicineMap?.get(log.medicineId) ?: return@mapNotNull null
            calculateDelay(log, med)
        }
        val avgDelay = if (delays.isEmpty()) 0L else delays.average().toLong()

        val totalAdherenceDenominator = taken + missed
        val adherence = if (totalAdherenceDenominator == 0) 0 else (taken * 100) / totalAdherenceDenominator

        // 🧩 SAFE COMPILATION FIX: Include outOfStock to match DailyStats constructor
        _dailyStats.value = DailyStats(taken, missed, outOfStock, adherence, avgDelay)

        processWeekly(logs)
        processAdvanced(logs)
    }

    private fun processWeekly(logs: List<DoseLog>) {
        val map = mutableMapOf<String, DailyStats>()
        val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        for (i in 0..6) {
            val dayStart = getStartOfDayMinus(i)
            val dayEnd = getEndOfDayMinus(i)

            val dayLogs = logs.filter {
                val logTime = try { it.timestamp.toDate().time } catch (e: Exception) { 0L }
                logTime in dayStart..dayEnd
            }

            // 🧩 TAKEN COUNT FIX: Include DELAYED
            val taken = dayLogs.count { 
                it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED 
            }
            val missed = dayLogs.count { it.status == DoseStatus.MISSED }
            val outOfStock = dayLogs.count { it.status == DoseStatus.OUT_OF_STOCK }

            val delays = dayLogs.filter { 
                it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED 
            }.mapNotNull { log ->
                val med = cachedMedicineMap?.get(log.medicineId) ?: return@mapNotNull null
                calculateDelay(log, med)
            }
            val avgDelay = if (delays.isEmpty()) 0L else delays.average().toLong()

            val totalAdherenceDenominator = taken + missed
            val adherence = if (totalAdherenceDenominator == 0) 0 else (taken * 100) / totalAdherenceDenominator

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dayKey = days[calendar.get(Calendar.DAY_OF_WEEK) - 1]

            // 🧩 SAFE COMPILATION FIX: Include outOfStock to match DailyStats constructor
            map[dayKey] = DailyStats(taken, missed, outOfStock, adherence, avgDelay)
        }

        _weeklyStats.value = WeeklyStats(map)
    }

    private fun processAdvanced(logs: List<DoseLog>) {
        val missedSlots = logs
            .filter { it.status == DoseStatus.MISSED }
            .groupingBy { it.slotName }
            .eachCount()

        val mostMissedSlot = missedSlots.maxByOrNull { it.value }?.key ?: "None"

        // 🧩 TAKEN COUNT FIX: Include DELAYED
        val allDelays = logs.filter { 
            it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED 
        }.mapNotNull { log ->
            val med = cachedMedicineMap?.get(log.medicineId) ?: return@mapNotNull null
            calculateDelay(log, med)
        }
        val totalAvgDelay = if (allDelays.isEmpty()) 0L else allDelays.average().toLong()

        _advancedStats.value = AdvancedStats(
            streak = StreakCalculator.calculateStreak(logs),
            avgDelayMinutes = (totalAvgDelay / 60000).toInt(),
            mostMissedSlot = mostMissedSlot
        )
    }

    private fun calculateDelay(log: DoseLog, medicine: Medicine): Long {
        val timeString = medicine.scheduleTimes[log.slotName] ?: return 0L
        val logDate = try { log.timestamp.toDate() } catch (e: Exception) { return 0L }
        
        // 🧩 SAFE FIX: Use standardized parsing to handle "hh:mm a" format
        val scheduledMillis = MedicineStatusUtil.parseTimeToTodayMillis(timeString, logDate.time)
        if (scheduledMillis == 0L) return 0L
        
        return (logDate.time - scheduledMillis).coerceAtLeast(0L)
    }

    private fun getStartOfDay(): Long = getStartOfDayMinus(0)
    private fun getEndOfDay(): Long = getEndOfDayMinus(0)

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
