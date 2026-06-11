package com.medmonitor.util

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.MedMonitorApplication
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.Medicine
import com.medmonitor.ui.InventoryActivity

object AlertManager {
    private const val TAG = "AlertManager"

    fun triggerLowStockNotification(context: Context, medicineName: String, days: Int) {
        val settingsManager = SettingsManager(context)
        
        if (!settingsManager.stockAlertsEnabled || !settingsManager.stockNotifyDevice) {
            Log.d(TAG, "Low stock notification suppressed by settings")
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = MedMonitorApplication.CHANNEL_ID

        val intent = Intent(context, InventoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val daysText = when {
            days <= 0 -> "Out of stock"
            days < 1 -> "Less than 1 day"
            else -> "~$days days left"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠ Low Stock: $medicineName")
            .setContentText("$daysText. Please refill soon.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(medicineName.hashCode(), notification)
    }

    fun notifyCaregiverForLowStock(context: Context, medicine: Medicine) {
        val settingsManager = SettingsManager(context)

        if (!settingsManager.stockAlertsEnabled || !settingsManager.stockNotifyCaregiver) {
            Log.d(TAG, "Caregiver low stock alert suppressed by settings")
            return
        }

        val daysLeft = calculateDaysLeft(medicine)
        
        val daysText = when {
            medicine.remainingQuantity <= 0 -> "Out of stock"
            daysLeft < 1 -> "Less than 1 day"
            else -> "~$daysLeft days left"
        }

        val message = """
            Hello,

            The patient's medicine stock is running low.

            Medicine: ${medicine.name}
            Remaining: ${medicine.remainingQuantity.toInt()}
            Estimated: $daysText
            Dosage: ${medicine.dosagePerDay.toInt()} per day

            Please refill the medicine as soon as possible.
        """.trimIndent()
        
        sendToCaregiver(context, settingsManager, "Low Stock Alert: ${medicine.name}", message)
    }

    // 🧩 PHASE 2: SAFE CRITICAL ESCALATION
    fun notifyCaregiverForCriticalStock(context: Context, medicine: Medicine) {
        val settingsManager = SettingsManager(context)

        if (!settingsManager.stockAlertsEnabled || !settingsManager.stockNotifyCaregiver) {
            Log.d(TAG, "Caregiver critical stock alert suppressed by settings")
            return
        }

        val message = """
            Critical refill alert:
            ${medicine.name} has less than 1 day remaining.
            Please refill soon to avoid missed doses.
        """.trimIndent()
        
        sendToCaregiver(context, settingsManager, "CRITICAL Stock Alert: ${medicine.name}", message)
    }

    private fun sendToCaregiver(context: Context, settingsManager: SettingsManager, subject: String, message: String) {
        val caregiverPhone = settingsManager.caregiverPhone
        val caregiverEmail = settingsManager.caregiverEmail
        val notificationTypes = settingsManager.notificationTypes

        if (notificationTypes.contains("SMS") && caregiverPhone.isNotBlank()) {
            sendSms(context, caregiverPhone, message)
        }

        if (notificationTypes.contains("Email") && caregiverEmail.isNotBlank()) {
            sendEmail(context, caregiverEmail, subject, message)
        }
    }

    fun sendCaregiverAlert(
        context: Context,
        caregiverName: String,
        phone: String,
        email: String,
        medicineName: String,
        time: String,
        isDelayed: Boolean
    ) {
        // SMS_TRACE: Entry
        Log.e("SMS_TRACE", "===== ENTERED AlertManager.sendCaregiverAlert =====")
        Log.e("SMS_TRACE", "caregiverName=$caregiverName")
        Log.e("SMS_TRACE", "phone=$phone")
        Log.e("SMS_TRACE", "isDelayed=$isDelayed")

        val patientName = FirebaseAuth.getInstance().currentUser?.displayName ?: "The patient"
        val settingsManager = SettingsManager(context)
        val notificationTypes = settingsManager.notificationTypes
        Log.e("SMS_TRACE", "notificationTypes=$notificationTypes")

        val message = if (isDelayed) {
            "ALERT!\n\n$patientName may have missed their medicine.\n\nMedicine: $medicineName\nScheduled at: $time\n\nPlease check on them."
        } else {
            "Hi $caregiverName,\n\n$patientName has a scheduled medicine now.\n\nMedicine: $medicineName\nTime: $time\n\nPlease ensure they take it."
        }
        
        Log.e("SMS_TRACE", "Built Message:\n$message")

        if (notificationTypes.contains("SMS") && phone.isNotBlank()) {
            Log.e("SMS_TRACE", "SMS type detected. Routing to sendSms...")
            sendSms(context, phone, message)
        } else {
            Log.e("SMS_TRACE", "SMS skipped: Type not in set or phone blank (phone='$phone')")
        }

        if (notificationTypes.contains("Email") && email.isNotBlank()) {
            sendEmail(context, email, "MedMonitor Alert", message)
        }
        Log.e("SMS_TRACE", "===== END AlertManager.sendCaregiverAlert =====")
    }

    private fun sendSms(context: Context, phone: String, message: String): Boolean {
        Log.e("SMS_TRACE", "Preparing to send legacy SMS to: $phone")
        
        if (phone.isEmpty() || phone.length < 10) {
            Log.e("SMS_TRACE", "SMS FAILED: Phone invalid (length=${phone.length})")
            return false
        }
        
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
        Log.e("SMS_TRACE", "Permission = $permission")
        
        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.e("SMS_TRACE", "SMS FAILED: Permission denied")
            return false
        }

        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager?.sendTextMessage(phone, null, message, null, null)
            Log.e("SMS_TRACE", "SMS SENT SUCCESSFULLY (Legacy Engine)")
            true
        } catch (e: Exception) {
            Log.e("SMS_TRACE", "SMS FAILED (Legacy Engine)", e)
            false
        }
    }

    private fun sendEmail(context: Context, email: String, subject: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) { }
    }
}
