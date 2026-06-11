package com.medmonitor.ui.caregiver.model

import com.google.firebase.Timestamp

data class CaregiverTimelineItem(
    val patientId: String = "",
    val patientName: String = "",
    val medicineId: String = "",
    val medicineName: String = "",
    val dosage: String = "",
    val scheduledTime: String = "",
    val status: String = "", // UPCOMING, DUE_NOW, TAKEN, MISSED
    val timestamp: Timestamp? = null,
    val phoneNumber: String = ""
)
