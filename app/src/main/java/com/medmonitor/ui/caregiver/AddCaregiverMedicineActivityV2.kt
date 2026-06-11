package com.medmonitor.ui.caregiver

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.Timestamp
import com.medmonitor.data.model.caregiver.CaregiverMedicine
import com.medmonitor.data.repository.CaregiverRepositoryV2
import com.medmonitor.databinding.ActivityAddPatientMedicineBinding
import com.medmonitor.util.CaregiverMedicineAlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddCaregiverMedicineActivityV2 : AppCompatActivity() {
    private lateinit var binding: ActivityAddPatientMedicineBinding
    private val repository = CaregiverRepositoryV2()
    private lateinit var scheduler: CaregiverMedicineAlarmScheduler
    
    private var patientId: String? = null
    private var medicineId: String? = null
    private var isEdit = false

    private var startDate: Long? = null
    private var endDate: Long? = null
    private val scheduleTimes = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPatientMedicineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scheduler = CaregiverMedicineAlarmScheduler(this)
        patientId = intent.getStringExtra("PATIENT_ID")
        medicineId = intent.getStringExtra("MEDICINE_ID")
        isEdit = intent.getBooleanExtra("IS_EDIT", false)

        if (patientId == null) {
            Toast.makeText(this, "Error: Patient info missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        if (isEdit && medicineId != null) {
            loadMedicineData()
        }
    }

    private fun setupUI() {
        binding.tvTitle.text = if (isEdit) "Edit Medicine" else "Add Medicine"
        binding.btnSaveMedicine.text = if (isEdit) "Update Medicine" else "Add to Patient"
        binding.btnBack.setOnClickListener { finish() }

        setupSpinners()
        setupListeners()
    }

    private fun loadMedicineData() {
        lifecycleScope.launch {
            val medicine = repository.getAllCaregiverMedicines().first().find { it.id == medicineId }
            medicine?.let { med ->
                binding.etMedicineName.setText(med.medicineName)
                binding.etDosage.setText(med.dosage)
                
                med.startDate?.let { 
                    startDate = it.toDate().time 
                }
                med.endDate?.let { 
                    endDate = it.toDate().time 
                }
                updateDateRangeUi()

                scheduleTimes.putAll(med.scheduleTimes)
                med.scheduleTimes.forEach { (slot, time) ->
                    updateTimeUi(slot, time)
                    checkSlot(slot)
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSelectDuration.setOnClickListener { showDatePicker() }

        binding.cbMorning.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) showTimePicker("Morning", 8, 0)
            else { scheduleTimes.remove("Morning"); binding.tvMorningTime.visibility = View.GONE }
        }

        binding.cbAfternoon.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) showTimePicker("Afternoon", 13, 0)
            else { scheduleTimes.remove("Afternoon"); binding.tvAfternoonTime.visibility = View.GONE }
        }

        binding.cbNight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) showTimePicker("Night", 20, 0)
            else { scheduleTimes.remove("Night"); binding.tvNightTime.visibility = View.GONE }
        }

        binding.btnSaveMedicine.setOnClickListener { validateAndSave() }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Duration")
            .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build())
            .build()

        picker.addOnPositiveButtonClickListener { range ->
            startDate = range.first
            endDate = range.second
            updateDateRangeUi()
        }
        picker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun updateDateRangeUi() {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        if (startDate != null && endDate != null) {
            binding.tvSelectedRange.text = "${sdf.format(Date(startDate!!))} - ${sdf.format(Date(endDate!!))}"
        }
    }

    private fun showTimePicker(slot: String, h: Int, m: Int) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(h)
            .setMinute(m)
            .setTitleText("Select Time for $slot")
            .build()

        picker.addOnPositiveButtonClickListener {
            val formattedTime = formatTo12Hour(picker.hour, picker.minute)
            scheduleTimes[slot] = formattedTime
            updateTimeUi(slot, formattedTime)
        }
        picker.addOnCancelListener { if (!scheduleTimes.containsKey(slot)) uncheckSlot(slot) }
        picker.show(supportFragmentManager, "TIME_PICKER_$slot")
    }

    private fun checkSlot(slot: String) {
        when(slot) {
            "Morning" -> binding.cbMorning.isChecked = true
            "Afternoon" -> binding.cbAfternoon.isChecked = true
            "Night" -> binding.cbNight.isChecked = true
        }
    }

    private fun uncheckSlot(slot: String) {
        when(slot) {
            "Morning" -> binding.cbMorning.isChecked = false
            "Afternoon" -> binding.cbAfternoon.isChecked = false
            "Night" -> binding.cbNight.isChecked = false
        }
    }

    private fun updateTimeUi(slot: String, time: String) {
        when (slot) {
            "Morning" -> { binding.tvMorningTime.text = time; binding.tvMorningTime.visibility = View.VISIBLE }
            "Afternoon" -> { binding.tvAfternoonTime.text = time; binding.tvAfternoonTime.visibility = View.VISIBLE }
            "Night" -> { binding.tvNightTime.text = time; binding.tvNightTime.visibility = View.VISIBLE }
        }
    }

    private fun formatTo12Hour(h: Int, m: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    private fun setupSpinners() {
        val timings = arrayOf("Before Food", "After Food", "Anytime")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timings)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFoodTiming.adapter = adapter
    }

    private fun validateAndSave() {
        val name = binding.etMedicineName.text.toString().trim()
        val dosage = binding.etDosage.text.toString().trim()
        val instructions = binding.spinnerFoodTiming.selectedItem.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter medicine name", Toast.LENGTH_SHORT).show()
            return
        }

        if (scheduleTimes.isEmpty()) {
            Toast.makeText(this, "Please select at least one schedule time", Toast.LENGTH_SHORT).show()
            return
        }

        if (startDate == null || endDate == null) {
            Toast.makeText(this, "Please select duration", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val medicine = CaregiverMedicine(
                    id = medicineId ?: "",
                    patientId = patientId!!,
                    medicineName = name,
                    dosage = dosage,
                    instructions = instructions,
                    scheduleTimes = scheduleTimes,
                    startDate = Timestamp(Date(startDate!!)),
                    endDate = Timestamp(Date(endDate!!))
                )

                if (isEdit) {
                    repository.updateCaregiverMedicine(medicine)
                } else {
                    repository.addCaregiverMedicine(medicine)
                }

                scheduler.schedule(medicine)
                Toast.makeText(this@AddCaregiverMedicineActivityV2, "Medicine saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddCaregiverMedicineActivityV2, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
