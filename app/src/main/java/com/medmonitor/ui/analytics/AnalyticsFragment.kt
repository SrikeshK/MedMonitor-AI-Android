package com.medmonitor.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.medmonitor.R
import com.medmonitor.databinding.FragmentAnalyticsBinding
import kotlinx.coroutines.launch
import java.util.Calendar

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalyticsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dailyStats.collect { stats ->
                        binding.tvAdherencePercent.text = "${stats.adherence}%"
                        binding.adherenceProgress.progress = stats.adherence
                        binding.tvDoseSummary.text = "${stats.taken} Taken • ${stats.missed} Missed"
                        
                        binding.tvStatus.text = when {
                            stats.adherence >= 90 -> "Excellent"
                            stats.adherence >= 75 -> "Good"
                            stats.adherence >= 50 -> "Fair"
                            else -> "Needs Attention"
                        }
                        binding.tvStatus.setTextColor(when {
                            stats.adherence >= 75 -> resources.getColor(R.color.success, null)
                            stats.adherence >= 50 -> resources.getColor(R.color.warning, null)
                            else -> resources.getColor(R.color.error, null)
                        })

                        binding.tvOnTimeCount.text = "${stats.taken} Doses"
                        binding.tvMissedCount.text = "${stats.missed} Doses"
                    }
                }
                launch {
                    viewModel.weeklyStats.collect { stats ->
                        updateBarChart(stats.daily)
                        updateTrend(stats.daily)
                    }
                }
                launch {
                    viewModel.advancedStats.collect { stats ->
                        binding.tvStreak.text = "${stats.streak} Day Streak"
                        binding.tvPunctuality.text = if (stats.avgDelayMinutes < 15) "High Punctuality" else "Delayed Pattern"
                        
                        binding.tvInsightAvgDelay.text = "Avg delay: ${stats.avgDelayMinutes} mins"
                        binding.tvInsightMissedSlot.text = if (stats.mostMissedSlot != "None") {
                            "You often miss ${stats.mostMissedSlot.lowercase()} doses"
                        } else {
                            "No frequent missed doses"
                        }
                    }
                }
            }
        }
    }

    private fun updateTrend(weeklyData: Map<String, com.medmonitor.data.model.DailyStats>) {
        val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val calendar = Calendar.getInstance()
        val todayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val yesterdayIndex = (todayIndex + 6) % 7
        
        val todayAdherence = weeklyData[days[todayIndex]]?.adherence ?: 0
        val yesterdayAdherence = weeklyData[days[yesterdayIndex]]?.adherence ?: 0
        
        when {
            todayAdherence > yesterdayAdherence -> {
                binding.tvInsightTrend.text = "Your adherence improved today"
                binding.ivInsightTrend.setImageResource(R.drawable.ic_trend_up)
            }
            todayAdherence < yesterdayAdherence -> {
                binding.tvInsightTrend.text = "Adherence is lower than yesterday"
                binding.ivInsightTrend.setImageResource(R.drawable.ic_trend_up)
            }
            else -> {
                binding.tvInsightTrend.text = "Adherence is steady"
                binding.ivInsightTrend.setImageResource(R.drawable.ic_trend_up)
            }
        }
    }

    private fun updateBarChart(weeklyData: Map<String, com.medmonitor.data.model.DailyStats>) {
        val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val entries = mutableListOf<BarEntry>()
        
        days.forEachIndexed { index, day ->
            val taken = weeklyData[day]?.taken?.toFloat() ?: 0f
            entries.add(BarEntry(index.toFloat(), taken))
        }

        val dataSet = BarDataSet(entries, "Doses Taken").apply {
            color = resources.getColor(R.color.primary, null)
            valueTextColor = Color.WHITE
            setDrawValues(false)
        }

        binding.weeklyBarChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(days)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                setDrawGridLines(false)
                granularity = 1f
            }
            
            axisLeft.apply {
                textColor = Color.WHITE
                setDrawGridLines(true)
                gridColor = resources.getColor(R.color.glass_stroke, null)
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
