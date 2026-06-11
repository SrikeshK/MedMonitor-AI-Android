package com.medmonitor.ui.caregiver.model

import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.util.InventoryState

sealed class TimelineEvent {
    abstract val id: String
    abstract val timestamp: Long

    data class TimelineHeader(
        override val id: String, 
        val title: String,
        override val timestamp: Long = 0L
    ) : TimelineEvent()

    data class DoseEvent(
        override val id: String,
        val patient: CaregiverPatient,
        val medicineName: String,
        val doseLog: DoseLog?,
        val status: String,
        override val timestamp: Long,
        val relativeTime: String,
        val medicine: Medicine? = null,
        val sourceItem: CaregiverTimelineItem? = null
    ) : TimelineEvent()

    data class StockEvent(
        override val id: String,
        val patient: CaregiverPatient,
        val medicineName: String,
        val inventoryState: InventoryState,
        override val timestamp: Long,
        val relativeTime: String,
        val medicine: Medicine
    ) : TimelineEvent()

    data class DueSoonEvent(
        override val id: String,
        val patient: CaregiverPatient,
        val medicineName: String,
        val scheduledTime: String,
        val timeUntilDue: String,
        override val timestamp: Long,
        val medicine: Medicine
    ) : TimelineEvent()
}
