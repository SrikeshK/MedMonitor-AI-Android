package com.medmonitor.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.data.SettingsManager
import com.medmonitor.databinding.ActivityModeSelectionBinding

class ModeSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModeSelectionBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModeSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        binding.btnPatient.setOnClickListener {
            settingsManager.setUserMode("PATIENT")
            navigateToMain()
        }

        binding.btnCaregiver.setOnClickListener {
            settingsManager.setUserMode("CAREGIVER")
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        val mode = settingsManager.getUserMode()
        val intent = if (mode == "CAREGIVER") {
            Intent(this, CaregiverMainActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
