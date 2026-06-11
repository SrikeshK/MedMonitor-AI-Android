package com.medmonitor.ui.caregiver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medmonitor.data.model.caregiver.CaregiverAlertLog
import com.medmonitor.data.model.caregiver.CaregiverMedicine
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.data.repository.CaregiverRepositoryV2
import com.medmonitor.ui.caregiver.engine.CaregiverStatusEngine
import com.medmonitor.ui.caregiver.model.CaregiverTimelineItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class CaregiverDashboardViewModelV2(
    private val repository: CaregiverRepositoryV2 = CaregiverRepositoryV2()
) : ViewModel() {

    private fun getTodayKey() = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    // Centralized timeline flow - SAME truth source as Alerts
    val timelineItems: StateFlow<List<CaregiverTimelineItem>> = repository.getCaregiverPatients()
        .flatMapLatest { patients ->
            if (patients.isEmpty()) return@flatMapLatest flowOf(emptyList())

            val patientMedicinesFlows = patients.map { patient ->
                repository.getCaregiverMedicines(patient.patientId)
            }

            combine(
                combine(patientMedicinesFlows) { it.toList().flatten() },
                repository.getCaregiverAlertLogs()
            ) { medicines, logs ->
                CaregiverStatusEngine.generateTimeline(patients, medicines, logs)
            }
        }
        .catch { e ->
            Log.e("DashboardViewModel", "Error in timeline flow", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val patients: StateFlow<List<CaregiverPatient>> = repository.getCaregiverPatients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPatients = patients.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Derived counts from centralized timeline
    val dueNowCount = timelineItems.map { items ->
        items.count { it.status == "DUE_NOW" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val missedCount = timelineItems.map { items ->
        items.count { it.status == "MISSED" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val takenTodayCount = timelineItems.map { items ->
        items.count { it.status == "TAKEN" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val caregiverAdherence = timelineItems.map { items ->
        val taken = items.count { it.status == "TAKEN" }
        val missed = items.count { it.status == "MISSED" }
        if (taken + missed == 0) 100.0 else (taken.toDouble() / (taken + missed)) * 100.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100.0)

    val nextMedication = combine(
        repository.getAllCaregiverMedicines(),
        patients
    ) { meds, patientsList ->
        findNextUpcomingMedicine(meds, patientsList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val attentionState = combine(dueNowCount, missedCount) { dueNow, missed ->
        when {
            missed > 0 -> AttentionInfo("$missed missed medication", "#FF5252")
            dueNow > 0 -> AttentionInfo("$dueNow medicines due now", "#FFBF00")
            else -> AttentionInfo("All patients stable", "#00E676")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AttentionInfo("All patients stable", "#00E676"))

    private fun findNextUpcomingMedicine(
        medicines: List<CaregiverMedicine>, 
        patients: List<CaregiverPatient>
    ): NextMedDisplay? {
        val now = Calendar.getInstance()
        val currentTimeInMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

        var closestMed: CaregiverMedicine? = null
        var closestPatient: CaregiverPatient? = null
        var closestTimeStr: String? = null
        var minDiff = Int.MAX_VALUE

        medicines.forEach { med ->
            // CRITICAL: Match using patientId
            val patient = patients.find { it.patientId == med.patientId } ?: return@forEach
            med.scheduleTimes.values.forEach { timeStr ->
                try {
                    val date = sdf.parse(timeStr) ?: return@forEach
                    val cal = Calendar.getInstance().apply { time = date }
                    val timeInMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    
                    val diff = timeInMinutes - currentTimeInMinutes
                    if (diff > 0 && diff < minDiff) {
                        minDiff = diff
                        closestMed = med
                        closestPatient = patient
                        closestTimeStr = timeStr
                    }
                } catch (e: Exception) {
                    // Skip invalid formats
                }
            }
        }

        return if (closestMed != null && closestPatient != null) {
            NextMedDisplay(
                patientName = closestPatient.patientName,
                medicineName = closestMed.medicineName,
                dosage = closestMed.dosage,
                scheduledTime = closestTimeStr ?: "",
                diffMinutes = minDiff
            )
        } else null
    }

    data class NextMedDisplay(
        val patientName: String,
        val medicineName: String,
        val dosage: String,
        val scheduledTime: String,
        val diffMinutes: Int
    )

    data class AttentionInfo(val text: String, val color: String)
}
