package com.medmonitor.ui.medicine

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.databinding.ActivitySuccessBinding
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

class SuccessActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        applyFadeTransition()

        binding = ActivitySuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startSuccessAnimations()
        
        // Auto close after 2 seconds as requested
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                finish()
            }
        }, 2000)
    }

    private fun startSuccessAnimations() {
        // 1. Haptic Feedback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        // 2. Circle Scale & Fade-in
        binding.successCircle.alpha = 0f
        binding.successCircle.scaleX = 0.8f
        binding.successCircle.scaleY = 0.8f
        
        binding.successCircle.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                binding.successCircle.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()

        // 3. Text Fade-in
        binding.textContainer.animate()
            .alpha(1f)
            .setDuration(500)
            .setStartDelay(300)
            .start()

        // 4. Pulsing Glow Animation
        val pulseAnimation = ScaleAnimation(
            1.0f, 1.15f, 1.0f, 1.15f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        binding.radialGlow.startAnimation(pulseAnimation)

        // 5. Minimal Confetti Celebration
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(0x00E0C6, 0x3AA6FF, 0xFFFFFF),
            position = Position.Relative(0.5, 0.3),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100)
        )
        binding.konfettiView.start(party)
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
