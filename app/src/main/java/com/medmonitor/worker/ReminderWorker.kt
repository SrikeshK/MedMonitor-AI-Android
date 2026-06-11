package com.medmonitor.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medmonitor.MedMonitorApplication
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.ui.medicine.DoseConfirmationActivity
import com.medmonitor.util.SoundUtil

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val medicineId = inputData.getString("MEDICINE_ID") ?: ""
        val medicineName = inputData.getString("MEDICINE_NAME") ?: "Medicine"
        val doseAmount = inputData.getDouble("DOSE_AMOUNT", 0.0)
        
        showNotification(medicineId, medicineName, "It's time to take $doseAmount doses of $medicineName.")
        
        return Result.success()
    }

    private fun showNotification(medicineId: String, medicineName: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = MedMonitorApplication.CHANNEL_ID
        val settingsManager = SettingsManager(applicationContext)

        // Note: Channel creation is handled in MedMonitorApplication.onCreate() 
        // to ensure it stays in sync with user settings.

        val intent = Intent(applicationContext, DoseConfirmationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // STEP 2 — FIX DATA PASSING
            putExtra("medicine_id", medicineId)
            putExtra("medicine_name", medicineName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            medicineId.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = SoundUtil.getSoundUri(applicationContext, settingsManager.notificationSound)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(medicineName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setAutoCancel(true)

        if (!settingsManager.vibrationEnabled) {
            notification.setVibrate(longArrayOf(0L))
        }

        notificationManager.notify(medicineId.hashCode(), notification.build())
    }
}
