package com.medmonitor.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.medmonitor.R
import com.medmonitor.databinding.FragmentDashboardBinding
import com.medmonitor.ui.medicine.AddMedicineActivity
import com.medmonitor.ui.medicine.MedicineListActivity
import com.medmonitor.ui.family.FamilyActivity
import com.medmonitor.ui.analytics.AnalyticsActivity
import com.medmonitor.ui.InventoryActivity
import com.medmonitor.ui.SettingsActivity
import com.medmonitor.util.MedicineAlarmScheduler
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var adapter: DashboardMedicineAdapter
    private lateinit var alarmScheduler: MedicineAlarmScheduler

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        alarmScheduler = MedicineAlarmScheduler(requireContext())
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        _binding?.let { b ->
            b.tvGreeting.text = "Hello, ${viewModel.getUserName()}"
            b.tvSubtitle.text = "Stay healthy today!"

            b.cardCompliance.setOnClickListener {
                it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).duration = 80
                }
            }

            b.cvSettings.setOnClickListener {
                if (isAdded) {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                }
            }

            b.btnQuickAdd.setOnClickListener {
                if (isAdded) {
                    startActivity(Intent(requireContext(), AddMedicineActivity::class.java))
                }
            }

            b.btnQuickFamily.setOnClickListener {
                if (isAdded) {
                    startActivity(Intent(requireContext(), FamilyActivity::class.java))
                }
            }

            b.btnQuickInventory.setOnClickListener {
                if (isAdded) {
                    startActivity(Intent(requireContext(), InventoryActivity::class.java))
                }
            }

            b.btnQuickAnalytics.setOnClickListener {
                if (isAdded) {
                    startActivity(Intent(requireContext(), AnalyticsActivity::class.java))
                }
            }

            b.tvTodayMedsTitle.setOnClickListener {
                 if (isAdded) {
                    startActivity(Intent(requireContext(), MedicineListActivity::class.java))
                 }
            }

            adapter = DashboardMedicineAdapter(emptyList())
            b.rvTodayMeds.layoutManager = LinearLayoutManager(requireContext())
            b.rvTodayMeds.adapter = adapter
            
            // Initial Chart Setup
            updateChart(0, 0)
        }
    }

    private fun updateChart(taken: Int, missed: Int) {
        _binding?.let { b ->
            val entries = if (taken == 0 && missed == 0) {
                listOf(PieEntry(1f, "")) // Placeholder
            } else {
                listOf(
                    PieEntry(taken.toFloat(), "Taken"),
                    PieEntry(missed.toFloat(), "Missed")
                )
            }
            
            val colors = if (taken == 0 && missed == 0) {
                listOf(resources.getColor(R.color.glass_stroke, null))
            } else {
                listOf(
                    resources.getColor(R.color.secondary, null),
                    resources.getColor(R.color.error, null)
                )
            }

            val dataSet = PieDataSet(entries, "").apply {
                this.colors = colors
                setDrawValues(false)
            }
            
            b.complianceChart.apply {
                data = PieData(dataSet)
                description.isEnabled = false
                legend.isEnabled = false
                setDrawEntryLabels(false)
                setDrawCenterText(false)
                holeRadius = 78f
                transparentCircleRadius = 82f
                setHoleColor(Color.TRANSPARENT)
                setDrawHoleEnabled(true)
                if (taken > 0 || missed > 0) animateY(800)
                invalidate()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dailyStats.collect { stats ->
                        _binding?.let { b ->
                            b.tvComplianceValue.text = "${stats.adherence}%"
                            b.tvTaken.text = "● ${stats.taken} Taken"
                            b.tvMissed.text = "● ${stats.missed} Missed"
                            updateChart(stats.taken, stats.missed)
                        }
                    }
                }

                launch {
                    viewModel.advancedStats.collect { stats ->
                        _binding?.let { b ->
                            if (stats.streak > 0) {
                                b.tvStreak.text = "${stats.streak} Day Streak"
                                b.tvStreak.visibility = View.VISIBLE
                                b.ivStreak.visibility = View.VISIBLE
                                b.streakDivider.visibility = View.VISIBLE
                            } else {
                                b.tvStreak.visibility = View.GONE
                                b.ivStreak.visibility = View.GONE
                                b.streakDivider.visibility = View.GONE
                            }
                        }
                    }
                }

                launch {
                    viewModel.todayMedicines.collect { medicines ->
                        _binding?.let { b ->
                            if (medicines.isEmpty()) {
                                b.tvTodayMedsTitle.text = "No medicines today"
                            } else {
                                b.tvTodayMedsTitle.text = "Today's Medicines"
                            }
                            adapter.updateData(medicines)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
