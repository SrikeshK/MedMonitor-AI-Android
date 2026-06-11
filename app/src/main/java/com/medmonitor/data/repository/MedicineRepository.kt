package com.medmonitor.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.caregiver.CaregiverAlertLog
import com.medmonitor.util.AlertManager
import com.medmonitor.util.CareAlertManager
import com.medmonitor.util.calculateDaysLeft
import com.medmonitor.util.getInventoryState
import com.medmonitor.util.InventoryState
import com.medmonitor.util.checkLowStock
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.NetworkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

class MedicineRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId get() = auth.currentUser?.uid ?: ""

    private val medicinesCollection = firestore.collection("Medicines")
    private val logsCollection = firestore.collection("dose_logs")

    suspend fun addMedicine(medicine: Medicine): String {
        val medicineId = medicine.id.trim()
        val docRef = if (medicineId.isEmpty()) {
            medicinesCollection.document()
        } else {
            medicinesCollection.document(medicineId)
        }
        
        val normalizedSlotStatus = medicine.slotStatus.mapKeys { MedicineStatusUtil.normalizeSlot(it.key) }
        val normalizedScheduleTimes = medicine.scheduleTimes.mapKeys { MedicineStatusUtil.normalizeSlot(it.key) }
        
        val medicineWithId = medicine.copy(
            id = docRef.id, 
            userId = userId,
            slotStatus = normalizedSlotStatus,
            scheduleTimes = normalizedScheduleTimes
        )
        docRef.set(medicineWithId).await()
        return docRef.id
    }
    
    suspend fun updateMedicine(medicine: Medicine) {
        if (medicine.id.isNotEmpty()) {
            val medicineRef = medicinesCollection.document(medicine.id)

            val oldMedSnapshot = try {
                medicineRef.get().await()
            } catch (e: Exception) { null }
            val oldMed = oldMedSnapshot?.toObject(Medicine::class.java)?.normalize()
            
            val shouldResetAlert = oldMed != null && medicine.remainingQuantity > oldMed.remainingQuantity

            val normalizedSlotStatus = medicine.slotStatus.mapKeys { MedicineStatusUtil.normalizeSlot(it.key) }
            val normalizedScheduleTimes = medicine.scheduleTimes.mapKeys { MedicineStatusUtil.normalizeSlot(it.key) }

            val updates = mutableMapOf<String, Any?>(
                "name" to medicine.name,
                "type" to medicine.type.name,
                "dosageAmount" to medicine.dosageAmount,
                "totalQuantity" to medicine.totalQuantity,
                "remainingQuantity" to medicine.remainingQuantity,
                "unit" to medicine.unit,
                "frequency" to medicine.frequency,
                "foodTiming" to medicine.foodTiming,
                "scheduleTimes" to normalizedScheduleTimes,
                "scheduleSlots" to medicine.scheduleSlots,
                "startDate" to medicine.startDate,
                "endDate" to medicine.endDate,
                "threshold" to medicine.threshold,
                "dosagePerDay" to medicine.dosagePerDay,
                "scheduledTime" to medicine.scheduledTime,
                "imageUrl" to medicine.imageUrl,
                "slotStatus" to normalizedSlotStatus,
                "lastUpdatedTime" to medicine.lastUpdatedTime,
                "reminderOwner" to medicine.reminderOwner
            )

            if (shouldResetAlert) {
                updates["lowStockAlertSent"] = false
                updates["criticalAlertSent"] = false
            }

            medicineRef.update(updates).await()
        }
    }

    suspend fun refillMedicine(medicineId: String, refillAmount: Double) {
        if (refillAmount <= 0) return
        
        val medicineRef = medicinesCollection.document(medicineId)
        val updates = mapOf(
            "remainingQuantity" to FieldValue.increment(refillAmount),
            "totalQuantity" to FieldValue.increment(refillAmount),
            "lowStockAlertSent" to false,
            "criticalAlertSent" to false
        )
        
        medicineRef.update(updates).await()
    }

    suspend fun updateThreshold(medicineId: String, newThreshold: Double) {
        val trimmedId = medicineId.trim()
        if (trimmedId.isNotEmpty()) {
            medicinesCollection.document(trimmedId).update("threshold", newThreshold).await()
        }
    }

    suspend fun deleteMedicine(medicineId: String) {
        val trimmedId = medicineId.trim()
        if (trimmedId.isNotEmpty()) {
            medicinesCollection.document(trimmedId).delete().await()
        }
    }

    suspend fun getMedicineById(medicineId: String): Medicine? {
        val trimmedId = medicineId.trim()
        if (trimmedId.isEmpty()) return null
        return try {
            medicinesCollection.document(trimmedId).get().await().toObject(Medicine::class.java)?.normalize()
        } catch (e: Exception) {
            null
        }
    }

    fun getMedicines(uid: String? = null): Flow<List<Medicine>> {
        val targetUid = uid ?: userId
        if (targetUid.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        
        return medicinesCollection
            .whereEqualTo("userId", targetUid)
            .snapshots()
            .map { snapshot -> 
                snapshot.toObjects(Medicine::class.java).mapNotNull { 
                    try { it.normalize() } catch (e: Exception) { null }
                }
            }
    }

    suspend fun getAllMedicinesOnce(uid: String? = null): List<Medicine> {
        return getMedicines(uid).first()
    }

    suspend fun getAllMedicines(uid: String? = null): List<Medicine> {
        val targetUid = uid ?: userId
        if (targetUid.isEmpty()) return emptyList()
        return try {
            medicinesCollection
                .whereEqualTo("userId", targetUid)
                .get()
                .await()
                .toObjects(Medicine::class.java)
                .mapNotNull { 
                    try { it.normalize() } catch (e: Exception) { null }
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun syncPendingDoses(context: Context) {
        if (!NetworkUtil.isNetworkAvailable(context)) return
        
        val settings = SettingsManager(context)
        val pendingDoses = settings.getPendingDoses()
        
        if (pendingDoses.isEmpty()) return
        
        Log.d("SYNC", "Starting sync for ${pendingDoses.size} doses")
        
        pendingDoses.forEach { dose ->
            try {
                val result = recordDose(dose, context)
                if (result == "SUCCESS" || result == "ALREADY_TAKEN") {
                    settings.removePendingDose(dose)
                    Log.d("SYNC", "Synced dose for ${dose.medicineName}")
                }
            } catch (e: Exception) {
                if (e.message == "OUT_OF_STOCK") {
                    Log.e("SYNC", "Cannot sync dose: OUT_OF_STOCK for ${dose.medicineName}")
                    settings.removePendingDose(dose)
                }
            }
        }
    }

    suspend fun recordDose(doseLog: DoseLog, context: Context? = null): String {
        val trimmedId = doseLog.medicineId.trim()
        if (trimmedId.isEmpty()) return "NOT_FOUND"

        val medicineRef = medicinesCollection.document(trimmedId)
        val normalizedSlot = MedicineStatusUtil.normalizeSlot(doseLog.slotName)
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val slotPart = if (normalizedSlot.isEmpty()) "DEFAULT" else normalizedSlot
        
        var medicineForAlert: Medicine? = null
        var alertType: String? = null 

        val result = try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(medicineRef)
                val rawMedicine = snapshot.toObject(Medicine::class.java) 
                    ?: return@runTransaction "NOT_FOUND"
                
                val medicine = rawMedicine.normalize()

                if (medicine.remainingQuantity < medicine.dosageAmount) {
                    throw IllegalStateException("OUT_OF_STOCK")
                }

                val scheduledTimeStr = if (doseLog.scheduledTime.isNotEmpty()) {
                    doseLog.scheduledTime
                } else if (MedicineStatusUtil.isMultiDose(medicine)) {
                    medicine.scheduleTimes[normalizedSlot] ?: "00:00"
                } else {
                    MedicineStatusUtil.formatTime(medicine.scheduledTime)
                }
                val safeTimeKey = scheduledTimeStr.replace(":", "").replace(" ", "").uppercase()
                val deterministicLogId = "${trimmedId}_${slotPart}_${safeTimeKey}_$dateStr"
                val logRef = logsCollection.document(deterministicLogId)

                val now = System.currentTimeMillis()
                val isToday = MedicineStatusUtil.isSameDay(medicine.lastUpdatedTime, now)

                val existingLogSnapshot = transaction.get(logRef)
                val existingLog = DoseLog.fromSnapshot(existingLogSnapshot)
                
                if (existingLog?.status == DoseStatus.TAKEN) {
                    return@runTransaction "ALREADY_TAKEN"
                }

                if (MedicineStatusUtil.isMultiDose(medicine)) {
                    if (medicine.slotStatus[normalizedSlot] == "TAKEN" && isToday) {
                        return@runTransaction "ALREADY_TAKEN"
                    }
                } else if (medicine.isCompleted && isToday) {
                    return@runTransaction "ALREADY_TAKEN"
                }

                val newRemaining = (medicine.remainingQuantity - medicine.dosageAmount).coerceAtLeast(0.0)
                transaction.update(medicineRef, "remainingQuantity", newRemaining)
                transaction.update(medicineRef, "lastUpdatedTime", now)
                
                if (MedicineStatusUtil.isMultiDose(medicine) && normalizedSlot.isNotEmpty()) {
                    val updatedSlots = medicine.slotStatus.toMutableMap()
                    updatedSlots[normalizedSlot] = "TAKEN"
                    transaction.update(medicineRef, "slotStatus", updatedSlots as Map<String, Any>)
                } else {
                    transaction.update(medicineRef, "isCompleted", true)
                    transaction.update(medicineRef, "status", "COMPLETED")
                }
                transaction.update(medicineRef, "completedTime", now)

                // 🧩 PHASE 3 SAFE FIX: Use logUserId fallback
                val logUserId = medicine.userId.ifEmpty { auth.currentUser?.uid ?: "" }
                val logData = mapOf(
                    "medicineId" to trimmedId,
                    "medicineName" to doseLog.medicineName,
                    "timestamp" to Timestamp(Date(now)),
                    "status" to DoseStatus.TAKEN.name, 
                    "userId" to logUserId, 
                    "verificationMethod" to doseLog.verificationMethod.name,
                    "slotName" to normalizedSlot,
                    "scheduledTime" to scheduledTimeStr
                )
                
                Log.d("ANALYTICS_DEBUG", "Recording dose: userId=$logUserId, medicineId=$trimmedId, status=TAKEN")
                
                transaction.set(logRef, logData)

                val updatedMed = medicine.copy(remainingQuantity = newRemaining)
                val newState = getInventoryState(updatedMed)

                if (newState == InventoryState.CRITICAL && !medicine.criticalAlertSent) {
                    transaction.update(medicineRef, "criticalAlertSent", true)
                    medicineForAlert = updatedMed
                    alertType = "CRITICAL"
                } else if (newState == InventoryState.LOW && !medicine.lowStockAlertSent) {
                    transaction.update(medicineRef, "lowStockAlertSent", true)
                    medicineForAlert = updatedMed
                    alertType = "LOW"
                }
                
                "SUCCESS"
            }.await()
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message == "OUT_OF_STOCK") {
                throw e
            }
            Log.e("REPO", "Transaction failed", e)
            "ERROR"
        }

        if (result == "SUCCESS") {
            withContext(Dispatchers.IO) {
                try {
                    // 🧩 PHASE 2: REDIRECT TO V2
                    val caregiverRepoV2 = CaregiverRepositoryV2()
                    val patientV2 = caregiverRepoV2.getCaregiverPatientByPatientId(userId)
                    
                    if (patientV2 != null) {
                        caregiverRepoV2.addCaregiverAlertLog(
                            CaregiverAlertLog(
                                caregiverId = patientV2.caregiverId,
                                patientId = userId,
                                medicineId = trimmedId,
                                medicineName = doseLog.medicineName,
                                scheduledTime = doseLog.scheduledTime,
                                status = "TAKEN",
                                actionTaken = "PATIENT_RECORDED",
                                actionTimestamp = Timestamp.now(),
                                dateKey = dateStr
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("REPO", "Caregiver log sync failed", e)
                }
            }

            if (alertType != null && context != null) {
                medicineForAlert?.let { alertMed ->
                    withContext(Dispatchers.IO) {
                        try {
                            val days = calculateDaysLeft(alertMed)
                            AlertManager.triggerLowStockNotification(context, alertMed.name, days)
                            
                            if (alertType == "CRITICAL") {
                                AlertManager.notifyCaregiverForCriticalStock(context, alertMed)
                            } else {
                                AlertManager.notifyCaregiverForLowStock(context, alertMed)
                            }
                        } catch (e: Exception) {
                            Log.e("REPO", "Alert side-effect failed", e)
                        }
                    }
                }
            }
        }

        return result
    }

    suspend fun recordMissedDose(doseLog: DoseLog) {
        val trimmedId = doseLog.medicineId.trim()
        if (trimmedId.isEmpty()) return

        val medicineRef = medicinesCollection.document(trimmedId)
        val normalizedSlot = MedicineStatusUtil.normalizeSlot(doseLog.slotName)
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val slotPart = if (normalizedSlot.isEmpty()) "DEFAULT" else normalizedSlot

        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(medicineRef)
                val rawMedicine = snapshot.toObject(Medicine::class.java) ?: return@runTransaction
                val medicine = rawMedicine.normalize()

                val scheduledTimeStr = if (doseLog.scheduledTime.isNotEmpty()) {
                    doseLog.scheduledTime
                } else if (MedicineStatusUtil.isMultiDose(medicine)) {
                    medicine.scheduleTimes[normalizedSlot] ?: "00:00"
                } else {
                    MedicineStatusUtil.formatTime(medicine.scheduledTime)
                }
                val safeTimeKey = scheduledTimeStr.replace(":", "").replace(" ", "").uppercase()
                val deterministicLogId = "${trimmedId}_${slotPart}_${safeTimeKey}_$dateStr"
                val logRef = logsCollection.document(deterministicLogId)

                val existingLogSnapshot = transaction.get(logRef)
                val existingLog = DoseLog.fromSnapshot(existingLogSnapshot)
                
                if (existingLog?.status == DoseStatus.TAKEN) {
                    return@runTransaction
                }

                val now = System.currentTimeMillis()
                // 🧩 PHASE 3 SAFE FIX: Use logUserId fallback
                val logUserId = medicine.userId.ifEmpty { auth.currentUser?.uid ?: "" }
                val logData = mapOf(
                    "medicineId" to trimmedId,
                    "medicineName" to doseLog.medicineName,
                    "timestamp" to Timestamp(Date(now)),
                    "status" to doseLog.status.name, 
                    "userId" to logUserId, 
                    "slotName" to normalizedSlot,
                    "scheduledTime" to scheduledTimeStr
                )
                
                Log.d("ANALYTICS_DEBUG", "Recording missed dose: userId=$logUserId, medicineId=$trimmedId, status=${doseLog.status}")
                
                transaction.set(logRef, logData)
            }.await()
            
            // 🧩 PHASE 3: MISSED DOSE REDIRECTION TO V2
            withContext(Dispatchers.IO) {
                try {
                    val caregiverRepoV2 = CaregiverRepositoryV2()
                    val patientV2 = caregiverRepoV2.getCaregiverPatientByPatientId(userId)
                    if (patientV2 != null) {
                        caregiverRepoV2.addCaregiverAlertLog(
                            CaregiverAlertLog(
                                caregiverId = patientV2.caregiverId,
                                patientId = userId,
                                medicineId = doseLog.medicineId,
                                medicineName = doseLog.medicineName,
                                scheduledTime = doseLog.scheduledTime,
                                status = "MISSED",
                                actionTaken = "SYSTEM_DETECTED",
                                actionTimestamp = Timestamp.now(),
                                dateKey = dateStr
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("REPO", "Missed dose caregiver alert failed", e)
                }
            }
        } catch (e: Exception) {
            Log.e("REPO", "Missed dose log failed", e)
        }
    }

    suspend fun resetTakenStatus(medicineId: String) {
        val trimmedId = medicineId.trim()
        if (trimmedId.isNotEmpty()) {
            medicinesCollection.document(trimmedId).update(
                mapOf(
                    "isCompleted" to false,
                    "status" to "PENDING"
                )
            ).await()
        }
    }

    suspend fun resetSlotStatus(medicineId: String, slots: List<String>) {
        val trimmedId = medicineId.trim()
        if (trimmedId.isNotEmpty()) {
            val emptyStatus = slots.associate { it to "PENDING" }
            medicinesCollection.document(trimmedId).update(
                mapOf(
                    "slotStatus" to emptyStatus,
                    "lastUpdatedTime" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    fun getRecentDoseLogsFlow(uid: String): Flow<List<DoseLog>> {
        if (uid.isEmpty()) {
            Log.w("ANALYTICS_QUERY", "getRecentDoseLogsFlow called with empty UID")
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        
        Log.d("ANALYTICS_DEBUG", "Query UID = $uid")

        val thirtyDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        return logsCollection
            .whereEqualTo("userId", uid)
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(thirtyDaysAgo))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    try {
                        DoseLog.fromSnapshot(doc)
                    } catch (e: Exception) {
                        Log.e("DoseLogParse", "Skipping malformed log: ${doc.id}", e)
                        null
                    }
                }
            }
            .catch { e ->
                // 🧩 PHASE 2: ENSURE FAILED_PRECONDITION remains visible
                Log.e("ANALYTICS_QUERY", "CRITICAL ERROR in dose logs flow for UID: $uid. Message: ${e.message}", e)
                if (e.message?.contains("index") == true || e.message?.contains("FAILED_PRECONDITION") == true) {
                    Log.e("ANALYTICS_QUERY", "Missing composite index (userId, timestamp). Please use the Logcat link to create it.")
                }
                emit(emptyList())
            }
    }
}
