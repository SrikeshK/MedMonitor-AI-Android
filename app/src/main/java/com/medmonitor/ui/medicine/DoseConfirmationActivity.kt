package com.medmonitor.ui.medicine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.VerificationMethod
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.databinding.ActivityDoseConfirmationBinding
import com.medmonitor.util.CareAlertManager
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.NetworkUtil
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DoseConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoseConfirmationBinding
    private val repository = MedicineRepository()
    private var medicineId: String = ""
    private var medicine: Medicine? = null
    private var medicineName: String = ""
    private var dosage: String = ""
    private var normalizedSlot: String = ""
    private var storedImageUrl: String? = null

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private enum class VerificationStatus {
        SUCCESS, MISMATCH, ERROR
    }

    companion object {
        private const val TAG = "VERIFY"
    }

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText?.contains("confirm", ignoreCase = true) == true || 
                spokenText?.contains("taken", ignoreCase = true) == true) {
                confirmDose(VerificationMethod.VOICE)
            } else {
                Toast.makeText(this, "Voice not recognized. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoseConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        medicineId = (
            intent.getStringExtra("medicine_id")
                ?: intent.getStringExtra("medicineId")
        )?.trim() ?: ""
        
        if (medicineId.isEmpty()) {
            Log.e(TAG, "Error: medicineId is null or empty")
            Toast.makeText(this, "Critical Error: Medicine ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val slotName = intent.getStringExtra("slot") ?: ""
        normalizedSlot = MedicineStatusUtil.normalizeSlot(slotName)
        
        medicineName = intent.getStringExtra("medicine_name") ?: "Medicine"
        dosage = intent.getStringExtra("dose") ?: "Take Dose"

        binding.tvTargetMedicine.text = medicineName
        binding.tvTargetDosage.text = dosage

        setButtonsEnabled(false)

        loadMedicineData()
        setupListeners()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnVerifyManual.isEnabled = enabled
        binding.btnVerifyVoice.isEnabled = enabled
        binding.btnVerifyImage.isEnabled = enabled
    }

    private fun loadMedicineData() {
        lifecycleScope.launch {
            try {
                medicine = repository.getMedicineById(medicineId)
                
                if (medicine == null) {
                    Log.e(TAG, "Verify database: Database returned null for ID $medicineId")
                    Toast.makeText(this@DoseConfirmationActivity, "Error: Medicine not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                storedImageUrl = medicine?.imageUrl
                binding.tvTargetMedicine.text = medicine?.name
                binding.tvTargetDosage.text = "${medicine?.dosageAmount ?: ""} ${medicine?.unit ?: ""}"

                setupFoodUI(medicine?.foodTiming)
                setButtonsEnabled(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading medicine", e)
                Toast.makeText(this@DoseConfirmationActivity, "Failed to load medicine data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFoodUI(foodTiming: String?) {
        try {
            val foodBadge = binding.foodBadge
            val foodHint = binding.foodHint
            foodBadge.elevation = 6f

            when(foodTiming) {
                "BEFORE", "BEFORE_FOOD" -> {
                    foodBadge.text = getString(R.string.before_food)
                    foodBadge.setBackgroundResource(R.drawable.bg_food_before)
                    foodHint.text = getString(R.string.take_before_food)
                }
                "AFTER", "AFTER_FOOD" -> {
                    foodBadge.text = getString(R.string.after_food)
                    foodBadge.setBackgroundResource(R.drawable.bg_food_after)
                    foodHint.text = getString(R.string.take_after_food)
                }
                else -> {
                    foodBadge.text = getString(R.string.anytime)
                    foodBadge.setBackgroundResource(R.drawable.bg_food_anytime)
                    foodHint.text = getString(R.string.take_anytime)
                }
            }
        } catch (e: Exception) {
            Log.e("FOOD_UI", "Error", e)
        }
    }

    private fun setupListeners() {
        binding.btnVerifyManual.setOnClickListener {
            animateButtonClick(it) { confirmDose(VerificationMethod.MANUAL) }
        }

        binding.btnVerifyVoice.setOnClickListener {
            animateButtonClick(it) { startVoiceRecognition() }
        }

        binding.btnVerifyImage.setOnClickListener {
            animateButtonClick(it) {
                if (storedImageUrl.isNullOrEmpty()) {
                    showResultDialog(VerificationStatus.ERROR, 0)
                } else {
                    startImageVerification()
                }
            }
        }

        binding.btnCaptureVerify.setOnClickListener { takePhotoForVerification() }
        binding.btnCloseCamera.setOnClickListener { binding.cameraOverlay.visibility = View.GONE }
    }

    private fun animateButtonClick(view: View, onEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction { onEnd() }.start()
        }.start()
    }

    private fun startImageVerification() {
        binding.cameraOverlay.visibility = View.VISIBLE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera launch failed", exc)
                binding.cameraOverlay.visibility = View.GONE
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhotoForVerification() {
        val imageCapture = imageCapture ?: return
        val file = File(externalCacheDir, "verify_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@DoseConfirmationActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    binding.cameraOverlay.visibility = View.GONE
                    processVerification(file)
                }
            }
        )
    }

    private fun processVerification(capturedFile: File) {
        try {
            val capturedBitmap = BitmapFactory.decodeFile(capturedFile.absolutePath)
            
            // 🧩 STORAGE FIX: Robust image loading supporting URIs and absolute paths
            val storedBitmap = if (storedImageUrl.isNullOrEmpty()) {
                null
            } else if (storedImageUrl!!.startsWith("/")) {
                // Absolute path
                BitmapFactory.decodeFile(storedImageUrl)
            } else {
                // URI (content:// or file://)
                try {
                    val uri = Uri.parse(storedImageUrl)
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load URI, trying as file path", e)
                    BitmapFactory.decodeFile(storedImageUrl)
                }
            }

            if (capturedBitmap == null || storedBitmap == null) {
                showResultDialog(VerificationStatus.ERROR, 0)
                return
            }

            val score = compareImages(capturedBitmap, storedBitmap)
            val similarityPercent = (score * 100).toInt()

            if (score > 0.7f) {
                showResultDialog(VerificationStatus.SUCCESS, similarityPercent)
            } else {
                showResultDialog(VerificationStatus.MISMATCH, similarityPercent, capturedBitmap, storedBitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Verification error", e)
            showResultDialog(VerificationStatus.ERROR, 0)
        }
    }

    private fun compareImages(b1: Bitmap, b2: Bitmap): Float {
        val r1 = Bitmap.createScaledBitmap(b1, 100, 100, true)
        val r2 = Bitmap.createScaledBitmap(b2, 100, 100, true)
        var diff = 0L
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                val p1 = r1.getPixel(x, y)
                val p2 = r2.getPixel(x, y)
                diff += Math.abs(android.graphics.Color.red(p1) - android.graphics.Color.red(p2))
                diff += Math.abs(android.graphics.Color.green(p1) - android.graphics.Color.green(p2))
                diff += Math.abs(android.graphics.Color.blue(p1) - android.graphics.Color.blue(p2))
            }
        }
        return 1.0f - (diff.toFloat() / (3L * 255 * 100 * 100))
    }

    private fun showResultDialog(
        status: VerificationStatus,
        score: Int,
        captured: Bitmap? = null,
        reference: Bitmap? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_verification_result, null)
        val dialog = AlertDialog.Builder(this, R.style.Theme_MedMonitor_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val tvIcon = dialogView.findViewById<TextView>(R.id.tvStatusIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvResultTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvResultMessage)
        val tvScore = dialogView.findViewById<TextView>(R.id.tvScore)
        val comparisonLayout = dialogView.findViewById<View>(R.id.comparisonLayout)
        val ivReference = dialogView.findViewById<ImageView>(R.id.ivReference)
        val ivCaptured = dialogView.findViewById<ImageView>(R.id.ivCaptured)
        val btnPositive = dialogView.findViewById<MaterialButton>(R.id.btnPositive)
        val btnNegative = dialogView.findViewById<MaterialButton>(R.id.btnNegative)

        tvScore.text = "Match Score: $score%"

        when (status) {
            VerificationStatus.SUCCESS -> {
                tvIcon.text = "✓"
                tvIcon.setTextColor(ContextCompat.getColor(this, R.color.success))
                tvTitle.text = "Medicine Verified"
                tvMessage.text = "Reference image matched successfully."
                btnPositive.text = "Continue"
                btnPositive.setOnClickListener {
                    dialog.dismiss()
                    confirmDose(VerificationMethod.IMAGE)
                }
                btnNegative.visibility = View.GONE
            }
            VerificationStatus.MISMATCH -> {
                tvIcon.text = "⚠"
                tvIcon.setTextColor(ContextCompat.getColor(this, R.color.warning))
                tvTitle.text = "Medicine Not Verified"
                tvMessage.text = "The captured image does not closely match\nthe stored medicine image."
                comparisonLayout.visibility = View.VISIBLE
                ivReference.setImageBitmap(reference)
                ivCaptured.setImageBitmap(captured)
                
                btnPositive.text = "Retry"
                btnPositive.setOnClickListener {
                    dialog.dismiss()
                    startImageVerification()
                }
                btnNegative.text = "Manual"
                btnNegative.setOnClickListener {
                    dialog.dismiss()
                    confirmDose(VerificationMethod.MANUAL)
                }
            }
            VerificationStatus.ERROR -> {
                tvIcon.text = "ⓘ"
                tvIcon.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tvTitle.text = "Verification Unavailable"
                tvMessage.text = "The medicine image could not be loaded.\n\nYou may retry or use another\nverification method."
                tvScore.visibility = View.GONE
                
                btnPositive.text = "Retry"
                btnPositive.setOnClickListener {
                    dialog.dismiss()
                    loadMedicineData()
                }
                btnNegative.text = "Manual"
                btnNegative.setOnClickListener {
                    dialog.dismiss()
                    confirmDose(VerificationMethod.MANUAL)
                }
            }
        }

        dialog.show()
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'Confirm' or 'Taken'")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDose(method: VerificationMethod) {
        setButtonsEnabled(false)

        try {
            if (medicine == null) {
                Log.e(TAG, "Confirm clicked but medicine object is null")
                Toast.makeText(this, "Medicine not loaded", Toast.LENGTH_SHORT).show()
                setButtonsEnabled(true)
                return
            }

            val currentMedicine = medicine!!
            
            lifecycleScope.launch {
                try {
                    val scheduledTimeIdentity = if (MedicineStatusUtil.isMultiDose(currentMedicine)) {
                        currentMedicine.scheduleTimes[normalizedSlot] ?: "00:00"
                    } else {
                        MedicineStatusUtil.formatTime(currentMedicine.scheduledTime)
                    }

                    val doseLog = DoseLog(
                        medicineId = medicineId,
                        medicineName = currentMedicine.name,
                        status = DoseStatus.TAKEN,
                        verificationMethod = method,
                        slotName = normalizedSlot,
                        scheduledTime = scheduledTimeIdentity
                    )

                    if (!NetworkUtil.isNetworkAvailable(this@DoseConfirmationActivity)) {
                        handleOfflineConfirmation(doseLog)
                        return@launch
                    }

                    if (currentMedicine.scheduleTimes.size <= 1) {
                        currentMedicine.isCompleted = true
                        currentMedicine.completedTime = System.currentTimeMillis()
                    }
                    
                    CareAlertManager.cancelMissedAlert(this@DoseConfirmationActivity, medicineId)
                    val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    val uniqueWorkName = "missed_${medicineId}*${normalizedSlot}*${currentDate}"
                    WorkManager.getInstance(this@DoseConfirmationActivity).cancelUniqueWork(uniqueWorkName)
                    
                    val result = repository.recordDose(doseLog, this@DoseConfirmationActivity)

                    when(result) {
                        "SUCCESS", "ALREADY_TAKEN" -> {
                            if (result == "ALREADY_TAKEN") {
                                Toast.makeText(this@DoseConfirmationActivity, "Dose already taken for this time", Toast.LENGTH_SHORT).show()
                            }
                            
                            val prefs = getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
                            prefs.edit().putString("status_$medicineId", "TAKEN").apply()

                            val successIntent = Intent(this@DoseConfirmationActivity, SuccessActivity::class.java)
                            startActivity(successIntent)
                            finish()
                        }
                        "NOT_FOUND" -> {
                            Toast.makeText(this@DoseConfirmationActivity, "Medicine data not available", Toast.LENGTH_SHORT).show()
                            setButtonsEnabled(true)
                        }
                        else -> {
                            Toast.makeText(this@DoseConfirmationActivity, "Failed to confirm dose", Toast.LENGTH_SHORT).show()
                            setButtonsEnabled(true)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during confirmation", e)
                    if (e is IllegalStateException && e.message == "OUT_OF_STOCK") {
                        val outOfStockIntent = Intent(this@DoseConfirmationActivity, OutOfStockActivity::class.java).apply {
                            putExtra("medicine_id", medicineId)
                            putExtra("medicine_name", currentMedicine.name)
                        }
                        startActivity(outOfStockIntent)
                        finish()
                    } else {
                        Toast.makeText(this@DoseConfirmationActivity, "Failed to confirm dose", Toast.LENGTH_SHORT).show()
                        setButtonsEnabled(true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Crash prevented", e)
            setButtonsEnabled(true)
        }
    }

    private fun handleOfflineConfirmation(doseLog: DoseLog) {
        val settings = SettingsManager(this)
        
        // 🧩 SAFE UX FALLBACK: Check local stock before saving offline
        if (medicine != null && medicine!!.remainingQuantity < medicine!!.dosageAmount) {
             val outOfStockIntent = Intent(this, OutOfStockActivity::class.java).apply {
                putExtra("medicine_id", medicineId)
                putExtra("medicine_name", medicine!!.name)
            }
            startActivity(outOfStockIntent)
            finish()
            return
        }

        settings.addPendingDose(doseLog)
        val prefs = getSharedPreferences("medmonitor_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("status_$medicineId", "TAKEN").apply()

        Toast.makeText(this, "Saved offline. Will sync automatically.", Toast.LENGTH_LONG).show()
        val successIntent = Intent(this, SuccessActivity::class.java)
        startActivity(successIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
