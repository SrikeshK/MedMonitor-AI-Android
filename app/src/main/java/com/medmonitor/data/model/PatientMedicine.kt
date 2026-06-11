package com.medmonitor.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class PatientMedicine(
    @DocumentId val medicineId: String = "",
    val patientId: String = "",
    val caregiverId: String = "",
    val name: String = "",
    val dosage: String = "",
    val foodTiming: String = "Anytime",
    val scheduleTimes: Map<String, String> = emptyMap(), // Changed to String for exact times
    val duration: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val durationDays: Int = 0,
    val isActive: Boolean = true,
    val type: String = "single",
    val createdAt: Timestamp = Timestamp.now(),
    // SAFE OWNERSHIP METADATA
    val reminderOwner: String = "CAREGIVER"
)
