package com.medmonitor.ui.medicine

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.MedicineAlarmScheduler
import com.medmonitor.util.MedicineStatusUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicineViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MedicineRepository()

    val medicines: StateFlow<List<Medicine>> = repository.getMedicines()
        .map { list ->
            Log.d("FLOW", "List size: ${list.size}")
            val now = System.currentTimeMillis()
            
            // 🧩 SAFE UI EXPIRY FILTER (LEGACY COMPATIBILITY)
            val filteredList = list.filter { medicine ->
                // The repository already normalizes endDate to Long.MAX_VALUE if it was 0 or null.
                val end = medicine.endDate ?: Long.MAX_VALUE
                end == Long.MAX_VALUE || now <= end
            }

            filteredList.map { medicine ->
                val status = MedicineStatusUtil.getMedicineStatus(medicine, getApplication())
                medicine.copy(displayStatus = status)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _operationStatus = MutableSharedFlow<Result<String>>()
    val operationStatus: SharedFlow<Result<String>> = _operationStatus

    fun deleteMedicine(id: String) {
        viewModelScope.launch {
            try {
                // 🧩 STEP A4: Cancel ALL alarms (base + slots) before deleting from DB
                MedicineAlarmScheduler.cancelMedicineAlarms(getApplication(), id)

                repository.deleteMedicine(id)
                _operationStatus.emit(Result.success("Medicine deleted successfully"))
            } catch (e: Exception) {
                _operationStatus.emit(Result.failure(e))
            }
        }
    }

    fun updateThreshold(medicineId: String, newThreshold: Double) {
        viewModelScope.launch {
            try {
                repository.updateThreshold(medicineId, newThreshold)
                _operationStatus.emit(Result.success("Threshold updated"))
            } catch (e: Exception) {
                _operationStatus.emit(Result.failure(e))
            }
        }
    }

    fun refillMedicine(medicineId: String, refillAmount: Double) {
        viewModelScope.launch {
            try {
                repository.refillMedicine(medicineId, refillAmount)
                _operationStatus.emit(Result.success("Stock updated"))
            } catch (e: Exception) {
                _operationStatus.emit(Result.failure(e))
            }
        }
    }

    fun confirmMedicine(doseLog: DoseLog, context: android.content.Context? = null) {
        viewModelScope.launch {
            try {
                Log.d("CONFIRM", "Clicked: ${doseLog.medicineId}")
                repository.recordDose(doseLog, context)
                _operationStatus.emit(Result.success("Dose confirmed"))
            } catch (e: Exception) {
                Log.e("CONFIRM", "Error confirming dose", e)
                _operationStatus.emit(Result.failure(e))
            }
        }
    }
}
