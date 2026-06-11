package com.medmonitor.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.MedMonitorApplication
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.databinding.ActivitySettingsNotificationsBinding

class NotificationsSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsNotificationsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupToolbar()
        setupDropdowns()
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

    private fun setupDropdowns() {
        val snoozeOptions = resources.getStringArray(R.array.snooze_options)
        val snoozeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, snoozeOptions)
        binding.dropdownSnooze.setAdapter(snoozeAdapter)
        binding.dropdownSnooze.setOnItemClickListener { _, _, position, _ ->
            val value = when (position) {
                0 -> 5
                1 -> 10
                2 -> 15
                else -> 10
            }
            settingsManager.snoozeDuration = value
        }

        val soundOptions = resources.getStringArray(R.array.notification_sounds)
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, soundOptions)
        binding.dropdownNotificationSound.setAdapter(soundAdapter)
        binding.dropdownNotificationSound.setOnItemClickListener { _, _, position, _ ->
            settingsManager.notificationSound = soundOptions[position]
            updateNotificationChannel()
        }
    }

    private fun loadSettings() {
        binding.switchNotifications.isChecked = settingsManager.notificationsEnabled
        binding.switchVibration.isChecked = settingsManager.vibrationEnabled
        binding.etMissedDoseDelay.setText(settingsManager.missedDoseDelay.toString())
        
        val snoozeText = when (settingsManager.snoozeDuration) {
            5 -> "5 minutes"
            10 -> "10 minutes"
            15 -> "15 minutes"
            else -> "10 minutes"
        }
        binding.dropdownSnooze.setText(snoozeText, false)
        binding.dropdownNotificationSound.setText(settingsManager.notificationSound, false)
    }

    private fun setupListeners() {
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.notificationsEnabled = isChecked
        }
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.vibrationEnabled = isChecked
            updateNotificationChannel()
        }
        binding.etMissedDoseDelay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toIntOrNull()?.let { settingsManager.missedDoseDelay = it }
            }
        })
    }

    private fun setupTouchAnimations() {
        val rows = listOf(
            binding.rowEnableNotifications,
            binding.rowSnooze,
            binding.rowSound,
            binding.rowVibration,
            binding.rowMissedDose
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

    private fun updateNotificationChannel() {
        (application as? MedMonitorApplication)?.updateNotificationChannel()
    }
}
