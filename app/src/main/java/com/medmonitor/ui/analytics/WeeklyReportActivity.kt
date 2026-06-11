package com.medmonitor.ui.analytics

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.MedMonitorApplication
import com.medmonitor.R
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.data.model.WeeklySummary
import com.medmonitor.data.repository.ReportRepository
import com.medmonitor.databinding.ActivityWeeklyReportBinding
import com.medmonitor.util.WeeklyReportPdfGenerator
import kotlinx.coroutines.launch
import java.io.File

class WeeklyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklyReportBinding
    private val repository = ReportRepository()
    private val adapter = WeeklyReportAdapter()
    private var currentSummary: WeeklySummary? = null
    private var allLogs: List<DoseLog> = emptyList()

    // 🧩 NOTIFICATION PERMISSION FIX: Pending file storage
    private var pendingPdfFile: File? = null

    // 🧩 NOTIFICATION PERMISSION FIX: Permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingPdfFile?.let {
                showDownloadNotification(it)
            }
        } else {
            Toast.makeText(
                this,
                "Notification permission denied",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // 🧩 REALTIME REFRESH: Refresh data whenever activity is reopened or resumed
        loadReportData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnDownloadReport.setOnClickListener {
            downloadPdfReport()
        }

        binding.chipGroupFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            filterLogs(checkedIds.firstOrNull())
        }
    }

    private fun loadReportData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val logs = repository.getWeeklyDoseLogs(userId)
                allLogs = logs
                val summary = repository.calculateWeeklySummary(logs)
                currentSummary = summary
                
                updateUI(summary)
            } catch (e: Exception) {
                Toast.makeText(this@WeeklyReportActivity, "Error loading report", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateUI(summary: WeeklySummary) {
        binding.tvAdherencePercent.text = "${summary.adherencePercent}%"
        binding.adherenceProgress.progress = summary.adherencePercent
        
        binding.tvTakenCount.text = summary.totalTaken.toString()
        binding.tvMissedCount.text = summary.totalMissed.toString()
        binding.tvDelayedCount.text = summary.totalDelayed.toString()
        
        adapter.submitList(summary.logs)
    }

    private fun filterLogs(checkedId: Int?) {
        val filteredList = when (checkedId) {
            binding.chipTaken.id -> allLogs.filter { it.status == DoseStatus.TAKEN }
            binding.chipMissed.id -> allLogs.filter { it.status == DoseStatus.MISSED }
            binding.chipDelayed.id -> allLogs.filter { it.status == DoseStatus.DELAYED }
            else -> allLogs
        }
        adapter.submitList(filteredList)
    }

    private fun downloadPdfReport() {
        val summary = currentSummary ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val generator = WeeklyReportPdfGenerator(this@WeeklyReportActivity)
            val file = generator.generateWeeklyReport(summary)
            
            binding.progressBar.visibility = View.GONE
            
            if (file != null) {
                // 🧩 NOTIFICATION PERMISSION FIX: Store file and check permissions
                pendingPdfFile = file
                checkAndShowNotification(file)
                showExportSuccess(file)
            } else {
                Toast.makeText(this@WeeklyReportActivity, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndShowNotification(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                showDownloadNotification(file)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            showDownloadNotification(file)
        }
    }

    private fun showDownloadNotification(file: File) {
        try {
            Log.d("WeeklyReport", "Showing PDF notification")
            
            // 🧩 PDF AUTHORITY FIX: Authority matches manifest
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, MedMonitorApplication.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download) // Professional download icon
                .setContentTitle("Weekly Report Downloaded")
                .setContentText("Tap to open your PDF report")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(file.name.hashCode(), notification)
        } catch (e: Exception) {
            Log.e("WeeklyReport", "Notification failed", e)
        }
    }

    private fun showExportSuccess(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        
        Snackbar.make(binding.root, "Report generated successfully", Snackbar.LENGTH_LONG)
            .setAction("Open") { openFile(uri) }
            .show()
    }

    private fun openFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open Report"))
        } catch (e: Exception) {
            Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }
}
