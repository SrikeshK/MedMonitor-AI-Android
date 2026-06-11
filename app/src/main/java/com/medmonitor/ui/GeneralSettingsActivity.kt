package com.medmonitor.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.databinding.ActivityGeneralSettingsBinding

class GeneralSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeneralSettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGeneralSettingsBinding.inflate(layoutInflater)
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
        val languages = resources.getStringArray(R.array.languages)
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languages)
        binding.dropdownLanguage.setAdapter(languageAdapter)
        binding.dropdownLanguage.setOnItemClickListener { _, _, position, _ ->
            settingsManager.appLanguage = languages[position]
        }
    }

    private fun loadSettings() {
        binding.dropdownLanguage.setText(settingsManager.appLanguage, false)
    }

    private fun setupListeners() {
        binding.btnHelpSupport.setOnClickListener {
            Toast.makeText(this, "Support email feature ready.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTouchAnimations() {
        val rows = listOf(
            binding.rowLanguage,
            binding.btnHelpSupport
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
