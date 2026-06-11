package com.medmonitor.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.medmonitor.data.model.*
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    private val usersCollection = db.collection("Users")
    private val medicinesCollection = db.collection("Medicines")
    private val doseHistoryCollection = db.collection("DoseHistory")
    private val familyMembersCollection = db.collection("FamilyMembers")
    private val notificationsCollection = db.collection("Notifications")

    // Medicine CRUD
    suspend fun addMedicine(medicine: Medicine): Boolean {
        return try {
            val docRef = medicinesCollection.document()
            val newMed = medicine.copy(id = docRef.id, userId = getCurrentUserId() ?: "")
            docRef.set(newMed).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getMedicines(): List<Medicine> {
        return try {
            val snapshot = medicinesCollection
                .whereEqualTo("userId", getCurrentUserId())
                .get()
                .await()
            snapshot.toObjects(Medicine::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteMedicine(medicineId: String): Boolean {
        return try {
            medicinesCollection.document(medicineId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    // Dose History
    suspend fun addDoseHistory(history: DoseHistory): Boolean {
        return try {
            val docRef = doseHistoryCollection.document()
            val newHistory = history.copy(id = docRef.id, userId = getCurrentUserId() ?: "")
            docRef.set(newHistory).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getDoseHistory(): List<DoseHistory> {
        return try {
            val snapshot = doseHistoryCollection
                .whereEqualTo("userId", getCurrentUserId())
                .get()
                .await()
            snapshot.toObjects(DoseHistory::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Family Members
    suspend fun addFamilyMember(member: FamilyMember): Boolean {
        return try {
            val docRef = familyMembersCollection.document()
            val newMember = member.copy(id = docRef.id, userId = getCurrentUserId() ?: "")
            docRef.set(newMember).await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
