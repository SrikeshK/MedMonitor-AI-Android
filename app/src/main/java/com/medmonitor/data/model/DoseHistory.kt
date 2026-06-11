package com.medmonitor.data.model

data class DoseHistory(
    val id: String = "",
    val medicineId: String = "",
    val userId: String = "",
    val scheduledTime: Long = 0L,
    val takenTime: Long? = null,
    val status: String = "MISSED", // "VERIFIED", "SELF_CONFIRMED", "MISSED"
    val verificationMethod: String = "NONE", // "OCR", "VOICE", "MANUAL", "NONE"
    val isTaken: Boolean = false // Added for user-driven confirmation logic
)
