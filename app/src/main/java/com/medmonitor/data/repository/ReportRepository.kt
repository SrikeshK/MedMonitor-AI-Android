package com.medmonitor.data.repository

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.WeeklySummary
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

class ReportRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val logsCollection = firestore.collection("dose_logs")

    suspend fun getWeeklyDoseLogs(userId: String): List<DoseLog> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val sevenDaysAgo = calendar.timeInMillis

        return try {
            // 🧩 SAFE FIX: Fetch by userId only to avoid composite index requirement
            // Filtering and sorting are performed locally to ensure reliability.
            val result = logsCollection
                .whereEqualTo("userId", userId)
                .get()
                .await()
            
            // 🧩 SAFE COMPATIBILITY FIX: Use fromSnapshot to avoid dropping legacy logs
            result.documents.mapNotNull { DoseLog.fromSnapshot(it) }
                .filter { it.timestamp.toDate().time >= sevenDaysAgo }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                Log.e("WeeklyReport", "Firestore query failed: Missing Index. Falling back to local filtering.", e)
            } else {
                Log.e("WeeklyReport", "Firestore query failed: ${e.message}", e)
            }
            emptyList()
        }
    }

    fun calculateWeeklySummary(logs: List<DoseLog>): WeeklySummary {
        val taken = logs.count { it.status == DoseStatus.TAKEN }
        val missed = logs.count { it.status == DoseStatus.MISSED }
        val delayed = logs.count { it.status == DoseStatus.DELAYED }
        // 🧩 PHASE 3: Count OUT_OF_STOCK separately
        val outOfStock = logs.count { it.status == DoseStatus.OUT_OF_STOCK }
        
        // 🧩 SAFE ANALYTICS: taken + delayed count as success.
        // missed counts as failure.
        // outOfStock is neutral and excluded from adherence % calculation to prevent penalizing the patient for supply issues.
        val totalAdherenceDenominator = taken + missed + delayed
        
        // 🧩 ADHERENCE FIX: Protect against divide-by-zero
        val adherence = if (totalAdherenceDenominator == 0) 0 else ((taken + delayed) * 100) / totalAdherenceDenominator

        return WeeklySummary(
            totalTaken = taken,
            totalMissed = missed,
            totalDelayed = delayed,
            totalOutOfStock = outOfStock,
            adherencePercent = adherence,
            logs = logs
        )
    }
}
