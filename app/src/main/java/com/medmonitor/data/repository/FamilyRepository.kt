package com.medmonitor.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.medmonitor.data.model.FamilyMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FamilyRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val userId get() = auth.currentUser?.uid ?: ""

    private val familyCollection = firestore.collection("family_members")

    suspend fun addFamilyMember(member: FamilyMember) {
        val docRef = familyCollection.document()
        val memberWithId = member.copy(id = docRef.id, userId = userId)
        docRef.set(memberWithId).await()
    }

    suspend fun updateFamilyMember(member: FamilyMember) {
        familyCollection.document(member.id).set(member).await()
    }

    suspend fun deleteFamilyMember(memberId: String) {
        familyCollection.document(memberId).delete().await()
    }

    fun getFamilyMembers(): Flow<List<FamilyMember>> = familyCollection
        .whereEqualTo("userId", userId)
        .snapshots()
        .map { snapshot -> snapshot.toObjects(FamilyMember::class.java) }
}
