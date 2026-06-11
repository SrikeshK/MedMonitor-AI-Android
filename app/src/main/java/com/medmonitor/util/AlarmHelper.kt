package com.medmonitor.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.medmonitor.receiver.MedicineReminderReceiver
import java.util.*

// ⚠️ DEPRECATED FILE
// This class is no longer used.
// Replaced by MedicineAlarmScheduler.
// Kept only for reference — DO NOT USE.

object AlarmHelper {

/*
Legacy alarm scheduling logic below is disabled.

Reason:
- Uses outdated request codes
- Can cause duplicate alarms
- Replaced by MedicineAlarmScheduler

Do not use unless refactored.

------------------------------------

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleReminder(context: Context, medicineId: String, medicineName: String, hour: Int, minute: Int, slot: String) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, MedicineReminderReceiver::class.java).apply {
            putExtra("type", "REMINDER")
            putExtra("medicineId", medicineId)
            putExtra("medicineName", medicineName)
            putExtra("slot", slot)
        }

        // Unique requestCode for this specific reminder
        val requestCode = (medicineId + "_" + slot + "_REM").hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("ALARM", "Reminder scheduled for $medicineName at ${calendar.time} (Code: $requestCode)")
        } catch (e: Exception) {
            Log.e("ALARM", "Error scheduling reminder: ${e.message}")
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleMissedAlert(context: Context, medicineId: String, medicineName: String, hour: Int, minute: Int, slot: String, delayMinutes: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val triggerTime = calendar.timeInMillis + (delayMinutes * 60 * 1000)

        val intent = Intent(context, MedicineReminderReceiver::class.java).apply {
            putExtra("type", "MISSED")
            putExtra("medicineId", medicineId)
            putExtra("medicineName", medicineName)
            putExtra("slot", slot)
        }

        // Unique requestCode for this specific missed alert
        val requestCode = (medicineId + "_" + slot + "_MISSED").hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d("ALARM", "Missed alert scheduled for $medicineName (Code: $requestCode)")
        } catch (e: Exception) {
            Log.e("ALARM", "Error scheduling missed alert: ${e.message}")
        }
    }

    fun cancelAlarms(context: Context, medicineId: String, slots: List<String>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (slot in slots) {
            val suffixes = listOf("_REM", "_MISSED")
            for (suffix in suffixes) {
                val intent = Intent(context, MedicineReminderReceiver::class.java)
                val requestCode = (medicineId + "_" + slot + suffix).hashCode()

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        Log.d("ALARM", "Old alarms cancelled for medicineId: $medicineId")
    }

------------------------------------
*/
}
