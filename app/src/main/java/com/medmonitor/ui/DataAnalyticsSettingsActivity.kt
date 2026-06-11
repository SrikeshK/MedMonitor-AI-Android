package com.medmonitor.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.repository.ReportRepository
import com.medmonitor.databinding.ActivityDataAnalyticsSettingsBinding
import com.medmonitor.ui.analytics.WeeklyReportActivity
import com.medmonitor.util.WeeklyReportPdfGenerator
import kotlinx.coroutines.launch

class DataAnalyticsSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataAnalyticsSettingsBinding
    private lateinit var settingsManager: SettingsManager
    private val reportRepository = ReportRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataAnalyticsSettingsBinding.inflate(layoutInflater)
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
        binding.switchWeeklyReport.isChecked = settingsManager.weeklyReportEnabled
    }

    private fun setupListeners() {
        binding.switchWeeklyReport.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.weeklyReportEnabled = isChecked
        }

        binding.btnViewReport.setOnClickListener {
            val intent = Intent(this, WeeklyReportActivity::class.java)
            startActivity(intent)
        }

        binding.btnDownloadPdf.setOnClickListener {
            downloadReportDirectly()
        }
    }

    private fun downloadReportDirectly() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        Toast.makeText(this, "Generating PDF...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val logs = reportRepository.getWeeklyDoseLogs(userId)
                val summary = reportRepository.calculateWeeklySummary(logs)
                val generator = WeeklyReportPdfGenerator(this@DataAnalyticsSettingsActivity)
                val file = generator.generateWeeklyReport(summary)
                
                if (file != null) {
                    Toast.makeText(this@DataAnalyticsSettingsActivity, "Report saved: ${file.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@DataAnalyticsSettingsActivity, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DataAnalyticsSettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTouchAnimations() {
        val rows = listOf(binding.rowWeeklyReport, binding.btnViewReport, binding.btnDownloadPdf)
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
