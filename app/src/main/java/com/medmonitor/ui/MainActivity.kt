package com.medmonitor.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.repository.MedicineRepository
import com.medmonitor.util.MedicineAlarmScheduler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private val TAG = "MainActivity"
    private val repository = MedicineRepository()
    private lateinit var settingsManager: SettingsManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] ?: false
        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        
        if (!smsGranted) {
            Toast.makeText(this, "SMS permission is required for Caregiver Alerts.", Toast.LENGTH_LONG).show()
        }
        if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, "Notification permission is required for Medicine Reminders.", Toast.LENGTH_LONG).show()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(TAG, "Network available, triggering sync")
            lifecycleScope.launch {
                repository.syncPendingDoses(this@MainActivity)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        
        // 🧩 STEP 4 — OPTIONAL SAFETY: Ensure mode exists
        if (settingsManager.getUserMode().isNullOrEmpty()) {
            startActivity(Intent(this, ModeSelectionActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        
        try {
            checkPermissions()
            
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController
            
            setupCustomBottomNav(navController)

            // 🧩 STRONGER START SYNC
            syncAlarmsOnStart()
            
            // 🧩 PART 5 — TRIGGER SYNC ON LAUNCH
            triggerInitialSync()
            
            registerNetworkCallback()

        } catch (e: Exception) {
            Log.e("HOME_UI", "Error in MainActivity onCreate", e)
        }
    }

    private fun triggerInitialSync() {
        lifecycleScope.launch {
            repository.syncPendingDoses(this@MainActivity)
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Unregister network callback failed", e)
        }
    }

    private fun syncAlarmsOnStart() {
        lifecycleScope.launch {
            try {
                val medicines = repository.getAllMedicinesOnce()
                val scheduler = MedicineAlarmScheduler(this@MainActivity)

                medicines.forEach { medicine ->
                    scheduler.schedule(medicine)
                }
                Log.d(TAG, "SyncAlarmsOnStart: All alarms rescheduled")
            } catch (e: Exception) {
                Log.e(TAG, "SyncAlarmsOnStart: Failed to sync alarms", e)
            }
        }
    }

    private fun setupCustomBottomNav(navController: NavController) {
        val navItems = listOf(
            NavigationItem(R.id.navigation_dashboard, R.id.icon_dashboard, R.id.text_dashboard, R.id.navContent_dashboard),
            NavigationItem(R.id.navigation_medicines, R.id.icon_medicines, R.id.text_medicines, R.id.navContent_medicines),
            NavigationItem(R.id.navigation_notifications, R.id.icon_notifications, R.id.text_notifications, R.id.navContent_notifications),
            NavigationItem(R.id.navigation_profile, R.id.icon_profile, R.id.text_profile, R.id.navContent_profile)
        )

        navItems.forEach { item ->
            findViewById<View>(item.layoutId).setOnClickListener {
                it.animateClick {
                    if (navController.currentDestination?.id != item.layoutId) {
                        navController.navigate(item.layoutId)
                    }
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            navItems.forEach { item ->
                val icon = findViewById<ImageView>(item.iconId)
                val text = findViewById<TextView>(item.textId)
                val navContent = findViewById<View>(item.navContentId)

                if (item.layoutId == destination.id) {
                    navContent.setBackgroundResource(R.drawable.bg_nav_selected_small)
                    icon.setColorFilter(Color.parseColor("#00E5FF"))
                    text.setTextColor(Color.parseColor("#00E5FF"))
                    navContent.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(120)
                        .start()
                } else {
                    navContent.background = null
                    icon.setColorFilter(Color.parseColor("#80FFFFFF"))
                    text.setTextColor(Color.parseColor("#80FFFFFF"))
                    navContent.scaleX = 1f
                    navContent.scaleY = 1f
                }
            }
        }
    }

    private data class NavigationItem(val layoutId: Int, val iconId: Int, val textId: Int, val navContentId: Int)

    private fun View.animateClick(onEnd: () -> Unit) {
        this.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                this.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { onEnd() }
                    .start()
            }
            .start()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
