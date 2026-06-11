package com.medmonitor.ui.caregiver.engine

import android.util.Log
import com.medmonitor.data.model.caregiver.CaregiverAlertLog
import com.medmonitor.data.model.caregiver.CaregiverMedicine
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.ui.caregiver.model.CaregiverTimelineItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CaregiverStatusEngine {

    /**
     * Centralized timeline generation logic for Caregiver runtime.
     * Ensures consistent status evaluation across Dashboard, Alerts, and Profile.
     */
    fun generateTimeline(
        patients: List<CaregiverPatient>,
        medicines: List<CaregiverMedicine>,
        logs: List<CaregiverAlertLog>
    ): List<CaregiverTimelineItem> {
        return try {
            val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val timeline = mutableListOf<CaregiverTimelineItem>()

            medicines.forEach { medicine ->
                // CRITICAL SAFETY: Match using patient.patientId per centralized logic requirements
                val patient = patients.find { it.patientId == medicine.patientId } ?: return@forEach
                
                medicine.scheduleTimes.forEach { (_, time) ->
                    val isTaken = logs.any { 
                        it.medicineId == medicine.id && 
                        it.scheduledTime == time && 
                        it.dateKey == today && 
                        it.status == "TAKEN" 
                    }

                    val status = calculateStatus(time, isTaken)
                    val logEntry = logs.find { 
                        it.medicineId == medicine.id && 
                        it.scheduledTime == time && 
                        it.dateKey == today 
                    }

                    timeline.add(
                        CaregiverTimelineItem(
                            patientId = patient.patientId, // Consistent ID linkage
                            patientName = patient.patientName,
                            medicineId = medicine.id,
                            medicineName = medicine.medicineName,
                            dosage = medicine.dosage,
                            scheduledTime = time,
                            status = status,
                            timestamp = logEntry?.actionTimestamp,
                            phoneNumber = patient.phoneNumber
                        )
                    )
                }
            }

            timeline.sortedWith(compareBy({ it.scheduledTime }, { it.patientName }))
        } catch (e: Exception) {
            Log.e("CaregiverStatusEngine", "Error generating timeline", e)
            emptyList()
        }
    }

    fun isUpcoming(scheduledTime: String): Boolean {
        val now = Calendar.getInstance()
        val scheduled = getCalendarForTime(scheduledTime)
        return now.before(scheduled)
    }

    fun isDueNow(scheduledTime: String, isTaken: Boolean): Boolean {
        if (isTaken) return false
        val now = Calendar.getInstance()
        val scheduled = getCalendarForTime(scheduledTime)
        val dueUntil = (scheduled.clone() as Calendar).apply {
            add(Calendar.MINUTE, 30)
        }
        return (now.after(scheduled) || now == scheduled) && now.before(dueUntil)
    }

    fun isMissed(scheduledTime: String, isTaken: Boolean): Boolean {
        if (isTaken) return false
        val now = Calendar.getInstance()
        val scheduled = getCalendarForTime(scheduledTime)
        val dueUntil = (scheduled.clone() as Calendar).apply {
            add(Calendar.MINUTE, 30)
        }
        return now.after(dueUntil)
    }

    fun calculateStatus(scheduledTime: String, isTaken: Boolean): String {
        if (isTaken) return "TAKEN"
        if (isDueNow(scheduledTime, isTaken)) return "DUE_NOW"
        if (isMissed(scheduledTime, isTaken)) return "MISSED"

        val now = Calendar.getInstance()
        val scheduled = getCalendarForTime(scheduledTime)
        
        if (now.before(scheduled)) {
            val diffMinutes = (scheduled.timeInMillis - now.timeInMillis) / (1000 * 60)
            return if (diffMinutes >= 120) {
                "UPCOMING"
            } else {
                "DUE_SOON"
            }
        }

        return "UPCOMING"
    }

    private fun getCalendarForTime(time: String): Calendar {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val parsedDate = try {
            sdf.parse(time) ?: Date()
        } catch (e: Exception) {
            Date()
        }

        val parsedCalendar = Calendar.getInstance().apply {
            this.time = parsedDate
        }

        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parsedCalendar.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, parsedCalendar.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
