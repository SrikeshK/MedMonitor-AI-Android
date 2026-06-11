package com.medmonitor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.medmonitor.data.SettingsManager
import com.medmonitor.util.SoundUtil
import com.medmonitor.worker.AlarmResyncWorker
import java.util.concurrent.TimeUnit

class MedMonitorApplication : Application() {
    
    companion object {
        const val CHANNEL_ID = "med_channel"
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
        private const val TAG = "MedMonitorApp"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Initializing MedMonitorApplication")
        
        FirebaseApp.initializeApp(this)
        
        // STEP 3: Verify all sounds on startup
        SoundUtil.verifyAllSounds(this)
        
        updateNotificationChannel()
        createDownloadNotificationChannel()
        
        // 🧩 FAILSAFE DAILY RESCHEDULER
        scheduleAlarmResync()
    }

    private fun scheduleAlarmResync() {
        val workRequest = PeriodicWorkRequestBuilder<AlarmResyncWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "alarm_resync_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d(TAG, "AlarmResyncWorker scheduled")
    }

    fun updateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val settingsManager = SettingsManager(this)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Delete existing channel to ensure sound/vibration updates are applied
            notificationManager.deleteNotificationChannel("medicine_reminders")
            notificationManager.deleteNotificationChannel(CHANNEL_ID)

            val name = "Medicine Alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = "Notifications for scheduled medicines"
                
                // Sound configuration - Using safe utility
                val soundUri = SoundUtil.getSoundUri(this@MedMonitorApplication, settingsManager.notificationSound)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
                Log.d(TAG, "Notification channel sound set to: $soundUri")

                // Vibration configuration
                enableVibration(settingsManager.vibrationEnabled)
                if (!settingsManager.vibrationEnabled) {
                    vibrationPattern = longArrayOf(0L)
                }
                Log.d(TAG, "Notification channel vibration enabled: ${settingsManager.vibrationEnabled}")
            }
            
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel recreated successfully")
        }
    }

    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Report Downloads"
            val descriptionText = "Notifications for generated health reports"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(DOWNLOAD_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
