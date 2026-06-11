package com.medmonitor.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.databinding.ActivitySplashBinding
import com.medmonitor.ui.auth.LoginActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupUI()
        startAnimations()

        Handler(Looper.getMainLooper()).postDelayed({
            val isOnboardingDone = settingsManager.isOnboardingComplete()
            
            if (!isOnboardingDone) {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return@postDelayed
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val mode = settingsManager.getUserMode()
                when (mode) {
                    "CAREGIVER" -> {
                        startActivity(Intent(this, CaregiverMainActivity::class.java))
                    }
                    "PATIENT" -> {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                    else -> {
                        startActivity(Intent(this, ModeSelectionActivity::class.java))
                    }
                }
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2000)
    }

    private fun setupUI() {
        binding.tvAppName.post {
            val paint = binding.tvAppName.paint
            val width = paint.measureText(binding.tvAppName.text.toString())
            val textShader: Shader = LinearGradient(
                0f, 0f, width, binding.tvAppName.textSize,
                intArrayOf(
                    ContextCompat.getColor(this, R.color.cyan_glow),
                    ContextCompat.getColor(this, R.color.white)
                ), null, Shader.TileMode.CLAMP
            )
            binding.tvAppName.paint.shader = textShader
        }
    }

    private fun startAnimations() {
        binding.ivSplashLogo.alpha = 0f
        binding.ivSplashLogo.scaleX = 0.9f
        binding.ivSplashLogo.scaleY = 0.9f
        
        binding.vGlassEffect.alpha = 0f
        binding.vGlassEffect.scaleX = 0.8f
        binding.vGlassEffect.scaleY = 0.8f

        binding.ivSplashLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1000)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.vGlassEffect.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.vRadialGlow,
            PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.2f),
            PropertyValuesHolder.ofFloat("alpha", 0.6f, 1.0f)
        ).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulse.start()

        binding.tvAppName.alpha = 0f
        binding.tvAppName.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(400)
            .start()

        binding.tvTagline.animate()
            .alpha(1f)
            .setDuration(800)
            .setStartDelay(700)
            .start()
            
        binding.loadingBar.alpha = 0f
        binding.loadingBar.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(1000)
            .start()
    }
}
