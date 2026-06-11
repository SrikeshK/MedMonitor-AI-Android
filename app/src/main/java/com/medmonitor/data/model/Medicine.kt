package com.medmonitor.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

data class Medicine(
    @DocumentId val id: String = "",
    val name: String = "",
    val type: MedicineType = MedicineType.TABLET,
    val dosageAmount: Double = 0.0,
    val totalQuantity: Double = 0.0,
    val remainingQuantity: Double = 0.0,
    val unit: String = "pcs",
    val frequency: Int = 1,
    val scheduleTime: List<String> = emptyList(),
    val imageUrl: String? = null,
    val userId: String = "",
    
    // Feature 7: Extended Fields
    val startDate: Long? = null,
    val endDate: Long? = null,
    val foodTiming: String = "ANYTIME", // BEFORE_FOOD, AFTER_FOOD, ANYTIME
    val scheduleSlots: List<String> = emptyList(), // MORNING, AFTERNOON, NIGHT
    val scheduleTimes: Map<String, String> = emptyMap(), // e.g., "MORNING" -> "08:00"
    
    // PART 1 — SLOT STATE (CORE)
    val slotStatus: Map<String, String> = emptyMap(), // e.g., "Morning" -> "PENDING"
    
    // Critical Synchronization Fields
    val scheduledTime: Long = 0, // Standardized time in millis
    val isTaken: Boolean = false,
    var isCompleted: Boolean = false,
    var completedTime: Long = 0,
    val status: String = "PENDING", // Added status field for state sync
    
    // FINAL STABILITY — PART 2: Track last reset/update day
    val lastUpdatedTime: Long = 0,
    val createdAt: Long = 0, // Added to track creation for first-day protection
    
    // Low Stock Alert System Fields
    val threshold: Double = 5.0,
    val dosagePerDay: Double = 1.0,
    var lowStockAlertSent: Boolean = false,
    var criticalAlertSent: Boolean = false, // 🧩 PHASE 2: Critical Escalation Tracking

    // SAFE OWNERSHIP METADATA
    val reminderOwner: String = "PATIENT", // "PATIENT" or "CAREGIVER"
    
    // UI Display field (not stored in DB usually, but added for convenience if needed)
    @Exclude val displayStatus: String = ""
) {
    @Exclude
    fun getTime(): Long = scheduledTime

    /**
     * 🧩 LEGACY COMPATIBILITY BRIDGE
     * Safely normalizes old schema medicines in-memory without affecting Firestore.
     */
    fun normalize(): Medicine {
        // 1. SAFE endDate HANDLING (Step 2)
        // Treat null or 0L as Long.MAX_VALUE only during normalization/filtering.
        val normalizedEndDate = if (endDate == null || endDate == 0L) {
            Long.MAX_VALUE
        } else {
            endDate
        }

        // 2. RESTORE LEGACY MULTI-SLOT SUPPORT (Step 3)
        // Bridging legacy lists to the new map format in-memory.
        var normalizedScheduleTimes = scheduleTimes.mapKeys { it.key.uppercase() }
        
        if (normalizedScheduleTimes.isEmpty()) {
            val bridgeMap = mutableMapOf<String, String>()
            
            // Scenario A: Both lists exist (standard legacy multi-slot)
            if (scheduleSlots.isNotEmpty() && scheduleTime.isNotEmpty()) {
                scheduleSlots.forEachIndexed { index, slot ->
                    if (index < scheduleTime.size) {
                        bridgeMap[slot.uppercase()] = scheduleTime[index]
                    }
                }
            } 
            // Scenario B: Only slots exist (use default times)
            else if (scheduleSlots.isNotEmpty()) {
                val legacySlotsUpper = scheduleSlots.map { it.uppercase() }
                if (legacySlotsUpper.contains("MORNING")) bridgeMap["MORNING"] = "08:00 AM"
                if (legacySlotsUpper.contains("AFTERNOON")) bridgeMap["AFTERNOON"] = "02:00 PM"
                if (legacySlotsUpper.contains("NIGHT")) bridgeMap["NIGHT"] = "10:00 PM"
            } 
            // Scenario C: Only scheduleTime exists (very old legacy single-dose)
            else if (scheduleTime.isNotEmpty()) {
                bridgeMap["MORNING"] = scheduleTime[0]
            }
            
            if (bridgeMap.isNotEmpty()) {
                normalizedScheduleTimes = bridgeMap
            }
        }

        // 3. NORMALIZE SLOT KEYS (Step 4)
        // Ensure all slot status keys are uppercase for consistent lookup.
        val normalizedSlotStatus = slotStatus.mapKeys { it.key.uppercase() }

        return this.copy(
            endDate = normalizedEndDate,
            scheduleTimes = normalizedScheduleTimes,
            slotStatus = normalizedSlotStatus
        )
    }
}

enum class MedicineType {
    TABLET, SYRUP
}
