package com.medmonitor.ui.caregiver

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medmonitor.data.repository.CaregiverRepositoryV2
import com.medmonitor.databinding.ActivitySelectPatientForMedicineBinding
import kotlinx.coroutines.launch

class SelectPatientForMedicineActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySelectPatientForMedicineBinding
    private val repository = CaregiverRepositoryV2()
    private lateinit var adapter: PatientSelectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectPatientForMedicineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        observePatients()
    }

    private fun setupRecyclerView() {
        adapter = PatientSelectAdapter { patient ->
            val intent = Intent(this, AddCaregiverMedicineActivityV2::class.java)
            intent.putExtra("PATIENT_ID", patient.patientId)
            intent.putExtra("PATIENT_NAME", patient.patientName)
            startActivity(intent)
            finish()
        }
        binding.rvPatients.layoutManager = LinearLayoutManager(this)
        binding.rvPatients.adapter = adapter
    }

    private fun observePatients() {
        lifecycleScope.launch {
            repository.getCaregiverPatients().collect { patients ->
                adapter.submitList(patients)
            }
        }
    }
}
