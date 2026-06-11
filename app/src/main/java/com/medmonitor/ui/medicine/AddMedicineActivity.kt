package com.medmonitor.ui.medicine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.MedicineType
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.databinding.ActivityAddMedicineBinding
import com.medmonitor.util.MedicineAlarmScheduler
import com.medmonitor.util.MedicineStatusUtil
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class AddMedicineActivity : AppCompatActivity() {

    private var _binding: ActivityAddMedicineBinding? = null
    private val binding get() = _binding!!
    
    private val repository = MedicineRepository()
    private lateinit var alarmScheduler: MedicineAlarmScheduler
    
    private var isSaving = false
    private var selectedType = MedicineType.TABLET
    private var selectedImageUri: Uri? = null

    private var startDate: Long? = null
    private var endDate: Long? = null
    private var foodTiming: String = "ANYTIME"
    
    private val scheduleTimes = mutableMapOf<String, String>()

    private var isEditMode = false
    private var medicineId: String? = null
    private var existingTotalQuantity: Double = 0.0
    private var existingRemainingQuantity: Double = 0.0
    private var existingImageUrl: String? = null
    private var existingReminderOwner: String = "PATIENT"

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                val uri = saveBitmapToUri(it)
                selectedImageUri = uri
                binding.ivMedicinePreview.setImageBitmap(it)
                showImagePreview()
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val internalUri = copyUriToInternalStorage(uri)
                if (internalUri != null) {
                    selectedImageUri = internalUri
                    binding.ivMedicinePreview.setImageURI(selectedImageUri)
                    showImagePreview()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityAddMedicineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmScheduler = MedicineAlarmScheduler(this)

        setupListeners()
        setupScheduleListeners()
        
        selectTablet()
        selectFoodTiming("ANYTIME")
        
        checkEditMode()
        requestPermissions()
        
        // Ensure internal storage folder exists
        getInternalImageFolder()
    }

    private fun checkEditMode() {
        medicineId = intent.getStringExtra("medicine_id")
        if (!medicineId.isNullOrEmpty()) {
            isEditMode = true
            binding.toolbar.title = "Update Medicine"
            binding.btnSaveMedicine.text = "Update Medicine"
            preFillFields()
        } else {
            // Default threshold for new medicine
            binding.etLowStockThreshold.setText("5")
            binding.toggleThresholdMode.check(R.id.btnAutoThreshold)
            binding.layoutLowStockThreshold.visibility = View.GONE
        }
    }

    private fun preFillFields() {
        Log.d("EDIT_DEBUG", "Prefill Medicine ID: $medicineId")

        binding.etMedicineName.setText(intent.getStringExtra("name"))
        
        val typeStr = intent.getStringExtra("type") ?: "TABLET"
        selectedType = try { MedicineType.valueOf(typeStr) } catch (e: Exception) { MedicineType.TABLET }
        if (selectedType == MedicineType.SYRUP) selectSyrup() else selectTablet()

        binding.etDoseAmount.setText(intent.getDoubleExtra("dose", 0.0).toString())
        
        existingTotalQuantity = intent.getDoubleExtra("total_quantity", 0.0)
        binding.etTotalQuantity.setText(existingTotalQuantity.toString())
        
        val threshold = intent.getDoubleExtra("threshold", 5.0)
        binding.etLowStockThreshold.setText(threshold.toInt().toString())

        val dosagePerDay = intent.getDoubleExtra("dosage_per_day", 1.0)
        if (threshold == dosagePerDay * 2) {
            binding.toggleThresholdMode.check(R.id.btnAutoThreshold)
            binding.layoutLowStockThreshold.visibility = View.GONE
        } else {
            binding.toggleThresholdMode.check(R.id.btnManualThreshold)
            binding.layoutLowStockThreshold.visibility = View.VISIBLE
        }

        existingRemainingQuantity = intent.getDoubleExtra("remaining_quantity", 0.0)
        existingImageUrl = intent.getStringExtra("image_url")
        existingReminderOwner = intent.getStringExtra("reminder_owner") ?: "PATIENT"
        
        if (!existingImageUrl.isNullOrEmpty()) {
            // 🧩 STORAGE FIX: Support internal file paths and legacy URIs
            selectedImageUri = if (existingImageUrl!!.startsWith("/")) {
                Uri.fromFile(File(existingImageUrl!!))
            } else {
                Uri.parse(existingImageUrl)
            }
            binding.ivMedicinePreview.setImageURI(selectedImageUri)
            showImagePreview()
        }

        foodTiming = intent.getStringExtra("food_timing") ?: "ANYTIME"
        selectFoodTiming(foodTiming)

        startDate = intent.getLongExtra("start_date", 0L).takeIf { it > 0 }
        endDate = intent.getLongExtra("end_date", 0L).takeIf { it > 0 }
        if (startDate != null && endDate != null) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.tvSelectedRange.text = "${sdf.format(Date(startDate!!))} - ${sdf.format(Date(endDate!!))}"
        }

        @Suppress("UNCHECKED_CAST")
        val scheduleMap = intent.getSerializableExtra("schedule_times") as? HashMap<String, String>
        scheduleMap?.forEach { (slot, time12) ->
            try {
                val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = sdf12.parse(time12)
                if (date != null) {
                    val time24 = sdf24.format(date)
                    scheduleTimes[slot] = time24
                    updateTimeUi(slot, time12)
                    when(slot) {
                        "MORNING" -> binding.cbMorning.isChecked = true
                        "AFTERNOON" -> binding.cbAfternoon.isChecked = true
                        "NIGHT" -> binding.cbNight.isChecked = true
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnTablet.setOnClickListener { selectTablet() }
        binding.btnSyrup.setOnClickListener { selectSyrup() }
        binding.btnSelectDuration.setOnClickListener { showDatePicker() }
        binding.beforeFood.setOnClickListener { selectFoodTiming("BEFORE_FOOD") }
        binding.afterFood.setOnClickListener { selectFoodTiming("AFTER_FOOD") }
        binding.anytime.setOnClickListener { selectFoodTiming("ANYTIME") }
        binding.btnScanMedicine.setOnClickListener { showImageSourceDialog() }
        binding.btnRetake.setOnClickListener { showImageSourceDialog() }
        binding.btnConfirmImage.setOnClickListener { binding.layoutConfirmImage.visibility = View.GONE }
        binding.btnSaveMedicine.setOnClickListener {
            if (isSaving) return@setOnClickListener

            isSaving = true
            binding.btnSaveMedicine.isEnabled = false
            binding.btnSaveMedicine.alpha = 0.6f

            saveMedicine()
        }

        binding.toggleThresholdMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnManualThreshold) {
                    binding.layoutLowStockThreshold.visibility = View.VISIBLE
                } else {
                    binding.layoutLowStockThreshold.visibility = View.GONE
                }
            }
        }
    }

    private fun selectTablet() {
        selectedType = MedicineType.TABLET
        binding.btnTablet.setBackgroundResource(R.drawable.bg_selected)
        binding.btnSyrup.setBackgroundResource(R.drawable.bg_unselected)
        binding.layoutDoseAmount.suffixText = "pcs"
    }

    private fun selectSyrup() {
        selectedType = MedicineType.SYRUP
        binding.btnSyrup.setBackgroundResource(R.drawable.bg_selected)
        binding.btnTablet.setBackgroundResource(R.drawable.bg_unselected)
        binding.layoutDoseAmount.suffixText = "ml"
    }

    private fun selectFoodTiming(timing: String) {
        foodTiming = timing
        binding.beforeFood.setBackgroundResource(R.drawable.bg_unselected)
        binding.afterFood.setBackgroundResource(R.drawable.bg_unselected)
        binding.anytime.setBackgroundResource(R.drawable.bg_unselected)
        when (timing) {
            "BEFORE_FOOD" -> binding.beforeFood.setBackgroundResource(R.drawable.bg_selected)
            "AFTER_FOOD" -> binding.afterFood.setBackgroundResource(R.drawable.bg_selected)
            "ANYTIME" -> binding.anytime.setBackgroundResource(R.drawable.bg_selected)
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Image")
        AlertDialog.Builder(this).setTitle("Add Reference Image").setItems(options) { _, which ->
            when (which) {
                0 -> openCamera()
                1 -> openGallery()
                2 -> removeImage()
            }
        }.show()
    }

    private fun openCamera() = takePhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    private fun openGallery() = pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
    private fun removeImage() {
        selectedImageUri = null
        existingImageUrl = null
        binding.ivMedicinePreview.visibility = View.GONE
        binding.cameraContainer.visibility = View.GONE
        binding.layoutConfirmImage.visibility = View.GONE
        binding.layoutScanAction.visibility = View.VISIBLE
    }

    private fun showImagePreview() {
        binding.cameraContainer.visibility = View.VISIBLE
        binding.ivMedicinePreview.visibility = View.VISIBLE
        binding.viewFinder.visibility = View.GONE
        binding.btnCapture.visibility = View.GONE
        binding.layoutConfirmImage.visibility = View.VISIBLE
        binding.layoutScanAction.visibility = View.GONE
    }

    private fun getInternalImageFolder(): File {
        val folder = File(filesDir, "medicine_refs")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    private fun copyUriToInternalStorage(uri: Uri): Uri? {
        return try {
            val folder = getInternalImageFolder()
            val file = File(folder, "med_gallery_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e("ADD_MED", "Error copying image to internal storage", e)
            null
        }
    }

    private fun saveBitmapToUri(bitmap: Bitmap): Uri? {
        // 🧩 STORAGE FIX: Use persistent internal storage instead of cache
        val folder = getInternalImageFolder()
        val file = File(folder, "med_capture_${System.currentTimeMillis()}.jpg")
        return try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
            Uri.fromFile(file)
        } catch (e: Exception) { null }
    }

    private fun setupScheduleListeners() {
        binding.cbMorning.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !scheduleTimes.containsKey("MORNING")) showTimePicker("MORNING", 8, 0)
            else if (!isChecked) { binding.tvMorningTime.visibility = View.GONE; scheduleTimes.remove("MORNING") }
        }
        binding.cbAfternoon.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !scheduleTimes.containsKey("AFTERNOON")) showTimePicker("AFTERNOON", 13, 0)
            else if (!isChecked) { binding.tvAfternoonTime.visibility = View.GONE; scheduleTimes.remove("AFTERNOON") }
        }
        binding.cbNight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !scheduleTimes.containsKey("NIGHT")) showTimePicker("NIGHT", 21, 0)
            else if (!isChecked) { binding.tvNightTime.visibility = View.GONE; scheduleTimes.remove("NIGHT") }
        }
        binding.tvMorningTime.setOnClickListener { showTimePicker("MORNING", 8, 0) }
        binding.tvAfternoonTime.setOnClickListener { showTimePicker("AFTERNOON", 13, 0) }
        binding.tvNightTime.setOnClickListener { showTimePicker("NIGHT", 21, 0) }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker().setTitleText("Select Duration")
            .setCalendarConstraints(CalendarConstraints.Builder().setValidator(DateValidatorPointForward.now()).build()).build()
        picker.addOnPositiveButtonClickListener {
            startDate = it.first; endDate = it.second
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.tvSelectedRange.text = "${sdf.format(Date(startDate!!))} - ${sdf.format(Date(endDate!!))}"
        }
        picker.show(supportFragmentManager, "DATE_RANGE_PICKER")
    }

    private fun showTimePicker(slot: String, h: Int, m: Int) {
        val picker = MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_12H).setHour(h).setMinute(m).setTitleText("Select Time for $slot").build()
        picker.addOnPositiveButtonClickListener {
            val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", picker.hour, picker.minute)
            scheduleTimes[slot] = formattedTime
            val displayTime = formatTo12Hour(picker.hour, picker.minute)
            updateTimeUi(slot, displayTime)
        }
        picker.show(supportFragmentManager, "TIME_PICKER_$slot")
    }

    private fun updateTimeUi(slot: String, displayTime: String) {
        when (slot) {
            "MORNING" -> { binding.tvMorningTime.text = displayTime; binding.tvMorningTime.visibility = View.VISIBLE }
            "AFTERNOON" -> { binding.tvAfternoonTime.text = displayTime; binding.tvAfternoonTime.visibility = View.VISIBLE }
            "NIGHT" -> { binding.tvNightTime.text = displayTime; binding.tvNightTime.visibility = View.VISIBLE }
        }
    }

    private fun formatTo12Hour(h: Int, m: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    private fun requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.CAMERA)
        }
    }

    private fun saveMedicine() {
        val name = binding.etMedicineName.text.toString().trim()
        val dose = binding.etDoseAmount.text.toString().toDoubleOrNull() ?: 0.0
        val total = binding.etTotalQuantity.text.toString().toDoubleOrNull() ?: 0.0
        
        val slots = mutableListOf<String>()
        if (binding.cbMorning.isChecked) slots.add("MORNING")
        if (binding.cbAfternoon.isChecked) slots.add("AFTERNOON")
        if (binding.cbNight.isChecked) slots.add("NIGHT")

        val safeSlots = if (slots.isEmpty()) 1 else slots.size
        val safeDose = if (dose <= 0.0) 1.0 else dose
        val dosagePerDay = safeDose * safeSlots

        val finalThreshold = if (binding.toggleThresholdMode.checkedButtonId == R.id.btnAutoThreshold) {
            dosagePerDay * 2 
        } else {
            binding.etLowStockThreshold.text.toString().toDoubleOrNull() ?: 5.0
        }

        if (name.isEmpty() || dose <= 0.0 || total <= 0.0 || startDate == null || endDate == null) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            isSaving = false
            binding.btnSaveMedicine.isEnabled = true
            binding.btnSaveMedicine.alpha = 1.0f
            return
        }

        if (slots.isEmpty() || !slots.all { scheduleTimes.containsKey(it) }) {
            Toast.makeText(this, "Please select times for all slots", Toast.LENGTH_SHORT).show()
            isSaving = false
            binding.btnSaveMedicine.isEnabled = true
            binding.btnSaveMedicine.alpha = 1.0f
            return
        }

        val formattedScheduleTimes = scheduleTimes.filterKeys { slots.contains(it) }.mapValues { entry ->
            val parts = entry.value.split(":")
            formatTo12Hour(parts[0].toInt(), parts[1].toInt())
        }

        val firstTimeStr = formattedScheduleTimes.values.first()
        val scheduledMillis = MedicineStatusUtil.parseTimeToMillis(firstTimeStr)

        val finalRemaining = if (isEditMode) {
            if (total > existingTotalQuantity) {
                existingRemainingQuantity + (total - existingTotalQuantity)
            } else {
                min(existingRemainingQuantity, total)
            }
        } else {
            total
        }

        val boundedRemaining = max(0.0, min(finalRemaining, total))

        lifecycleScope.launch {
            try {
                var existingCompleted = false
                var existingCompletedTime = 0L
                var existingStatus = "PENDING"
                var existingSlotStatus = mutableMapOf<String, String>()
                var existingScheduleTimes = emptyMap<String, String>()
                var existingCreatedAt = 0L

                val currentId = medicineId
                if (isEditMode && currentId != null) {
                    try {
                        val oldMedicine = repository.getMedicineById(currentId)
                        oldMedicine?.let {
                            // 🧩 SAFE EDIT RESET: Detect time change for single dose
                            val isSingleDose = formattedScheduleTimes.size == 1 && it.scheduleTimes.size == 1
                            val newTime = formattedScheduleTimes.values.firstOrNull()
                            val oldTime = it.scheduleTimes.values.firstOrNull()
                            val isTimeChanged = isSingleDose && newTime != oldTime

                            if (isTimeChanged) {
                                existingCompleted = false
                                existingCompletedTime = 0L
                                existingStatus = "PENDING"
                            } else {
                                existingCompleted = it.isCompleted
                                existingCompletedTime = it.completedTime
                                existingStatus = it.status
                            }
                            existingSlotStatus = it.slotStatus.toMutableMap()
                            existingScheduleTimes = it.scheduleTimes
                            existingCreatedAt = it.createdAt
                        }
                    } catch (e: Exception) {
                        Log.e("SAFE_FIX", "Error fetching old medicine", e)
                    }
                }

                val finalSlotStatus = if (formattedScheduleTimes.size > 1) {
                    val newSlotStatus = mutableMapOf<String, String>()
                    formattedScheduleTimes.forEach { (slotName, newTime) ->
                        val oldTime = existingScheduleTimes[slotName]
                        val oldStatus = existingSlotStatus[slotName]

                        if (oldTime == newTime) {
                            // Time unchanged → keep old state
                            newSlotStatus[slotName] = oldStatus ?: "PENDING"
                        } else {
                            // Time changed → reset state
                            newSlotStatus[slotName] = "PENDING"
                        }
                    }
                    newSlotStatus
                } else {
                    emptyMap()
                }

                // 🧩 STORAGE FIX: Store absolute path for internal images
                val finalImageUrl = when {
                    selectedImageUri != null -> {
                        if (selectedImageUri?.scheme == "file") {
                            selectedImageUri?.path
                        } else {
                            selectedImageUri?.toString()
                        }
                    }
                    else -> existingImageUrl
                }

                val medicine = Medicine(
                    id = medicineId ?: "",
                    name = name,
                    type = selectedType,
                    dosageAmount = dose,
                    totalQuantity = total,
                    remainingQuantity = boundedRemaining,
                    unit = if (selectedType == MedicineType.TABLET) "pcs" else "ml",
                    startDate = startDate,
                    endDate = endDate,
                    foodTiming = foodTiming,
                    scheduleSlots = slots,
                    scheduleTimes = formattedScheduleTimes,
                    scheduledTime = scheduledMillis,
                    threshold = finalThreshold,
                    dosagePerDay = dosagePerDay,
                    imageUrl = finalImageUrl,
                    isCompleted = existingCompleted,
                    completedTime = existingCompletedTime,
                    status = existingStatus,
                    slotStatus = finalSlotStatus,
                    createdAt = if (isEditMode) existingCreatedAt else System.currentTimeMillis(),
                    reminderOwner = if (isEditMode) existingReminderOwner else "PATIENT"
                )

                val finalId = repository.addMedicine(medicine)
                
                val prefs = getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
                prefs.edit().remove("status_$finalId").apply()

                alarmScheduler.schedule(medicine.copy(id = finalId))
                
                // 🧩 PART 6 — OPTIONAL USER SAFETY (HIGHLY RECOMMENDED)
                val settingsManager = SettingsManager(this@AddMedicineActivity)
                val hasCaregiver = settingsManager.getCachedCaregivers().isNotEmpty() || 
                                 settingsManager.caregiverPhone.isNotBlank()
                
                if (!hasCaregiver) {
                    AlertDialog.Builder(this@AddMedicineActivity)
                        .setTitle("Add caregiver to enable alerts")
                        .setMessage("You haven't added a caregiver. SMS alerts for missed doses will not be sent until a caregiver is added in settings.")
                        .setPositiveButton("OK") { _, _ -> 
                            Toast.makeText(this@AddMedicineActivity, "Medicine saved", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    Toast.makeText(this@AddMedicineActivity, "Medicine saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) { 
                Toast.makeText(this@AddMedicineActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() 
                isSaving = false
                binding.btnSaveMedicine.isEnabled = true
                binding.btnSaveMedicine.alpha = 1.0f
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isSaving) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
