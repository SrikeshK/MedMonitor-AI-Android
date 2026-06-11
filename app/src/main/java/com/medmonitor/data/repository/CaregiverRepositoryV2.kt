package com.medmonitor.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.medmonitor.data.model.caregiver.CaregiverAlertLog
import com.medmonitor.data.model.caregiver.CaregiverMedicine
import com.medmonitor.data.model.caregiver.CaregiverPatient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CaregiverRepositoryV2 {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId get() = auth.currentUser?.uid ?: ""

    private val caregiverPatients = firestore.collection("caregiver_patients")
    private val caregiverMedicines = firestore.collection("caregiver_medicines")
    private val caregiverAlertLogs = firestore.collection("caregiver_alert_logs")

    suspend fun addCaregiverPatient(patient: CaregiverPatient) {
        if (currentUserId.isEmpty()) return
        val docRef = caregiverPatients.document()
        
        // 🧩 Search for real patient UID by phone number (Phase 2)
        val realPatientUid = try {
            firestore.collection("Users")
                .whereEqualTo("phoneNumber", patient.phoneNumber)
                .get()
                .await()
                .documents
                .firstOrNull()?.id
        } catch (e: Exception) {
            null
        }

        // Ensure patientId is set to the REAL patient UID if found.
        // Fallback to docRef.id if real UID is unavailable to avoid blocking (Phase 3)
        val patientWithId = patient.copy(
            id = docRef.id, 
            patientId = realPatientUid ?: docRef.id,
            caregiverId = currentUserId
        )
        docRef.set(patientWithId).await()
    }

    fun getCaregiverPatients(): Flow<List<CaregiverPatient>> {
        if (currentUserId.isEmpty()) return flowOf(emptyList())
        return caregiverPatients
            .whereEqualTo("caregiverId", currentUserId)
            .snapshots()
            .map { it.toObjects(CaregiverPatient::class.java) }
    }

    suspend fun getCaregiverPatientByPatientId(patientId: String): CaregiverPatient? {
        return try {
            caregiverPatients
                .whereEqualTo("patientId", patientId)
                .get()
                .await()
                .toObjects(CaregiverPatient::class.java)
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addCaregiverMedicine(medicine: CaregiverMedicine) {
        if (currentUserId.isEmpty()) return
        val docRef = caregiverMedicines.document()
        val medicineWithId = medicine.copy(id = docRef.id, caregiverId = currentUserId)
        docRef.set(medicineWithId).await()
    }

    suspend fun updateCaregiverMedicine(medicine: CaregiverMedicine) {
        if (medicine.id.isEmpty()) return
        
        // SAFE PRESERVATION: Fetch existing document to prevent losing caregiverId, patientId, or createdAt
        val existingDoc = caregiverMedicines.document(medicine.id).get().await()
        val existingMedicine = existingDoc.toObject(CaregiverMedicine::class.java)
        
        val medicineToSave = medicine.copy(
            caregiverId = existingMedicine?.caregiverId?.takeIf { it.isNotEmpty() } ?: currentUserId,
            patientId = existingMedicine?.patientId?.takeIf { it.isNotEmpty() } ?: medicine.patientId,
            createdAt = existingMedicine?.createdAt ?: medicine.createdAt
        )
        
        caregiverMedicines.document(medicine.id).set(medicineToSave).await()
    }

    suspend fun deleteCaregiverMedicine(medicineId: String) {
        if (medicineId.isEmpty()) return
        caregiverMedicines.document(medicineId).delete().await()
    }

    fun getCaregiverMedicines(patientId: String): Flow<List<CaregiverMedicine>> {
        return caregiverMedicines
            .whereEqualTo("patientId", patientId)
            .snapshots()
            .map { it.toObjects(CaregiverMedicine::class.java) }
    }

    fun getAllCaregiverMedicines(): Flow<List<CaregiverMedicine>> {
        if (currentUserId.isEmpty()) return flowOf(emptyList())
        return caregiverMedicines
            .whereEqualTo("caregiverId", currentUserId)
            .snapshots()
            .map { it.toObjects(CaregiverMedicine::class.java) }
    }

    suspend fun addCaregiverAlertLog(log: CaregiverAlertLog) {
        val docRef = caregiverAlertLogs.document()
        val logWithId = if (log.caregiverId.isEmpty()) {
            log.copy(id = docRef.id, caregiverId = currentUserId)
        } else {
            log.copy(id = docRef.id)
        }
        docRef.set(logWithId).await()
    }

    fun getCaregiverAlertLogs(): Flow<List<CaregiverAlertLog>> {
        if (currentUserId.isEmpty()) return flowOf(emptyList())
        return caregiverAlertLogs
            .whereEqualTo("caregiverId", currentUserId)
            .snapshots()
            .map { it.toObjects(CaregiverAlertLog::class.java) }
    }
}
