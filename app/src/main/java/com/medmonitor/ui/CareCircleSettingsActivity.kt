package com.medmonitor.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.databinding.ActivityCareCircleSettingsBinding

class CareCircleSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCareCircleSettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCareCircleSettingsBinding.inflate(layoutInflater)
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
        binding.switchNotifyImmediately.isChecked = settingsManager.notifyImmediately
        binding.switchNotifyAfterDelay.isChecked = settingsManager.notifyAfterDelay
        updateNotificationTypeButtonText()
    }

    private fun setupListeners() {
        binding.switchNotifyImmediately.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.notifyImmediately = isChecked
        }
        binding.switchNotifyAfterDelay.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.notifyAfterDelay = isChecked
        }
        binding.btnNotificationType.setOnClickListener {
            showNotificationTypeDialog()
        }
    }

    private fun showNotificationTypeDialog() {
        val options = resources.getStringArray(R.array.notification_types)
        val checkedItems = BooleanArray(options.size) { index ->
            settingsManager.notificationTypes.contains(options[index])
        }

        AlertDialog.Builder(this)
            .setTitle("Select Notification Types")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val selectedTypes = mutableSetOf<String>()
                options.forEachIndexed { index, s ->
                    if (checkedItems[index]) selectedTypes.add(s)
                }
                settingsManager.notificationTypes = selectedTypes
                updateNotificationTypeButtonText()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateNotificationTypeButtonText() {
        val selected = settingsManager.notificationTypes
        binding.btnNotificationType.text = if (selected.isEmpty()) {
            "Notification Type: None"
        } else {
            "Notification Type: ${selected.joinToString(", ")}"
        }
    }

    private fun setupTouchAnimations() {
        val rows = listOf(
            binding.rowNotifyImmediately,
            binding.rowNotifyAfterDelay,
            binding.btnNotificationType
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
