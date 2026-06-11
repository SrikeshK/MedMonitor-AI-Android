package com.medmonitor.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.medmonitor.data.model.Patient
import com.medmonitor.data.model.PatientMedicine
import com.medmonitor.data.model.PatientDoseLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CaregiverRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUser get() = auth.currentUser

    private val patientsCollection = firestore.collection("patients")
    private val patientMedicinesCollection = firestore.collection("patient_medicines")
    private val patientLogsCollection = firestore.collection("patient_logs")

    /**
     * 🧩 STEP B1: Normalize phone number ONCE during save.
     */
    private fun normalizePhone(phone: String): String {
        val sanitized = phone.replace(Regex("[\\s\\-\\(\\)]"), "").trim()
        if (sanitized.startsWith("+")) return sanitized
        if (sanitized.length == 10) return "+91$sanitized"
        return sanitized
    }

    suspend fun addPatient(patient: Patient) {
        val caregiverId = currentUser?.uid ?: return
        
        val docRef = patientsCollection.document()
        val normalizedPhone = normalizePhone(patient.phone)
        val patientWithId = patient.copy(
            id = docRef.id, 
            caregiverId = caregiverId,
            phone = normalizedPhone
        )
        
        Log.d("CARE_DEBUG", "Saving patient with normalized phone: $patientWithId")
        docRef.set(patientWithId).await()
    }

    fun getPatients(): Flow<List<Patient>> {
        val caregiverId = currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return patientsCollection
            .whereEqualTo("caregiverId", caregiverId)
            .snapshots()
            .map { it.toObjects(Patient::class.java) }
    }
    
    suspend fun getPatientById(patientId: String): Patient? {
        return try {
            patientsCollection.document(patientId).get().await().toObject(Patient::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addMedicineToPatient(medicine: PatientMedicine) {
        val caregiverId = currentUser?.uid ?: return
        val docRef = patientMedicinesCollection.document()
        val medicineWithId = medicine.copy(medicineId = docRef.id, caregiverId = caregiverId)
        docRef.set(medicineWithId).await()
    }

    fun getPatientMedicines(patientId: String): Flow<List<PatientMedicine>> {
        return patientMedicinesCollection
            .whereEqualTo("patientId", patientId)
            .snapshots()
            .map { it.toObjects(PatientMedicine::class.java) }
    }

    suspend fun recordPatientDose(log: PatientDoseLog) {
        val docRef = patientLogsCollection.document()
        val logWithId = log.copy(logId = docRef.id)
        docRef.set(logWithId).await()
    }

    fun getPatientLogs(patientId: String): Flow<List<PatientDoseLog>> {
        return patientLogsCollection
            .whereEqualTo("patientId", patientId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(PatientDoseLog::class.java) }
    }

    fun getAllCaregiverLogs(): Flow<List<PatientDoseLog>> {
        val caregiverId = currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        // 🧩 SAFE FIX: Filter by caregiverId to ensure isolation
        return patientLogsCollection
            .whereEqualTo("caregiverId", caregiverId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { it.toObjects(PatientDoseLog::class.java) }
    }

    suspend fun deletePatient(patientId: String) {
        try {
            val batch = firestore.batch()

            // 1. Delete patient document
            batch.delete(patientsCollection.document(patientId))

            // 2. Delete patient medicines
            val medicines = patientMedicinesCollection.whereEqualTo("patientId", patientId).get().await()
            medicines.documents.forEach { batch.delete(it.reference) }

            // 3. Delete patient logs
            val logs = patientLogsCollection.whereEqualTo("patientId", patientId).get().await()
            logs.documents.forEach { batch.delete(it.reference) }

            batch.commit().await()
        } catch (e: Exception) {
            Log.e("CARE_REPO", "Error deleting patient: ${e.message}")
        }
    }
}
