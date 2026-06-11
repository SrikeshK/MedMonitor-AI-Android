package com.medmonitor.ui.dashboard

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.data.model.AdvancedStats
import com.medmonitor.data.model.DailyStats
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.MedicineAlarmScheduler
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.StreakCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import com.google.firebase.Timestamp

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MedicineRepository()
    private val _authenticatedUserId = MutableStateFlow<String?>(null)
    
    private val _dailyStats = MutableStateFlow(DailyStats())
    val dailyStats: StateFlow<DailyStats> = _dailyStats

    private val _advancedStats = MutableStateFlow(AdvancedStats())
    val advancedStats: StateFlow<AdvancedStats> = _advancedStats

    private var cachedMedicineMap: Map<String, Medicine>? = null

    init {
        performSafeAlarmMigration()
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
                observeAnalytics(uid)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayMedicines: StateFlow<List<Medicine>> = _authenticatedUserId
        .filterNotNull()
        .flatMapLatest { uid ->
            repository.getMedicines(uid).map { allMedicines ->
                val now = System.currentTimeMillis()
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val todayStart = calendar.timeInMillis
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val todayEnd = calendar.timeInMillis

                allMedicines.filter { medicine ->
                    val end = medicine.endDate ?: Long.MAX_VALUE
                    val isExpired = end != Long.MAX_VALUE && now > end
                    
                    if (isExpired) return@filter false

                    val start = medicine.startDate ?: 0L
                    todayStart <= end && todayEnd >= start
                }.map { medicine ->
                    val status = MedicineStatusUtil.getMedicineStatus(medicine, getApplication())
                    medicine.copy(displayStatus = status)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun observeAnalytics(uid: String) {
        viewModelScope.launch {
            repository.getRecentDoseLogsFlow(uid).collect { logs ->
                if (cachedMedicineMap == null) {
                    cachedMedicineMap = repository.getAllMedicinesOnce(uid).associateBy { it.id }
                }

                val todayStart = getStartOfDay()
                val todayEnd = getEndOfDay()

                val todayLogs = logs.filter {
                    val logTime = try { it.timestamp.toDate().time } catch (e: Exception) { 0L }
                    logTime in todayStart..todayEnd
                }

                val taken = todayLogs.count { 
                    it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED 
                }
                val missed = todayLogs.count { it.status == DoseStatus.MISSED }
                val outOfStock = todayLogs.count { it.status == DoseStatus.OUT_OF_STOCK }

                Log.d("ANALYTICS_DEBUG", "DashboardViewModel -> logs.size: ${logs.size}, taken: $taken, missed: $missed")

                val delays = todayLogs.filter { 
                    it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED 
                }.mapNotNull { log ->
                    val med = cachedMedicineMap?.get(log.medicineId) ?: return@mapNotNull null
                    calculateDelay(log, med)
                }
                val avgDelay = if (delays.isEmpty()) 0L else delays.average().toLong()

                val totalAdherenceDenominator = taken + missed
                val adherence = if (totalAdherenceDenominator == 0) 0 else (taken * 100) / totalAdherenceDenominator

                _dailyStats.value = DailyStats(taken, missed, outOfStock, adherence, avgDelay)

                processAdvanced(logs)
            }
        }
    }

    private fun processAdvanced(logs: List<DoseLog>) {
        val missedSlots = logs
            .filter { it.status == DoseStatus.MISSED }
            .groupingBy { it.slotName.uppercase() }
            .eachCount()

        val mostMissedSlot = missedSlots.maxByOrNull { it.value }?.key ?: "None"

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
        val normalizedSlot = log.slotName.uppercase()
        val timeString = medicine.scheduleTimes[normalizedSlot] ?: return 0L
        return try {
            val sdf = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf.parse(timeString) ?: return 0L
            val timeCal = Calendar.getInstance().apply { time = date }

            val logDate = try { log.timestamp.toDate() } catch (e: Exception) { return 0L }
            val cal = Calendar.getInstance().apply {
                time = logDate
                set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            (logDate.time - cal.timeInMillis).coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
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

    fun getUserName(): String {
        return FirebaseAuth.getInstance().currentUser?.displayName ?: "User"
    }

    private fun performSafeAlarmMigration() {
        val prefs = getApplication<Application>().getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
        val isMigrated = prefs.getBoolean("alarms_migrated_v2", false)
        
        if (!isMigrated) {
            viewModelScope.launch {
                try {
                    val scheduler = MedicineAlarmScheduler(getApplication())
                    scheduler.refreshAllAlarms()
                    prefs.edit().putBoolean("alarms_migrated_v2", true).apply()
                } catch (e: Exception) {
                    Log.e("ALARM_FIX", "Alarm migration failed", e)
                }
            }
        }
    }
}
