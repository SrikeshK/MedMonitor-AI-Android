package com.medmonitor.data.model.caregiver

import com.google.firebase.Timestamp

data class CaregiverMedicine(
    val id: String = "",
    val caregiverId: String = "",
    val patientId: String = "",
    val medicineName: String = "",
    val dosage: String = "",
    val scheduleTimes: Map<String, String> = emptyMap(),
    val instructions: String = "",
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now()
)
