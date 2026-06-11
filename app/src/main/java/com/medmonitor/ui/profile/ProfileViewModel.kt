package com.medmonitor.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.StreakCalculator
import kotlinx.coroutines.flow.*
import java.util.*

data class ProfileHealthSnapshot(
    val medicineCount: Int = 0,
    val adherencePercent: Int = 0,
    val streak: Int = 0
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MedicineRepository()
    private val auth = FirebaseAuth.getInstance()
    private val userId = auth.currentUser?.uid ?: ""

    val medicines: StateFlow<List<Medicine>> = repository.getMedicines()
        .map { list ->
            list.map { medicine ->
                val status = MedicineStatusUtil.getMedicineStatus(medicine, getApplication())
                medicine.copy(displayStatus = status)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val healthSnapshot: StateFlow<ProfileHealthSnapshot> = if (userId.isEmpty()) {
        MutableStateFlow(ProfileHealthSnapshot())
    } else {
        combine(
            medicines,
            repository.getRecentDoseLogsFlow(userId)
        ) { meds, logs ->
            calculateSnapshot(meds, logs)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileHealthSnapshot()
        )
    }

    private fun calculateSnapshot(meds: List<Medicine>, logs: List<DoseLog>): ProfileHealthSnapshot {
        if (logs.isEmpty()) {
            return ProfileHealthSnapshot(medicineCount = meds.size)
        }

        // Adherence Calculation
        val todayStart = getStartOfDay()
        val todayEnd = getEndOfDay()

        val todayLogs = logs.filter {
            val logTime = try { it.timestamp.toDate().time } catch (e: Exception) { 0L }
            logTime in todayStart..todayEnd
        }

        val taken = todayLogs.count { it.status == DoseStatus.TAKEN || it.status == DoseStatus.DELAYED }
        val missed = todayLogs.count { it.status == DoseStatus.MISSED }
        
        val totalAdherenceDenominator = taken + missed
        val adherence = if (totalAdherenceDenominator == 0) 0 else (taken * 100) / totalAdherenceDenominator

        // ✅ FIXED STREAK CALCULATION
        val streak = StreakCalculator.calculateStreak(logs)

        return ProfileHealthSnapshot(
            medicineCount = meds.size,
            adherencePercent = adherence,
            streak = streak
        )
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
