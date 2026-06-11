package com.medmonitor.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.medmonitor.R
import com.medmonitor.data.model.caregiver.CaregiverMedicine
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.ui.CaregiverMainActivity
import com.medmonitor.util.CaregiverSmsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CaregiverReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val patientId = intent.getStringExtra("patient_id") ?: return
        val medicineId = intent.getStringExtra("medicine_id") ?: return
        val medicineName = intent.getStringExtra("medicine_name") ?: "Medicine"
        val dosage = intent.getStringExtra("dosage") ?: ""
        val slot = intent.getStringExtra("slot") ?: ""
        val time = intent.getStringExtra("time") ?: ""

        Log.d("CaregiverReceiver", "Alarm received for $medicineName at $time")

        val smsManager = CaregiverSmsManager()
        val firestore = FirebaseFirestore.getInstance()

        // 🧩 PHASE 1: V2 Migration - Redirect to caregiver_medicines and caregiver_patients
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch medicine from V2 collection
                val medicineDoc = firestore.collection("caregiver_medicines").document(medicineId).get().await()
                val medicine = medicineDoc.toObject(CaregiverMedicine::class.java)
                
                if (medicine == null) {
                    Log.e("CaregiverReceiver", "Medicine $medicineId not found in caregiver_medicines. Aborting.")
                    return@launch
                }
                
                // Fetch patient data from V2 collection to get name and phone
                val patientQuery = firestore.collection("caregiver_patients")
                    .whereEqualTo("patientId", patientId)
                    .get().await()
                
                val patient = patientQuery.toObjects(CaregiverPatient::class.java).firstOrNull()
                
                patient?.let {
                    // 1. Send SMS reminder (existing flow)
                    smsManager.sendAutomatedReminder(
                        context = context,
                        patientName = it.patientName,
                        patientPhone = it.phoneNumber,
                        patientId = patientId,
                        medicineId = medicineId,
                        medicineName = medicineName,
                        dosage = dosage,
                        time = time,
                        slot = slot
                    )

                    // 2. Show local system notification (new additive fix)
                    showLocalNotification(context, it.patientName, medicineName, patientId, medicineId)

                } ?: Log.e("CaregiverReceiver", "Patient $patientId not found in caregiver_patients.")
            } catch (e: Exception) {
                Log.e("CaregiverReceiver", "Error fetching V2 data for reminder", e)
            }
        }
    }

    private fun showLocalNotification(context: Context, patientName: String, medicineName: String, patientId: String, medicineId: String) {
        val channelId = "caregiver_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (Safe for Min SDK 26)
        val channel = notificationManager.getNotificationChannel(channelId)
        if (channel == null) {
            val newChannel = NotificationChannel(
                channelId,
                "Caregiver Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for patient medicine reminders"
            }
            notificationManager.createNotificationChannel(newChannel)
        }

        // Prepare Intent to open CaregiverMainActivity and navigate to Alerts
        val intent = Intent(context, CaregiverMainActivity::class.java).apply {
            putExtra("navigate_to", "alerts")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val notificationId = (patientId + medicineId).hashCode()

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Medicine Reminder")
            .setContentText("$patientName needs to take $medicineName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Display notification
        notificationManager.notify(notificationId, notification)
    }
}
