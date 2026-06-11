package com.medmonitor.util

import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.PatientMedicine

/**
 * 🧩 PHASE 3A: Caregiver-Only Active Medication Filtering
 * Provides safe logic to identify medications that are currently in their active window
 * and have remaining stock.
 */
fun Medicine.isMedicineActive(now: Long = System.currentTimeMillis()): Boolean {
    // Treat null/0 as always started
    val start = startDate ?: 0L
    val hasStarted = start <= now
    
    // Treat null/0 as never expiring (Long.MAX_VALUE)
    val end = if (endDate == null || endDate == 0L) Long.MAX_VALUE else endDate
    val notExpired = end >= now
    
    // Use remainingQuantity as stock indicator
    val hasStock = remainingQuantity > 0

    return hasStarted && notExpired && hasStock
}

/**
 * 🧩 PHASE 3A: PatientMedicine Active Check
 * Used in Caregiver Detail views to separate active from past meds.
 * Does not check stock as PatientMedicine model doesn't track live inventory.
 */
fun PatientMedicine.isMedicineActive(now: Long = System.currentTimeMillis()): Boolean {
    val end = if (endDate == null || endDate == 0L) Long.MAX_VALUE else endDate
    return end >= now
}
