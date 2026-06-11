package com.medmonitor.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.databinding.ActivityOnboardingBinding
import com.medmonitor.databinding.ItemOnboardingBinding
import com.medmonitor.ui.auth.LoginActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        val adapter = OnboardingAdapter(listOf(
            OnboardingPage(
                "Never Miss Your Medicine",
                "Smart reminders and tracking",
                R.drawable.ic_pill
            ),
            OnboardingPage(
                "Stay Connected with Caregivers",
                "Real-time alerts and monitoring",
                R.drawable.ic_group
            ),
            OnboardingPage(
                "Track Your Health Easily",
                "Analytics and insights",
                R.drawable.ic_chart
            )
        ))

        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 2) {
                    binding.btnGetStarted.text = "Get Started"
                } else {
                    binding.btnGetStarted.text = "Next"
                }
            }
        })

        binding.btnGetStarted.setOnClickListener {
            if (binding.viewPager.currentItem < 2) {
                binding.viewPager.currentItem += 1
            } else {
                settingsManager.setOnboardingComplete(true)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }

    data class OnboardingPage(val title: String, val subtitle: String, val imageRes: Int)

    inner class OnboardingAdapter(private val pages: List<OnboardingPage>) :
        RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemOnboardingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val page = pages[position]
            holder.binding.tvTitle.text = page.title
            holder.binding.tvSubtitle.text = page.subtitle
            holder.binding.ivIllustration.setImageResource(page.imageRes)
        }

        override fun getItemCount(): Int = pages.size
    }
}
