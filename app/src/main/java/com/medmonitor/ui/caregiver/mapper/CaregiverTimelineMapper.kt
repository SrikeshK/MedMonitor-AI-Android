package com.medmonitor.ui.caregiver.mapper

import android.util.Log
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.ui.caregiver.model.CaregiverTimelineItem
import com.medmonitor.ui.caregiver.model.TimelineEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CaregiverTimelineMapper {
    fun map(items: List<CaregiverTimelineItem>?): List<TimelineEvent> {
        if (items == null) return emptyList()
        
        return try {
            val events = mutableListOf<TimelineEvent>()
            
            val dueNowItems = items.filter { it.status == "DUE_NOW" }
            val missedItems = items.filter { it.status == "MISSED" }
            val takenItems = items.filter { it.status == "TAKEN" }
            val dueSoonItems = items.filter { it.status == "DUE_SOON" }
            val upcomingItems = items.filter { it.status == "UPCOMING" }

            if (dueNowItems.isNotEmpty()) {
                events.add(TimelineEvent.TimelineHeader("due_now_header", "DUE NOW"))
                events.addAll(dueNowItems.mapNotNull { mapToDoseEvent(it, "DUE NOW") })
            }

            if (dueSoonItems.isNotEmpty()) {
                events.add(TimelineEvent.TimelineHeader("due_soon_header", "DUE SOON"))
                events.addAll(dueSoonItems.mapNotNull { mapToDueSoonEvent(it, "Due Soon") })
            }

            if (upcomingItems.isNotEmpty()) {
                events.add(TimelineEvent.TimelineHeader("upcoming_header", "UPCOMING"))
                events.addAll(upcomingItems.mapNotNull { mapToDueSoonEvent(it, "Upcoming") })
            }

            if (missedItems.isNotEmpty()) {
                events.add(TimelineEvent.TimelineHeader("missed_header", "MISSED"))
                events.addAll(missedItems.mapNotNull { mapToDoseEvent(it, "MISSED") })
            }

            if (takenItems.isNotEmpty()) {
                events.add(TimelineEvent.TimelineHeader("taken_header", "COMPLETED TODAY"))
                events.addAll(takenItems.mapNotNull { mapToDoseEvent(it, "COMPLETED") })
            }

            events
        } catch (e: Exception) {
            Log.e("CaregiverMapper", "Error mapping timeline items", e)
            emptyList()
        }
    }

    private fun mapToDoseEvent(item: CaregiverTimelineItem, statusDisplay: String): TimelineEvent.DoseEvent? {
        return try {
            val patient = CaregiverPatient(
                patientId = item.patientId, 
                patientName = item.patientName ?: "Unknown Patient", 
                phoneNumber = item.phoneNumber ?: ""
            )
            val medicine = Medicine(
                id = item.medicineId, 
                name = item.medicineName ?: "Unknown Medicine"
            )
            
            val timestamp = item.timestamp?.toDate()?.time ?: parseTimeToTodayMillis(item.scheduledTime)
            
            TimelineEvent.DoseEvent(
                id = "${item.medicineId}_${item.scheduledTime}_${item.status}",
                patient = patient,
                medicineName = item.medicineName ?: "Unknown",
                doseLog = null,
                status = statusDisplay,
                timestamp = timestamp,
                relativeTime = item.scheduledTime ?: "",
                medicine = medicine,
                sourceItem = item
            )
        } catch (e: Exception) {
            Log.e("CaregiverMapper", "Error mapping to DoseEvent", e)
            null
        }
    }

    private fun mapToDueSoonEvent(item: CaregiverTimelineItem, displayStatus: String): TimelineEvent.DueSoonEvent? {
        return try {
            val patient = CaregiverPatient(
                patientId = item.patientId, 
                patientName = item.patientName ?: "Unknown Patient", 
                phoneNumber = item.phoneNumber ?: ""
            )
            val medicine = Medicine(
                id = item.medicineId, 
                name = item.medicineName ?: "Unknown Medicine"
            )
            
            val timestamp = parseTimeToTodayMillis(item.scheduledTime)
            
            TimelineEvent.DueSoonEvent(
                id = "${item.medicineId}_${item.scheduledTime}_${item.status.lowercase()}",
                patient = patient,
                medicineName = item.medicineName ?: "Unknown",
                scheduledTime = item.scheduledTime ?: "",
                timeUntilDue = displayStatus,
                timestamp = timestamp,
                medicine = medicine
            )
        } catch (e: Exception) {
            Log.e("CaregiverMapper", "Error mapping to DueSoonEvent", e)
            null
        }
    }

    private fun parseTimeToTodayMillis(timeStr: String?): Long {
        if (timeStr.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf.parse(timeStr) ?: return System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            val timeCalendar = Calendar.getInstance()
            timeCalendar.time = date
            
            calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
