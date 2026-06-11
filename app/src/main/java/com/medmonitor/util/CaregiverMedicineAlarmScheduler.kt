package com.medmonitor.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medmonitor.data.model.caregiver.CaregiverMedicine
import com.medmonitor.receiver.CaregiverReminderReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CaregiverMedicineAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(medicine: CaregiverMedicine) {
        val cleanMedId = medicine.id.trim()
        val cleanPatId = medicine.patientId.trim()

        // Purge before reschedule
        cancelAllForMedicine(cleanPatId, cleanMedId, medicine.scheduleTimes.keys.toList())

        medicine.scheduleTimes.forEach { (slotName, timeStr) ->
            try {
                val intent = Intent(context, CaregiverReminderReceiver::class.java).apply {
                    putExtra("patient_id", cleanPatId)
                    putExtra("medicine_id", cleanMedId)
                    putExtra("medicine_name", medicine.medicineName)
                    putExtra("dosage", medicine.dosage)
                    putExtra("slot", slotName)
                    putExtra("time", timeStr)
                }

                val requestCode = (cleanPatId + cleanMedId + slotName).hashCode()
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerTime = parseTimeToTodayMillis(timeStr)
                
                val now = System.currentTimeMillis()
                val end = medicine.endDate?.toDate()?.time ?: Long.MAX_VALUE

                if (now > end) {
                    Log.d("CaregiverAlarm", "Medicine ${medicine.medicineName} has expired. Skipping.")
                    return@forEach
                }

                var finalTrigger = triggerTime
                if (finalTrigger < now) {
                    finalTrigger += AlarmManager.INTERVAL_DAY
                }
                
                if (finalTrigger <= end) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        finalTrigger,
                        pendingIntent
                    )
                    Log.d("CaregiverAlarm", "Scheduled alarm for ${medicine.medicineName} at $timeStr")
                }
            } catch (e: Exception) {
                Log.e("CaregiverAlarm", "Error scheduling alarm", e)
            }
        }
    }

    fun cancelAllForMedicine(patientId: String, medicineId: String, slots: List<String>) {
        val cleanPatId = patientId.trim()
        val cleanMedId = medicineId.trim()
        
        slots.forEach { slotName ->
            cancelSlotAlarm(cleanPatId, cleanMedId, slotName)
        }
        
        val legacySlots = listOf("Morning", "Afternoon", "Night", "Evening", "DEFAULT")
        legacySlots.forEach { slotName ->
            cancelSlotAlarm(cleanPatId, cleanMedId, slotName)
        }
    }

    private fun cancelSlotAlarm(patientId: String, medicineId: String, slotName: String) {
        val intent = Intent(context, CaregiverReminderReceiver::class.java)
        val requestCode = (patientId + medicineId + slotName).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun cancel(medicine: CaregiverMedicine) {
        cancelAllForMedicine(medicine.patientId, medicine.id, medicine.scheduleTimes.keys.toList())
    }

    private fun parseTimeToTodayMillis(timeStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf.parse(timeStr) ?: return 0L
            val calendar = Calendar.getInstance()
            val timeCal = Calendar.getInstance().apply { time = date }
            
            calendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            calendar.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }
}
