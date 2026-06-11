package com.medmonitor.data.model.caregiver

import com.google.firebase.Timestamp

data class CaregiverAlertLog(
    val id: String = "",
    val caregiverId: String = "",
    val patientId: String = "",
    val medicineId: String = "",
    val medicineName: String = "",
    val scheduledTime: String = "",
    val status: String = "UPCOMING", // UPCOMING, DUE_NOW, TAKEN, MISSED
    val actionTaken: String = "",
    val actionTimestamp: Timestamp? = null,
    val dateKey: String = "", // yyyyMMdd
    val createdAt: Timestamp = Timestamp.now()
)
