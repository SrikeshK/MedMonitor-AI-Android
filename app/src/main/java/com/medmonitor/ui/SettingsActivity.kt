package com.medmonitor.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupNavigation()
        setupTouchAnimations()
        setupHeaderIcon()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupNavigation() {
        binding.cardNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationsSettingsActivity::class.java))
        }
        binding.cardStockAlerts.setOnClickListener {
            startActivity(Intent(this, StockAlertsActivity::class.java))
        }
        binding.cardCareCircle.setOnClickListener {
            startActivity(Intent(this, CareCircleSettingsActivity::class.java))
        }
        binding.cardData.setOnClickListener {
            startActivity(Intent(this, DataAnalyticsSettingsActivity::class.java))
        }
        binding.cardGeneral.setOnClickListener {
            startActivity(Intent(this, GeneralSettingsActivity::class.java))
        }
        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun setupHeaderIcon() {
        binding.headerIcon.setOnClickListener {
            it.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(80)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).duration = 80
                }
        }
    }

    private fun setupTouchAnimations() {
        val rows = listOf(
            binding.cardNotifications,
            binding.cardStockAlerts,
            binding.cardCareCircle,
            binding.cardData,
            binding.cardGeneral,
            binding.btnAbout
        )
        rows.forEach { applyTouchAnimation(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun applyTouchAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }
            false
        }
    }
}
