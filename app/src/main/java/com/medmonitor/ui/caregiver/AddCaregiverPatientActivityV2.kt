package com.medmonitor.ui.caregiver

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.data.repository.CaregiverRepositoryV2
import com.medmonitor.databinding.ActivityAddPatientBinding
import kotlinx.coroutines.launch

class AddCaregiverPatientActivityV2 : AppCompatActivity() {
    private lateinit var binding: ActivityAddPatientBinding
    private val repository = CaregiverRepositoryV2()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPatientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = "Add Caregiver Patient"
        binding.btnBack.setOnClickListener { finish() }

        setupSpinners()

        binding.btnSavePatient.setOnClickListener {
            val name = binding.etPatientName.text.toString().trim()
            val phone = binding.etPatientPhone.text.toString().trim()
            val ageStr = binding.etPatientAge.text.toString().trim()
            val gender = binding.spinnerGender.selectedItem.toString()
            val relation = binding.spinnerRelation.selectedItem.toString()

            if (name.isEmpty() || phone.isEmpty() || ageStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val age = ageStr.toIntOrNull() ?: 0
            savePatient(name, phone, age, gender, relation)
        }
    }

    private fun setupSpinners() {
        val genders = arrayOf("Male", "Female", "Other")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genders)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGender.adapter = genderAdapter

        val relations = arrayOf("Father", "Mother", "Spouse", "Sibling", "Friend", "Other")
        val relationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, relations)
        relationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRelation.adapter = relationAdapter
    }

    private fun savePatient(name: String, phone: String, age: Int, gender: String, relation: String) {
        lifecycleScope.launch {
            try {
                val patient = CaregiverPatient(
                    patientName = name,
                    phoneNumber = phone,
                    age = age,
                    gender = gender,
                    relation = relation,
                    createdAt = Timestamp.now()
                )
                repository.addCaregiverPatient(patient)
                Toast.makeText(this@AddCaregiverPatientActivityV2, "Patient added successfully (V2)", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddCaregiverPatientActivityV2, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
