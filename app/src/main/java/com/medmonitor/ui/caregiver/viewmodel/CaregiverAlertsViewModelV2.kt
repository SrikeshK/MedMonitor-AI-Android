package com.medmonitor.ui.caregiver.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.medmonitor.data.model.caregiver.CaregiverAlertLog
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.data.repository.CaregiverRepositoryV2
import com.medmonitor.ui.caregiver.engine.CaregiverStatusEngine
import com.medmonitor.ui.caregiver.model.CaregiverTimelineItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class CaregiverAlertsViewModelV2(
    private val repository: CaregiverRepositoryV2 = CaregiverRepositoryV2()
) : ViewModel() {

    val timelineItems: StateFlow<List<CaregiverTimelineItem>> = repository.getCaregiverPatients()
        .flatMapLatest { patients ->
            if (patients.isEmpty()) return@flatMapLatest flowOf(emptyList<CaregiverTimelineItem>())

            val patientMedicinesFlows = patients.map { patient ->
                repository.getCaregiverMedicines(patient.patientId) // Use patientId for matching
            }

            combine(
                combine(patientMedicinesFlows) { it.toList().flatten() },
                repository.getCaregiverAlertLogs()
            ) { medicines, logs ->
                CaregiverStatusEngine.generateTimeline(patients, medicines, logs)
            }
        }
        .catch { e ->
            Log.e("CaregiverViewModel", "Error in timeline flow", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun markMedicineTaken(item: CaregiverTimelineItem) {
        viewModelScope.launch {
            try {
                val dateKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val log = CaregiverAlertLog(
                    patientId = item.patientId,
                    medicineId = item.medicineId,
                    medicineName = item.medicineName,
                    scheduledTime = item.scheduledTime,
                    status = "TAKEN",
                    actionTaken = "MANUAL_BY_CAREGIVER",
                    actionTimestamp = Timestamp.now(),
                    dateKey = dateKey
                )
                repository.addCaregiverAlertLog(log)
            } catch (e: Exception) {
                Log.e("CaregiverViewModel", "Error marking medicine as taken", e)
            }
        }
    }
}
