package com.medmonitor.data.model

import com.google.firebase.firestore.DocumentId

data class FamilyMember(
    @DocumentId val id: String = "",
    val name: String = "",
    val relation: String = "",
    val phone: String = "",
    val email: String = "",
    val backupPhone: String = "",
    val status: String = "Unknown", // Active, Not reachable, Unknown
    val lastAlertTime: Long? = null,
    val notifyAfterMissedDose: Boolean = true,
    val notifyImmediately: Boolean = false,
    val userId: String = ""
)
