package com.medmonitor.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.medmonitor.R

class CaregiverMainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_main)

        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_caregiver) as NavHostFragment
            navController = navHostFragment.navController

            setupCustomBottomNav(navController)
            handleIntent(intent)
        } catch (e: Exception) {
            Log.e("CaregiverMainActivity", "Error in onCreate", e)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            val navigateTo = it.getStringExtra("navigate_to")
            if (navigateTo == "alerts") {
                try {
                    // Check if we are already there to avoid redundant transactions
                    // especially during rapid UI refreshes in AlertsFragment
                    if (navController.currentDestination?.id != R.id.navigation_alerts) {
                        navController.navigate(R.id.navigation_alerts)
                    }
                    // Clear the extra so it doesn't re-trigger on config change
                    it.removeExtra("navigate_to")
                } catch (e: Exception) {
                    Log.e("CaregiverMainActivity", "Navigation error in handleIntent", e)
                }
            }
        }
    }

    private fun setupCustomBottomNav(navController: NavController) {
        val navItems = listOf(
            NavigationItem(R.id.navigation_caregiver_dashboard, R.id.icon_dashboard, R.id.text_dashboard, R.id.navContent_dashboard),
            NavigationItem(R.id.navigation_patients, R.id.icon_patients, R.id.text_patients, R.id.navContent_patients),
            NavigationItem(R.id.navigation_alerts, R.id.icon_alerts, R.id.text_alerts, R.id.navContent_alerts),
            NavigationItem(R.id.navigation_profile, R.id.icon_profile, R.id.text_profile, R.id.navContent_profile)
        )

        navItems.forEach { item ->
            findViewById<View>(item.layoutId)?.setOnClickListener {
                it.animateClick {
                    try {
                        if (navController.currentDestination?.id != item.layoutId) {
                            navController.navigate(item.layoutId)
                        }
                    } catch (e: Exception) {
                        Log.e("CaregiverMainActivity", "Navigation click error", e)
                    }
                }
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            try {
                navItems.forEach { item ->
                    val icon = findViewById<ImageView>(item.iconId)
                    val text = findViewById<TextView>(item.textId)
                    val navContent = findViewById<View>(item.navContentId)

                    if (item.layoutId == destination.id) {
                        navContent?.setBackgroundResource(R.drawable.bg_nav_selected_small)
                        icon?.setColorFilter(Color.parseColor("#00E5FF"))
                        text?.setTextColor(Color.parseColor("#00E5FF"))
                        navContent?.animate()
                            ?.scaleX(1.1f)
                            ?.scaleY(1.1f)
                            ?.setDuration(120)
                            ?.start()
                    } else {
                        navContent?.background = null
                        icon?.setColorFilter(Color.parseColor("#80FFFFFF"))
                        text?.setTextColor(Color.parseColor("#80FFFFFF"))
                        navContent?.scaleX = 1f
                        navContent?.scaleY = 1f
                    }
                }
            } catch (e: Exception) {
                Log.e("CaregiverMainActivity", "Error in onDestinationChanged", e)
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
}
