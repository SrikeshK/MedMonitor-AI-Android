package com.medmonitor.util

import com.medmonitor.data.model.Medicine
import kotlin.math.ceil
import kotlin.math.max

enum class InventoryState {
    NORMAL,
    LOW,
    CRITICAL,
    EMPTY
}

/**
 * 🧩 PHASE 2: Centralized Inventory State Engine
 * SINGLE source-of-truth for Dashboard, Inventory, Alerts, and Escalation.
 */
fun getInventoryState(medicine: Medicine): InventoryState {
    val daysLeft = calculateDaysLeft(medicine)
    
    return when {
        medicine.remainingQuantity <= 0 -> InventoryState.EMPTY
        // CRITICAL: insufficient quantity for next dose OR less than 1 day remaining
        medicine.remainingQuantity < medicine.dosageAmount || daysLeft <= 1 -> InventoryState.CRITICAL
        medicine.remainingQuantity <= medicine.threshold -> InventoryState.LOW
        else -> InventoryState.NORMAL
    }
}

/**
 * 🧩 PHASE 2: Improved Days-Left Engine
 * Uses safeDailyUsage to prevent division by zero and handle legacy medicines.
 */
fun calculateDaysLeft(medicine: Medicine): Int {
    val normalized = medicine.normalize()
    
    // SAFE daily usage calculation: Fallback to schedule count if dosagePerDay is missing
    val calculatedUsage = max(
        medicine.dosagePerDay,
        normalized.scheduleTimes.size.toDouble()
    )

    return if (calculatedUsage > 0) {
        ceil(medicine.remainingQuantity / calculatedUsage).toInt()
    } else {
        0
    }
}

fun checkLowStock(medicine: Medicine): Boolean {
    return !medicine.lowStockAlertSent && medicine.remainingQuantity <= medicine.threshold
}
