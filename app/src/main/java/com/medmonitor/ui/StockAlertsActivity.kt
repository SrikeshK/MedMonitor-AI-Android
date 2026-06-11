package com.medmonitor.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.data.SettingsManager
import com.medmonitor.databinding.ActivityStockAlertsBinding

class StockAlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockAlertsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupToolbar()
        loadSettings()
        setupListeners()
        setupTouchAnimations()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadSettings() {
        binding.switchEnableStockAlerts.isChecked = settingsManager.stockAlertsEnabled
        binding.switchNotifyDevice.isChecked = settingsManager.stockNotifyDevice
        binding.switchNotifyCaregiver.isChecked = settingsManager.stockNotifyCaregiver
    }

    private fun setupListeners() {
        binding.switchEnableStockAlerts.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.stockAlertsEnabled = isChecked
        }
        binding.switchNotifyDevice.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.stockNotifyDevice = isChecked
        }
        binding.switchNotifyCaregiver.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.stockNotifyCaregiver = isChecked
        }
    }

    private fun setupTouchAnimations() {
        val rows = listOf(
            binding.rowEnableStockAlerts,
            binding.rowNotifyDevice,
            binding.rowNotifyCaregiver
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
