package com.medmonitor.utils

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class MedicineReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val medicineId = inputData.getString("MEDICINE_ID")
        val medicineName = inputData.getString("MEDICINE_NAME")

        // TODO: Fire local notification for the reminder
        
        return Result.success()
    }
}
