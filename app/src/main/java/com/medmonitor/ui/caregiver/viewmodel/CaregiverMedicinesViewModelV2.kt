package com.medmonitor.ui.caregiver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medmonitor.data.model.caregiver.CaregiverAlertLog
import com.medmonitor.data.model.caregiver.CaregiverMedicine
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.data.repository.CaregiverRepositoryV2
import com.medmonitor.ui.caregiver.engine.CaregiverStatusEngine
import com.medmonitor.util.CaregiverMedicineAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PatientWithMedicines(
    val patient: CaregiverPatient,
    val activeMedicines: List<MedicineWithStatus>,
    val pastMedicines: List<MedicineWithStatus>
)

data class MedicineWithStatus(
    val medicine: CaregiverMedicine,
    val status: String
)

sealed class CaregiverMedicinesUiState {
    object Loading : CaregiverMedicinesUiState()
    data class Success(val groupedData: List<PatientWithMedicines>) : CaregiverMedicinesUiState()
    data class Error(val message: String) : CaregiverMedicinesUiState()
}

class CaregiverMedicinesViewModelV2(
    private val repository: CaregiverRepositoryV2 = CaregiverRepositoryV2(),
    private val scheduler: CaregiverMedicineAlarmScheduler? = null // Passed if context is available
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaregiverMedicinesUiState>(CaregiverMedicinesUiState.Loading)
    val uiState: StateFlow<CaregiverMedicinesUiState> = _uiState
    private val todayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.getCaregiverPatients(),
                repository.getAllCaregiverMedicines(),
                repository.getCaregiverAlertLogs()
            ) { patients, allMedicines, allLogs ->
                patients.map { patient ->
                    val patientMedicines = allMedicines.filter { it.patientId == patient.patientId }
                    val patientLogs = allLogs.filter { it.patientId == patient.patientId && it.dateKey == todayKey }
                    
                    val (active, past) = patientMedicines.partition { medicine ->
                        val now = System.currentTimeMillis()
                        val endDate = medicine.endDate?.toDate()?.time ?: Long.MAX_VALUE
                        endDate >= now
                    }

                    PatientWithMedicines(
                        patient = patient,
                        activeMedicines = active.map { med -> 
                            MedicineWithStatus(med, calculateOverallStatus(med, patientLogs))
                        },
                        pastMedicines = past.map { med -> 
                            MedicineWithStatus(med, calculateOverallStatus(med, patientLogs))
                        }
                    )
                }
            }.collect { groupedList ->
                _uiState.value = CaregiverMedicinesUiState.Success(groupedList)
            }
        }
    }

    private fun calculateOverallStatus(medicine: CaregiverMedicine, todayLogs: List<CaregiverAlertLog>): String {
        if (medicine.scheduleTimes.isEmpty()) return "UPCOMING"
        
        val medLogs = todayLogs.filter { it.medicineId == medicine.id }
        
        val statuses = medicine.scheduleTimes.values.map { time ->
            val isTaken = medLogs.any { it.scheduledTime == time && it.status == "TAKEN" }
            CaregiverStatusEngine.calculateStatus(time, isTaken)
        }

        return when {
            statuses.contains("DUE_NOW") -> "DUE_NOW"
            statuses.contains("MISSED") -> "MISSED"
            statuses.all { it == "TAKEN" } -> "TAKEN"
            else -> "UPCOMING"
        }
    }

    fun deleteMedicine(medicine: CaregiverMedicine) {
        viewModelScope.launch {
            repository.deleteCaregiverMedicine(medicine.id)
            scheduler?.cancel(medicine)
        }
    }
}
