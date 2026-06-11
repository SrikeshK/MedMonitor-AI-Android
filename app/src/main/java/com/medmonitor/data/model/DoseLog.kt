package com.medmonitor.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot

data class DoseLog(
    @DocumentId val id: String = "",
    val medicineId: String = "",
    val medicineName: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: DoseStatus = DoseStatus.TAKEN,
    val userId: String = "",
    val verificationMethod: VerificationMethod = VerificationMethod.MANUAL,
    val slotName: String = "",
    val scheduledTime: String = "" // Identity key for the specific dose instance
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): DoseLog? {
            return try {
                val data = doc.data ?: return null
                
                // 🧩 LEGACY COMPATIBILITY MAPPING
                val rawStatus = (data["status"] as? String)?.lowercase() ?: ""
                val safeStatus = when (rawStatus) {
                    "taken", "completed", "done", "taken_legacy" -> DoseStatus.TAKEN
                    "missed" -> DoseStatus.MISSED
                    "delayed" -> DoseStatus.DELAYED
                    "out_of_stock" -> DoseStatus.OUT_OF_STOCK
                    // Fallback to enum name matching for exact matches like "TAKEN"
                    else -> {
                        try {
                            DoseStatus.valueOf(rawStatus.uppercase())
                        } catch (e: Exception) {
                            DoseStatus.MISSED // 🧩 SAFE FIX: Fallback to MISSED, NEVER TAKEN
                        }
                    }
                }

                val rawVM = (data["verificationMethod"] as? String)?.uppercase() ?: ""
                val safeVM = try {
                    VerificationMethod.valueOf(rawVM)
                } catch (e: Exception) {
                    VerificationMethod.MANUAL
                }

                DoseLog(
                    id = doc.id,
                    medicineId = data["medicineId"] as? String ?: "",
                    medicineName = data["medicineName"] as? String ?: "Unknown Medicine",
                    timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now(),
                    status = safeStatus,
                    userId = data["userId"] as? String ?: "",
                    verificationMethod = safeVM,
                    slotName = data["slotName"] as? String ?: "",
                    scheduledTime = data["scheduledTime"] as? String ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

enum class DoseStatus {
    TAKEN, MISSED, DELAYED, OUT_OF_STOCK
}

enum class VerificationMethod {
    MANUAL, VOICE, IMAGE
}
