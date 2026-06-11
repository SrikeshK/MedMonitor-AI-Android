package com.medmonitor.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class CaregiverSmsManager {
    /**
     * Manual reminder sent by caregiver to patient.
     */
    fun sendReminder(context: Context, phoneNumber: String, medicineName: String, time: String) {
        try {
            val message = """
                Reminder:
                Take your medicine:
                $medicineName
                Scheduled Time: $time
            """.trimIndent()
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d("CaregiverSms", "SMS intent opened for $phoneNumber")
        } catch (e: Exception) {
            Log.e("CaregiverSms", "Failed to open SMS intent", e)
        }
    }

    /**
     * 🧩 PHASE 5: Automated Reminder with Deep Link
     * Triggered by CaregiverReminderReceiver. Pre-fills SMS with a remote confirmation link.
     */
    fun sendAutomatedReminder(
        context: Context,
        patientName: String,
        patientPhone: String,
        patientId: String,
        medicineId: String,
        medicineName: String,
        dosage: String,
        time: String,
        slot: String
    ) {
        try {
            // 🧩 Deep Link for Remote Confirmation (V2 Runtime)
            val deepLink = "medmonitor://confirm?patientId=$patientId&medicineId=$medicineId&slot=$slot"
            
            val message = """
                MedMonitor Reminder for $patientName:
                It's time for $medicineName ($dosage) at $time.
                
                Please confirm here once taken:
                $deepLink
            """.trimIndent()
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$patientPhone")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d("CaregiverSms", "Automated SMS intent pre-filled for $patientName")
        } catch (e: Exception) {
            Log.e("CaregiverSms", "Failed to open automated SMS intent", e)
        }
    }
}
