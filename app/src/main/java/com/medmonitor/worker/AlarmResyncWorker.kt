package com.medmonitor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.MedicineAlarmScheduler

class AlarmResyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("AlarmResyncWorker", "Starting daily alarm resync...")
        val repository = MedicineRepository()

        return try {
            val medicines = repository.getAllMedicinesOnce()
            val scheduler = MedicineAlarmScheduler(applicationContext)

            medicines.forEach { medicine ->
                Log.d("AlarmResyncWorker", "Rescheduling: ${medicine.name}")
                scheduler.schedule(medicine)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("AlarmResyncWorker", "Error during alarm resync", e)
            Result.retry()
        }
    }
}
