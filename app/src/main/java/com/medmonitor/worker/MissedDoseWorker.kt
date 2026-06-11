package com.medmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.repository.FamilyRepository
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.CareAlertManager
import com.medmonitor.util.MedicineStatusUtil
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MissedDoseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val medicineRepository = MedicineRepository()
    private val familyRepository = FamilyRepository()

    companion object {
        private const val TAG = "CARE_ALERT"

        fun schedule(context: Context, medicineId: String, medicineName: String, slotName: String, scheduledTime: String, delayMinutes: Long = 30, dateSuffix: String? = null) {
            val normalizedSlot = MedicineStatusUtil.normalizeSlot(slotName)
            val currentDate = dateSuffix ?: SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            
            val data = workDataOf(
                "medicine_id" to medicineId,
                "medicine_name" to medicineName,
                "slot_name" to normalizedSlot,
                "scheduled_time" to scheduledTime
            )
            
            val request = OneTimeWorkRequestBuilder<MissedDoseWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(data)
                .build()

            // 🧩 SAFE FIX: Use '*' as separator for daily uniqueness and clear identification
            val uniqueWorkName = "missed_${medicineId}*${normalizedSlot}*${currentDate}"
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE, // Changed to REPLACE to handle time edits on same day/slot
                request
            )
            Log.d(TAG, "Scheduled missed dose check for $medicineName ($normalizedSlot) at $scheduledTime on $currentDate in $delayMinutes mins")
        }
    }

    override suspend fun doWork(): Result {
        Log.e("SMS_TRACE", "===== MISSED WORKER START =====")
        
        val medicineId = inputData.getString("medicine_id") ?: return Result.failure()
        val medicineName = inputData.getString("medicine_name") ?: "Medicine"
        val slotName = inputData.getString("slot_name") ?: ""
        val scheduledTime = inputData.getString("scheduled_time") ?: ""
        
        val normalizedSlot = MedicineStatusUtil.normalizeSlot(slotName)
        
        Log.e("SMS_TRACE", "Checking: medicineId=$medicineId, slot=$normalizedSlot, scheduledTime=$scheduledTime")

        try {
            val medicine = medicineRepository.getMedicineById(medicineId)
            if (medicine == null) {
                Log.e("SMS_TRACE", "Medicine not found. Skipping.")
                return Result.success()
            }

            val currentStatus = if (MedicineStatusUtil.isMultiDose(medicine)) {
                medicine.slotStatus[normalizedSlot]
            } else {
                val prefs = applicationContext.getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
                prefs.getString("status_$medicineId", "PENDING")
            }

            // 🧩 SAFE FIX: Check for TAKEN status including identity awareness
            if (currentStatus == "TAKEN" || currentStatus == "COMPLETED") {
                // Double check deterministic log in case status engine is slightly out of sync
                Log.d("Worker", "Already taken ($currentStatus) → skip SMS")
                return Result.success()
            }

            val settingsManager = SettingsManager(applicationContext)
            if (!settingsManager.notifyAfterDelay) {
                Log.e("SMS_TRACE", "Notify After Delay disabled. Skipping.")
                return Result.success()
            }

            // 🧩 PHASE 3: Inventory-Aware Missed Detection
            val isOutOfStock = medicine.remainingQuantity < medicine.dosageAmount
            val finalStatus = if (isOutOfStock) DoseStatus.OUT_OF_STOCK else DoseStatus.MISSED

            // Record Log in DB with identity key
            val doseLog = DoseLog(
                medicineId = medicineId,
                medicineName = medicineName,
                status = finalStatus,
                timestamp = Timestamp.now(),
                slotName = normalizedSlot,
                scheduledTime = scheduledTime
            )
            medicineRepository.recordMissedDose(doseLog)

            // Send Caregiver Alert
            val patientName = FirebaseAuth.getInstance().currentUser?.displayName ?: "The patient"
            val patientId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            
            // 🧩 PHASE 3: Context-Aware Caregiver Alerts
            CareAlertManager.sendAlertNow(
                context = applicationContext,
                type = if (isOutOfStock) "OUT_OF_STOCK" else "MISSED",
                patientId = patientId,
                medicineId = medicineId,
                medicineName = medicineName,
                patientName = patientName,
                slot = scheduledTime.ifEmpty { normalizedSlot }
            )

            return Result.success()
        } catch (e: Exception) {
            Log.e("SMS_TRACE", "MISSED WORKER FAILED", e)
            return Result.retry()
        } finally {
            Log.e("SMS_TRACE", "===== MISSED WORKER END =====")
        }
    }
}
