package com.medmonitor.ui.caregiver

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medmonitor.data.repository.CaregiverRepository
import com.medmonitor.databinding.ActivityPatientMedicineListBinding
import com.medmonitor.util.CaregiverSmsManager
import com.medmonitor.util.isMedicineActive
import kotlinx.coroutines.launch

class PatientMedicineListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPatientMedicineListBinding
    private val repository = CaregiverRepository()
    private val smsManager = CaregiverSmsManager()
    private lateinit var adapter: PatientMedicineAdapter
    private var patientId: String? = null
    private var patientPhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatientMedicineListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patientId = intent.getStringExtra("PATIENT_ID")
        val patientName = intent.getStringExtra("PATIENT_NAME")
        patientPhone = intent.getStringExtra("PATIENT_PHONE")
        
        binding.tvTitle.text = patientName ?: "Patient Medicines"

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        observeMedicines()
    }

    private fun setupRecyclerView() {
        adapter = PatientMedicineAdapter { medicine ->
            val phone = patientPhone
            if (phone != null) {
                // Formatting manual reminder text
                val timeStr = medicine.scheduleTimes.entries.joinToString(", ") { "${it.key} (${it.value})" }
                smsManager.sendReminder(this, phone, medicine.name, timeStr)
                Toast.makeText(this, "Reminder sent via SMS", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Patient info missing", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvPatientMedicines.layoutManager = LinearLayoutManager(this)
        binding.rvPatientMedicines.adapter = adapter
    }

    private fun observeMedicines() {
        patientId?.let { id ->
            lifecycleScope.launch {
                repository.getPatientMedicines(id).collect { medicines ->
                    val now = System.currentTimeMillis()
                    
                    // 🧩 PHASE 3A: Separate Active and Past Medications
                    val activeMeds = medicines.filter { it.isMedicineActive(now) }
                    val pastMeds = medicines.filter { !it.isMedicineActive(now) }
                    
                    val items = mutableListOf<PatientMedicineItem>()
                    
                    if (activeMeds.isNotEmpty()) {
                        items.add(PatientMedicineItem.Header("Active Medications"))
                        items.addAll(activeMeds.map { PatientMedicineItem.Entry(it) })
                    }
                    
                    if (pastMeds.isNotEmpty()) {
                        items.add(PatientMedicineItem.Header("Past Medications"))
                        items.addAll(pastMeds.map { PatientMedicineItem.Entry(it) })
                    }
                    
                    adapter.submitList(items)
                }
            }
        }
    }
}
