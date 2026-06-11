package com.medmonitor.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class PatientDoseLog(
    @DocumentId val logId: String = "",
    val patientId: String = "",
    val patientName: String = "Unknown Patient",
    val caregiverId: String = "",
    val medicineId: String = "",
    val medicineName: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "TAKEN", // TAKEN, MISSED
    val slotName: String = ""
)
