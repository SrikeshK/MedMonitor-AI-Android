package com.medmonitor.ui.analytics

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.medmonitor.databinding.ActivityAnalyticsBinding

class AnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnalyticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
