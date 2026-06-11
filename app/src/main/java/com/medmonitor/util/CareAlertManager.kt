package com.medmonitor.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.medmonitor.MedMonitorApplication
import com.medmonitor.R
import com.medmonitor.data.SettingsManager

object CareAlertManager {
    private const val TAG = "CARE_ALERT"

    fun cancelMissedAlert(context: Context, medicineId: String) {
        val prefs = context.getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("confirmed_$medicineId", true).apply()
        Log.d(TAG, "Dose confirmed for $medicineId. Caregiver MISSED alert will be suppressed.")
    }

    suspend fun sendMissedAlert(
        context: Context,
        patientId: String,
        medicineId: String,
        medicineName: String,
        patientName: String,
        slot: String
    ) {
        sendAlertNow(
            context = context,
            type = "MISSED",
            patientId = patientId,
            medicineId = medicineId,
            medicineName = medicineName,
            patientName = patientName,
            slot = slot
        )
    }

    suspend fun sendAlertNow(
        context: Context,
        type: String,
        patientId: String,
        medicineId: String,
        medicineName: String,
        patientName: String,
        slot: String,
        dosage: String? = null,
        remainingQuantity: Double = 0.0,
        daysLeft: Int = 0
    ) {
        Log.d(TAG, "===== sendAlertNow: $type for $medicineName =====")

        val settingsManager = SettingsManager(context)

        val fallback = settingsManager.caregiverPhone.trim()
        val cached = settingsManager.getCachedCaregivers().map { it.phone }
        val finalRecipients = mutableListOf<String>()

        if (fallback.isNotEmpty()) {
            finalRecipients.add(fallback)
        }

        cached.forEach {
            if (!it.isNullOrBlank()) finalRecipients.add(it.trim())
        }

        if (finalRecipients.isEmpty()) {
            Log.e("SMS", "No caregivers configured. SMS skipped safely.")
            return
        }

        val message = buildMessage(type, patientId, medicineId, medicineName, patientName, slot, dosage, remainingQuantity, daysLeft)

        for (phone in finalRecipients) {
            sendSMS(context, phone, message)
        }
    }

    private fun buildMessage(
        type: String,
        patientId: String,
        medicineId: String,
        medicineName: String,
        patientName: String,
        slot: String,
        dosage: String?,
        remainingQuantity: Double,
        daysLeft: Int
    ): String {
        return when (type) {
            "IMMEDIATE" -> {
                """
                💊 MedMonitor Alert
                
                Time for medication: $medicineName
                Patient: $patientName
                Dosage: $dosage
                Time: $slot
                
                Please ensure the medicine is taken.
                """.trimIndent()
            }
            "LOW_STOCK" -> {
                """
                📦 MedMonitor Alert
                
                Low Medicine Stock
                
                Medicine: $medicineName
                Remaining: ${remainingQuantity.toInt()}
                Approx Days Left: $daysLeft
                
                Please refill soon.
                """.trimIndent()
            }
            "OUT_OF_STOCK" -> {
                """
                🚨 MedMonitor Alert: STOCK DEPLETED
                
                Patient: $patientName
                Medicine: $medicineName
                
                Patient could not take medicine because inventory is depleted. Please refill medicine.
                """.trimIndent()
            }
            else -> {
                """
                ⚠️ MedMonitor Alert
                
                Missed Medication: $medicineName
                Patient: $patientName
                Scheduled Time: $slot
                
                Please check on the patient.
                """.trimIndent()
            }
        }
    }

    /**
     * 🧩 STEP B2: Clean send layer.
     * 🧩 STEP B3: Legacy safety fallback for 10-digit numbers.
     */
    fun sendSMS(context: Context, phone: String, message: String) {
        if (phone.isBlank()) return

        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.e("SMS", "Permission NOT granted")
            return
        }

        try {
            // Sanitize: remove spaces/dashes
            var cleanPhone = phone.replace(Regex("[\\s\\-\\(\\)]"), "").trim()
            
            // Legacy Fallback: If 10 digits, assume +91 (production safety for existing Indian users)
            if (cleanPhone.length == 10 && !cleanPhone.startsWith("+")) {
                cleanPhone = "+91$cleanPhone"
            }

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(cleanPhone, null, parts, null, null)

            Log.d("SMS", "SMS triggered successfully to $cleanPhone (Parts: ${parts.size})")

        } catch (e: Exception) {
            Log.e("SMS", "SMS failed to $phone: ${e.message}")
        }
    }
}
