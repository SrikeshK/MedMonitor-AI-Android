package com.medmonitor.ui.caregiver

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.data.model.caregiver.CaregiverAlertLog
import com.medmonitor.data.repository.CaregiverRepositoryV2
import com.medmonitor.ui.CaregiverMainActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeepLinkHandlerActivity : AppCompatActivity() {
    private val repositoryV2 = CaregiverRepositoryV2()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent.data
        if (data != null && data.scheme == "medmonitor" && data.host == "confirm") {
            val patientId = data.getQueryParameter("patientId")
            val medicineId = data.getQueryParameter("medicineId")
            val slotName = data.getQueryParameter("slot")

            if (patientId != null && medicineId != null) {
                confirmPatientDose(patientId, medicineId, slotName ?: "")
            } else {
                navigateToDashboard()
            }
        } else {
            navigateToDashboard()
        }
    }

    private fun confirmPatientDose(patientId: String, medicineId: String, slotName: String) {
        lifecycleScope.launch {
            try {
                // 🧩 PHASE 4: V2 Migration - Redirect to caregiver_alert_logs
                val caregiverId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

                // In V2, we write to caregiver_alert_logs. 
                // We don't need to fetch patient metadata here if we only care about the log, 
                // but for dashboard consistency, we populate what we can.
                
                val log = CaregiverAlertLog(
                    caregiverId = caregiverId,
                    patientId = patientId,
                    medicineId = medicineId,
                    medicineName = "Medicine", // Ideally fetch this, but for deep link confirmation "Medicine" is a safe fallback or we can expand query params
                    scheduledTime = slotName, 
                    status = "TAKEN",
                    actionTaken = "REMOTE_CONFIRMED",
                    actionTimestamp = Timestamp.now(),
                    dateKey = dateStr
                )
                
                repositoryV2.addCaregiverAlertLog(log)
                
                Toast.makeText(this@DeepLinkHandlerActivity, "Dose confirmed remotely", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("DeepLink", "Error confirming dose", e)
                Toast.makeText(this@DeepLinkHandlerActivity, "Failed to confirm dose", Toast.LENGTH_SHORT).show()
            } finally {
                navigateToDashboard()
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, CaregiverMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
