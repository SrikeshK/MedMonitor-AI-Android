package com.medmonitor.receiver

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.MedMonitorApplication
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.VerificationMethod
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.ui.medicine.DoseConfirmationActivity
import com.medmonitor.ui.medicine.SuccessActivity
import com.medmonitor.util.CareAlertManager
import com.medmonitor.util.MedicineAlarmScheduler
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.SoundUtil
import com.medmonitor.worker.MissedDoseWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicineReminderReceiver : BroadcastReceiver() {

    private val repository = MedicineRepository()
    private val ACTION_CONFIRM = "com.medmonitor.ACTION_CONFIRM_NOW"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val type = intent.getStringExtra("type") ?: ""

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val medicines = repository.getAllMedicines()
                    medicines.forEach { medicine ->
                        MedicineAlarmScheduler(context).schedule(medicine)
                    }
                } catch (e: Exception) {
                    Log.e("ALARM_FIX", "Boot reschedule failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val medicineId = (
            intent.getStringExtra("medicine_id") 
                ?: intent.getStringExtra("medicineId")
        )?.trim()

        if (medicineId.isNullOrEmpty()) {
            return
        }

        val medicineName = intent.getStringExtra("medicineName") ?: "Medicine"
        val rawSlot = intent.getStringExtra("slot") ?: ""
        val actualTime = intent.getStringExtra("actual_time") ?: ""
        val normalizedSlot = MedicineStatusUtil.normalizeSlot(rawSlot)
        
        if (action == ACTION_CONFIRM) {
            handleDirectConfirm(context, medicineId, medicineName, normalizedSlot, actualTime)
            return
        }

        if (type == "REMINDER") {
            val pendingResult = goAsync()
            val prefs = context.getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
            val settingsManager = SettingsManager(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val medicine = repository.getMedicineById(medicineId)
                    
                    if (medicine == null) {
                        pendingResult.finish()
                        return@launch
                    }

                    val today = System.currentTimeMillis()
                    val endDate = medicine.endDate
                    if (endDate != null && endDate > 0 && today > endDate) {
                        MedicineAlarmScheduler.cancelMedicineAlarms(context, medicineId)
                        repository.deleteMedicine(medicineId)
                        pendingResult.finish()
                        return@launch
                    }

                    var dosageStr = "${medicine.dosageAmount.toInt()} ${medicine.unit}"
                    
                    if (MedicineStatusUtil.isMultiDose(medicine)) {
                        if (MedicineStatusUtil.shouldResetSlots(medicine)) {
                            repository.resetSlotStatus(medicineId, medicine.scheduleSlots)
                        }
                    } else {
                        if (!MedicineStatusUtil.isSameDay(medicine.completedTime, System.currentTimeMillis())) {
                            repository.resetTakenStatus(medicineId)
                            prefs.edit().putString("status_$medicineId", "PENDING").apply()
                        }
                    }
                    
                    MedicineAlarmScheduler(context).schedule(medicine)
                    
                    if (medicine.reminderOwner == "PATIENT") {
                        if (settingsManager.notifyImmediately) {
                            val patientUser = FirebaseAuth.getInstance().currentUser
                            val patientName = patientUser?.displayName ?: "The patient"
                            val patientId = patientUser?.uid ?: ""
                            
                            CareAlertManager.sendAlertNow(
                                context = context,
                                type = "IMMEDIATE",
                                patientId = patientId,
                                medicineId = medicineId,
                                medicineName = medicineName,
                                patientName = patientName,
                                slot = actualTime.ifEmpty { normalizedSlot },
                                dosage = dosageStr
                            )
                        }

                        // PHASE 3: Wire missed dose delay from settings. 
                        // Use toLong() to match MissedDoseWorker signature.
                        MissedDoseWorker.schedule(
                            context = context,
                            medicineId = medicineId,
                            medicineName = medicineName,
                            slotName = normalizedSlot,
                            scheduledTime = actualTime,
                            delayMinutes = settingsManager.missedDoseDelay.toLong()
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ALARM_FIX", "Reminder processing failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
            
            // PHASE 2: Check if notifications are enabled before showing UI.
            // This only suppresses the local reminder notification.
            if (settingsManager.notificationsEnabled) {
                showNotification(context, medicineId, medicineName, normalizedSlot, actualTime)
            }
        }
    }

    private fun handleDirectConfirm(context: Context, medicineId: String, medicineName: String, normalizedSlot: String, actualTime: String) {
        val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val uniqueWorkName = "missed_${medicineId}*${normalizedSlot}*${currentDate}"
        
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        CareAlertManager.cancelMissedAlert(context, medicineId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val medicine = repository.getMedicineById(medicineId)
                if (medicine != null) {
                    val doseLog = DoseLog(
                        medicineId = medicineId,
                        medicineName = medicine.name,
                        status = DoseStatus.TAKEN,
                        verificationMethod = VerificationMethod.MANUAL,
                        slotName = normalizedSlot,
                        scheduledTime = actualTime
                    )
                    
                    val result = repository.recordDose(doseLog, context)
                    
                    if (result == "SUCCESS" || result == "ALREADY_TAKEN") {
                        val prefs = context.getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
                        prefs.edit().putString("status_$medicineId", "TAKEN").apply()
                        
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel((medicineId + normalizedSlot).hashCode())

                        val successIntent = Intent(context, SuccessActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(successIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("ALARM_RECEIVER", "Error confirming from notification", e)
                if (e is IllegalStateException && e.message == "OUT_OF_STOCK") {
                    showOutOfStockNotification(context, medicineId, medicineName)
                    
                    // Dismiss original reminder
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel((medicineId + normalizedSlot).hashCode())
                }
            }
        }
    }

    private fun showOutOfStockNotification(context: Context, medicineId: String, medicineName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = MedMonitorApplication.CHANNEL_ID
        
        val intent = Intent(context, com.medmonitor.ui.InventoryActivity::class.java).apply {
            putExtra("medicine_id", medicineId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, medicineId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("Out of Stock")
            .setContentText("$medicineName is out of stock. Please refill inventory.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(medicineId.hashCode() + 999, notification)
    }

    private fun showNotification(context: Context, medicineId: String, medicineName: String, normalizedSlot: String, actualTime: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = MedMonitorApplication.CHANNEL_ID
        val settingsManager = SettingsManager(context)

        val contentIntent = Intent(context, DoseConfirmationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("medicine_id", medicineId)
            putExtra("medicine_name", medicineName)
            putExtra("slot", normalizedSlot)
            putExtra("actual_time", actualTime)
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context, (medicineId + normalizedSlot).hashCode(), contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = Intent(context, MedicineReminderReceiver::class.java).apply {
            action = ACTION_CONFIRM
            putExtra("medicine_id", medicineId)
            putExtra("medicineName", medicineName)
            putExtra("slot", normalizedSlot)
            putExtra("actual_time", actualTime)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context, (medicineId + normalizedSlot + "_confirm").hashCode(), confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = SoundUtil.getSoundUri(context, settingsManager.notificationSound)
        val readableSlot = normalizedSlot.lowercase().replaceFirstChar { it.uppercase() }
        val bigText = """
            $medicineName — $readableSlot dose
            Scheduled at ${actualTime.ifEmpty { "your set time" }}
            
            Please take it now to stay on track.
        """.trimIndent()

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💊 Medicine Reminder")
            .setContentText("$medicineName — $readableSlot dose")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(contentPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .addAction(R.drawable.ic_launcher_foreground, "Mark as Taken", confirmPendingIntent)

        if (!settingsManager.vibrationEnabled) {
            notificationBuilder.setVibrate(longArrayOf(0L))
        }

        notificationManager.notify((medicineId + normalizedSlot).hashCode(), notificationBuilder.build())
    }
}
