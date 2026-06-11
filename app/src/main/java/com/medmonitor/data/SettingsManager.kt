package com.medmonitor.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.medmonitor.data.model.DoseLog

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "medmonitor_settings"
        
        // Onboarding & Auth
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_USER_MODE = "user_mode"
        
        // Reminders & Notifications
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_SNOOZE_DURATION = "snooze_duration"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_MISSED_DOSE_DELAY = "missed_delay"
        private const val KEY_NOTIFICATION_SOUND = "notification_sound"

        // Stock Alerts
        private const val KEY_STOCK_ALERTS_ENABLED = "stock_alerts_enabled"
        private const val KEY_STOCK_NOTIFY_DEVICE = "stock_notify_device"
        private const val KEY_STOCK_NOTIFY_CAREGIVER = "stock_notify_caregiver"
        
        // Legacy Medication Settings
        private const val KEY_LOW_STOCK_THRESHOLD = "low_stock_threshold"
        private const val KEY_AUTO_REFILL_REMINDER = "auto_refill"
        private const val KEY_UNIT_TYPE = "unit_type"

        // Care Circle Settings
        private const val KEY_NOTIFY_IMMEDIATELY = "notify_immediate"
        private const val KEY_NOTIFY_AFTER_DELAY = "notify_delay"
        private const val KEY_NOTIFICATION_TYPES = "notification_types"
        private const val KEY_CAREGIVER_PHONE = "caregiver_phone"
        private const val KEY_CAREGIVER_EMAIL = "caregiver_email"
        private const val KEY_CACHED_CAREGIVERS = "cached_caregivers"

        // Data & Analytics
        private const val KEY_WEEKLY_REPORT_ENABLED = "weekly_report_enabled"
        
        // General
        private const val KEY_LANGUAGE = "language"

        // Offline Sync Key
        private const val KEY_OFFLINE_DOSE_QUEUE = "offline_dose_queue"
    }

    data class CachedCaregiver(val name: String, val phone: String)

    private fun getUserScopedKey(key: String): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        return "${uid}_$key"
    }

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    fun setOnboardingComplete(value: Boolean) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    fun getUserMode(): String? = prefs.getString(getUserScopedKey(KEY_USER_MODE), null)
    fun setUserMode(mode: String) = prefs.edit().putString(getUserScopedKey(KEY_USER_MODE), mode).apply()

    fun addPendingDose(doseLog: DoseLog) {
        val queue = getPendingDoses().toMutableList()
        queue.add(doseLog)
        prefs.edit().putString(KEY_OFFLINE_DOSE_QUEUE, gson.toJson(queue)).apply()
    }

    fun getPendingDoses(): List<DoseLog> {
        val json = prefs.getString(KEY_OFFLINE_DOSE_QUEUE, null) ?: return emptyList()
        val type = object : TypeToken<List<DoseLog>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    fun removePendingDose(doseLog: DoseLog) {
        val queue = getPendingDoses().toMutableList()
        queue.removeAll { it.medicineId == doseLog.medicineId && it.slotName == doseLog.slotName && it.timestamp.seconds == doseLog.timestamp.seconds }
        prefs.edit().putString(KEY_OFFLINE_DOSE_QUEUE, gson.toJson(queue)).apply()
    }

    fun clearAll() = prefs.edit().clear().apply()

    fun setCachedCaregivers(caregivers: List<CachedCaregiver>) = prefs.edit().putString(getUserScopedKey(KEY_CACHED_CAREGIVERS), gson.toJson(caregivers)).apply()
    fun getCachedCaregivers(): List<CachedCaregiver> {
        val json = prefs.getString(getUserScopedKey(KEY_CACHED_CAREGIVERS), null) ?: prefs.getString(KEY_CACHED_CAREGIVERS, null) ?: return emptyList()
        val type = object : TypeToken<List<CachedCaregiver>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    // PHASE 5: User-Scoped Settings with Fallback for Safety
    var notificationsEnabled: Boolean
        get() = if (prefs.contains(getUserScopedKey(KEY_NOTIFICATIONS_ENABLED))) {
            prefs.getBoolean(getUserScopedKey(KEY_NOTIFICATIONS_ENABLED), true)
        } else {
            prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        }
        set(value) = prefs.edit().putBoolean(getUserScopedKey(KEY_NOTIFICATIONS_ENABLED), value).apply()

    var snoozeDuration: Int
        get() = if (prefs.contains(getUserScopedKey(KEY_SNOOZE_DURATION))) {
            prefs.getInt(getUserScopedKey(KEY_SNOOZE_DURATION), 10)
        } else {
            prefs.getInt(KEY_SNOOZE_DURATION, 10)
        }
        set(value) = prefs.edit().putInt(getUserScopedKey(KEY_SNOOZE_DURATION), value).apply()

    var vibrationEnabled: Boolean
        get() = if (prefs.contains(getUserScopedKey(KEY_VIBRATION_ENABLED))) {
            prefs.getBoolean(getUserScopedKey(KEY_VIBRATION_ENABLED), true)
        } else {
            prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        }
        set(value) = prefs.edit().putBoolean(getUserScopedKey(KEY_VIBRATION_ENABLED), value).apply()

    var missedDoseDelay: Int
        get() = if (prefs.contains(getUserScopedKey(KEY_MISSED_DOSE_DELAY))) {
            prefs.getInt(getUserScopedKey(KEY_MISSED_DOSE_DELAY), 30)
        } else {
            prefs.getInt(KEY_MISSED_DOSE_DELAY, 30)
        }
        set(value) = prefs.edit().putInt(getUserScopedKey(KEY_MISSED_DOSE_DELAY), value).apply()

    var notificationSound: String
        get() = prefs.getString(getUserScopedKey(KEY_NOTIFICATION_SOUND), null) 
                ?: prefs.getString(KEY_NOTIFICATION_SOUND, "Default") ?: "Default"
        set(value) = prefs.edit().putString(getUserScopedKey(KEY_NOTIFICATION_SOUND), value).apply()

    var stockAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_STOCK_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_STOCK_ALERTS_ENABLED, value).apply()

    var stockNotifyDevice: Boolean
        get() = prefs.getBoolean(KEY_STOCK_NOTIFY_DEVICE, true)
        set(value) = prefs.edit().putBoolean(KEY_STOCK_NOTIFY_DEVICE, value).apply()

    var stockNotifyCaregiver: Boolean
        get() = prefs.getBoolean(KEY_STOCK_NOTIFY_CAREGIVER, false)
        set(value) = prefs.edit().putBoolean(KEY_STOCK_NOTIFY_CAREGIVER, value).apply()

    var lowStockThreshold: Int
        get() = prefs.getInt(KEY_LOW_STOCK_THRESHOLD, 5)
        set(value) = prefs.edit().putInt(KEY_LOW_STOCK_THRESHOLD, value).apply()

    var autoRefillReminder: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REFILL_REMINDER, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_REFILL_REMINDER, value).apply()

    var unitType: String
        get() = prefs.getString(KEY_UNIT_TYPE, "Tablet") ?: "Tablet"
        set(value) = prefs.edit().putString(KEY_UNIT_TYPE, value).apply()

    var notifyImmediately: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_IMMEDIATELY, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_IMMEDIATELY, value).apply()

    var notifyAfterDelay: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_AFTER_DELAY, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY_AFTER_DELAY, value).apply()

    var notificationTypes: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFICATION_TYPES, setOf("SMS")) ?: setOf("SMS")
        set(value) = prefs.edit().putStringSet(KEY_NOTIFICATION_TYPES, value).apply()

    var caregiverPhone: String
        get() = prefs.getString(getUserScopedKey(KEY_CAREGIVER_PHONE), null) ?: prefs.getString(KEY_CAREGIVER_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(getUserScopedKey(KEY_CAREGIVER_PHONE), value).apply()

    var caregiverEmail: String
        get() = prefs.getString(getUserScopedKey(KEY_CAREGIVER_EMAIL), null) ?: prefs.getString(KEY_CAREGIVER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(getUserScopedKey(KEY_CAREGIVER_EMAIL), value).apply()

    var weeklyReportEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEEKLY_REPORT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WEEKLY_REPORT_ENABLED, value).apply()

    var appLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, "English") ?: "English"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()
}
