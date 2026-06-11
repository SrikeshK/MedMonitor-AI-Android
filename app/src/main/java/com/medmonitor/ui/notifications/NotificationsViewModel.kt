package com.medmonitor.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.MedicineStatusUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.*

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MedicineRepository()

    val notificationItems: StateFlow<List<NotificationListItem>> = repository.getMedicines()
        .map { medicines ->
            processAndGroupAlerts(medicines)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun processAndGroupAlerts(medicines: List<Medicine>): List<NotificationListItem> {
        val missed = mutableListOf<AlertItem>()
        val dueNow = mutableListOf<AlertItem>()
        val upcoming = mutableListOf<AlertItem>()
        val now = System.currentTimeMillis()

        for (medicine in medicines) {
            // 🧩 SAFE UI EXPIRY FILTER
            val end = medicine.endDate ?: 0L
            if (end > 0L && now > end) {
                continue
            }

            // ✅ PART 6 — VIEWMODEL (MAIN CONTROL)
            // IF multi-dose: IGNORE isCompleted/status, USE slotStatus ONLY
            if (MedicineStatusUtil.isMultiDose(medicine)) {
                medicine.scheduleTimes.forEach { (slotName, time) ->
                    // ✅ FINAL FIX — NORMALIZE SLOT FOR LOOKUP
                    val normalizedSlot = MedicineStatusUtil.normalizeSlot(slotName)
                    
                    // ✅ FIXED: Using day-aware engine status with first-day protection
                    val status = MedicineStatusUtil.getSlotStatus(
                        time, 
                        medicine.slotStatus[normalizedSlot], 
                        now, 
                        medicine.lastUpdatedTime,
                        medicine.createdAt
                    )
                    
                    val alertStatus = when(status) {
                        "MISSED" -> AlertStatus.MISSED
                        "DUE_NOW" -> AlertStatus.DUE
                        "COMPLETED" -> AlertStatus.TAKEN
                        else -> AlertStatus.UPCOMING
                    }
                    
                    if (alertStatus != AlertStatus.TAKEN) {
                        val alertItem = AlertItem(medicine, time, alertStatus, slotName)
                        when (status) {
                            "UPCOMING" -> upcoming.add(alertItem)
                            "DUE_NOW" -> dueNow.add(alertItem)
                            "MISSED" -> missed.add(alertItem)
                        }
                    }
                }
            } else {
                // Keep original logic for single-dose (Backward Compatibility - PART 11)
                val status = MedicineStatusUtil.getMedicineStatus(medicine, getApplication())
                if (status == "COMPLETED") continue

                val slotEntry = medicine.scheduleTimes.entries.firstOrNull()
                val slotName = slotEntry?.key ?: ""
                val time = slotEntry?.value ?: ""
                
                val alertStatus = when (status) {
                    "MISSED" -> AlertStatus.MISSED
                    "DUE_NOW" -> AlertStatus.DUE
                    else -> AlertStatus.UPCOMING
                }
                
                val alertItem = AlertItem(medicine, time, alertStatus, slotName)

                when (status) {
                    "UPCOMING" -> upcoming.add(alertItem)
                    "DUE_NOW" -> dueNow.add(alertItem)
                    "MISSED" -> missed.add(alertItem)
                }
            }
        }

        val result = mutableListOf<NotificationListItem>()

        addSection(result, "Missed", AlertStatus.MISSED, missed, "No missed doses")
        addSection(result, "Due Now", AlertStatus.DUE, dueNow, "No doses due right now")
        addSection(result, "Upcoming", AlertStatus.UPCOMING, upcoming, "No upcoming medicines")

        return result
    }

    private fun addSection(
        result: MutableList<NotificationListItem>,
        title: String,
        status: AlertStatus,
        items: List<AlertItem>,
        emptyMessage: String
    ) {
        result.add(NotificationListItem.Header(title, status))
        if (items.isEmpty()) {
            result.add(NotificationListItem.EmptyState(emptyMessage))
        } else {
            result.addAll(items.map { NotificationListItem.Alert(it) })
        }
    }
}

sealed class NotificationListItem {
    data class Header(val title: String, val status: AlertStatus) : NotificationListItem()
    data class Alert(val alert: AlertItem) : NotificationListItem()
    data class EmptyState(val message: String) : NotificationListItem()
}

data class AlertItem(
    val medicine: Medicine,
    val time: String,
    val status: AlertStatus,
    val slotName: String
)

enum class AlertStatus(val priority: Int) {
    MISSED(0),
    DUE(1),
    UPCOMING(2),
    TAKEN(3)
}
