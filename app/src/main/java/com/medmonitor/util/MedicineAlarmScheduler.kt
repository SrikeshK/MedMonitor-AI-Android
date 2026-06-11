package com.medmonitor.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.receiver.MedicineReminderReceiver

class MedicineAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(medicine: Medicine) {
        Log.e("SMS_TRACE", "===== SCHEDULER START: ${medicine.name} =====")
        val cleanId = medicine.id.trim()

        // 🧩 STEP A3: Purge all possible previous/stale slots before rescheduling
        // Includes known legacy slots and current active slots to ensure thorough cleanup.
        val allPossibleSlots = mutableListOf("MORNING", "AFTERNOON", "EVENING", "NIGHT")
        allPossibleSlots.addAll(medicine.scheduleSlots)
        allPossibleSlots.addAll(medicine.scheduleTimes.keys)
        
        cancelAllForMedicine(context, cleanId, allPossibleSlots.distinct())

        // 2. Schedule new alarm (ONLY if single dose)
        if (medicine.scheduleTimes.isEmpty()) {
            val normalizedSlot = "DEFAULT"
            val timeStr = MedicineStatusUtil.formatTime(medicine.scheduledTime)
            
            val newIntent = Intent(context, MedicineReminderReceiver::class.java).apply {
                putExtra("medicine_id", cleanId)
                putExtra("type", "REMINDER")
                putExtra("medicineName", medicine.name)
                putExtra("slot", normalizedSlot)
                putExtra("actual_time", timeStr)
            }

            val requestCode = cleanId.hashCode()
            val newPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            var triggerTime = MedicineStatusUtil.getTodayScheduledTime(medicine.scheduledTime)
            if (triggerTime < System.currentTimeMillis()) {
                triggerTime += AlarmManager.INTERVAL_DAY
            }

            if (triggerTime > 0) {
                Log.e("SMS_TRACE", "Scheduling Single Alarm: ${medicine.name} at ${MedicineStatusUtil.formatTime(triggerTime)}")
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    newPendingIntent
                )
            }
        }

        // ✅ MULTI-SLOT LOOP
        medicine.scheduleTimes.forEach { (slotName, timeStr) ->
            try {
                val normalizedSlot = MedicineStatusUtil.normalizeSlot(slotName)
                val slotTimeMillis = MedicineStatusUtil.parseTimeToMillis(timeStr)
                var slotTriggerTime = MedicineStatusUtil.getTodayScheduledTime(slotTimeMillis)

                if (slotTriggerTime < System.currentTimeMillis()) {
                    slotTriggerTime += AlarmManager.INTERVAL_DAY
                }

                val uniqueRequestCode = (cleanId + "_" + normalizedSlot).hashCode()

                val multiIntent = Intent(context, MedicineReminderReceiver::class.java).apply {
                    putExtra("medicine_id", cleanId)
                    putExtra("medicineName", medicine.name)
                    putExtra("slot", normalizedSlot)
                    putExtra("actual_time", timeStr)
                    putExtra("type", "REMINDER")
                }

                val multiPendingIntent = PendingIntent.getBroadcast(
                    context,
                    uniqueRequestCode,
                    multiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (slotTriggerTime > 0) {
                    Log.e("SMS_TRACE", "Scheduling Multi-Slot Alarm: $normalizedSlot for ${medicine.name} at ${MedicineStatusUtil.formatTime(slotTriggerTime)}")
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        slotTriggerTime,
                        multiPendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.e("SMS_TRACE", "Scheduling FAILED for $slotName", e)
            }
        }
        Log.e("SMS_TRACE", "===== SCHEDULER END =====")
    }

    fun cancel(medicine: Medicine) {
        val cleanId = medicine.id.trim()
        val slots = mutableListOf<String>()
        slots.addAll(medicine.scheduleSlots)
        slots.addAll(medicine.scheduleTimes.keys)
        cancelAllForMedicine(context, cleanId, slots.distinct())
    }

    /**
     * 🧩 STEP A1: Centralized cleanup for all medicine-specific alarms.
     * Safely cancels base alarm and all specified slot alarms.
     */
    fun cancelAllForMedicine(
        context: Context,
        medicineId: String,
        possibleSlots: List<String>
    ) {
        val cleanId = medicineId.trim()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. Cancel base/single-dose alarm
        // 🧩 STEP A2: RequestCode = medicineId.hashCode()
        val baseIntent = Intent(context, MedicineReminderReceiver::class.java)
        val basePendingIntent = PendingIntent.getBroadcast(
            context,
            cleanId.hashCode(),
            baseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(basePendingIntent)
        basePendingIntent.cancel()

        // 2. Cancel all slot alarms (current and legacy)
        possibleSlots.forEach { slotName ->
            val normalizedSlot = MedicineStatusUtil.normalizeSlot(slotName)
            // 🧩 STEP A2: RequestCode = (medicineId + "_" + slotName).hashCode()
            val uniqueRequestCode = (cleanId + "_" + normalizedSlot).hashCode()
            
            val slotIntent = Intent(context, MedicineReminderReceiver::class.java)
            val slotPendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueRequestCode,
                slotIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(slotPendingIntent)
            slotPendingIntent.cancel()
        }
        
        Log.d("ALARM_CLEANUP", "Cancelled all alarms for medicine: $cleanId")
    }

    suspend fun refreshAllAlarms() {
        try {
            val repository = MedicineRepository()
            val medicines = repository.getAllMedicinesOnce()
            
            medicines.forEach { medicine ->
                schedule(medicine)
            }
            Log.d("ALARM_MIGRATION", "Successfully refreshed all alarms.")
        } catch (e: Exception) {
            Log.e("ALARM_MIGRATION", "Failed to refresh alarms", e)
        }
    }

    companion object {
        fun cancelMedicineAlarms(context: Context, medicineId: String) {
            val scheduler = MedicineAlarmScheduler(context)
            // Legacy/safety slots to ensure thorough cleanup
            scheduler.cancelAllForMedicine(context, medicineId, listOf("MORNING", "AFTERNOON", "EVENING", "NIGHT"))
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun testNotification() {
        val intent = Intent(context, MedicineReminderReceiver::class.java).apply {
            putExtra("type", "REMINDER")
            putExtra("medicine_id", "test_id")
            putExtra("medicineName", "Test Medicine")
            putExtra("slot", "TEST")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 5000,
            pendingIntent
        )
    }
}
