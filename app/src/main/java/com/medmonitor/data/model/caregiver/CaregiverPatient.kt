package com.medmonitor.data.model.caregiver

import com.google.firebase.Timestamp

data class CaregiverPatient(
    val id: String = "",
    val caregiverId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val phoneNumber: String = "",
    val age: Int = 0,
    val gender: String = "",
    val relation: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
