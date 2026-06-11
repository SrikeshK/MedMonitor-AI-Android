package com.medmonitor.ui.medicine

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.databinding.ActivityOutOfStockBinding
import com.medmonitor.ui.InventoryActivity

class OutOfStockActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOutOfStockBinding
    private var medicineId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        applyFadeTransition()

        binding = ActivityOutOfStockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        medicineId = intent.getStringExtra("medicine_id")

        startAnimations()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnRefillNow.setOnClickListener {
            val intent = Intent(this, InventoryActivity::class.java).apply {
                putExtra("medicine_id", medicineId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun startAnimations() {
        // 1. Haptic Feedback (Warning pattern)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.root.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        // 2. Circle Scale & Fade-in
        binding.warningCircle.alpha = 0f
        binding.warningCircle.scaleX = 0.8f
        binding.warningCircle.scaleY = 0.8f
        
        binding.warningCircle.animate()
            .alpha(1f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 3. Text Fade-in
        binding.textContainer.alpha = 0f
        binding.textContainer.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(200)
            .start()
    }

    private fun applyFadeTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
