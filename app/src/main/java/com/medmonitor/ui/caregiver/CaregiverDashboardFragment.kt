package com.medmonitor.ui.caregiver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.medmonitor.R
import com.medmonitor.databinding.FragmentCaregiverDashboardBinding
import com.medmonitor.ui.caregiver.viewmodel.CaregiverDashboardViewModelV2
import kotlinx.coroutines.launch

class CaregiverDashboardFragment : Fragment() {
    private var _binding: FragmentCaregiverDashboardBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CaregiverDashboardViewModelV2 by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCaregiverDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupInitialUI()
        setupClickListeners()
        observeViewModel()
        startEntranceAnimation()
    }

    private fun setupInitialUI() {
        binding.careOverviewChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setDrawCenterText(false)
            holeRadius = 82f
            transparentCircleRadius = 85f
            setHoleColor(Color.TRANSPARENT)
            setDrawHoleEnabled(true)
            setTouchEnabled(false)
        }
        updateChart(0, 0)
    }

    private fun startEntranceAnimation() {
        binding.scrollView.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(400) // Slightly faster, crisper animation
                .start()
        }
    }

    private fun setupClickListeners() {
        fun View.applyClickEffect() {
            this.animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction {
                this.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
            }.start()
        }

        binding.btnAddPatient.setOnClickListener {
            it.applyClickEffect()
            startActivity(Intent(requireContext(), AddCaregiverPatientActivityV2::class.java))
        }

        binding.btnAddMedicine.setOnClickListener {
            it.applyClickEffect()
            startActivity(Intent(requireContext(), SelectPatientForMedicineActivity::class.java))
        }
        
        binding.btnViewAlerts.setOnClickListener {
            it.applyClickEffect()
            // Navigation logic would go here
        }

        binding.cardNeedsAttention.setOnClickListener {
             it.applyClickEffect()
             try {
                 findNavController().navigate(R.id.navigation_patients)
             } catch (e: Exception) {
                 // Fallback or log error
             }
        }
        
        binding.cardCareOverview.setOnClickListener {
            it.applyClickEffect()
        }
    }

    private fun updateChart(taken: Int, missed: Int) {
        val entries = if (taken == 0 && missed == 0) {
            listOf(PieEntry(1f, "")) 
        } else {
            listOf(
                PieEntry(taken.toFloat(), "Taken"),
                PieEntry(missed.toFloat(), "Missed")
            )
        }
        
        val colors = if (taken == 0 && missed == 0) {
            listOf(Color.parseColor("#0DFFFFFF")) // Muted placeholder
        } else {
            listOf(
                ContextCompat.getColor(requireContext(), R.color.secondary),
                ContextCompat.getColor(requireContext(), R.color.error)
            )
        }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            setDrawValues(false)
        }
        
        binding.careOverviewChart.apply {
            data = PieData(dataSet)
            if (taken > 0 || missed > 0) animateY(800)
            invalidate()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.patients.collect { patients ->
                        binding.llActivePatients.removeAllViews()
                        patients.take(3).forEach { patient ->
                            val patientView = LayoutInflater.from(requireContext())
                                .inflate(R.layout.item_medicine_compact, binding.llActivePatients, false)
                            
                            patientView.findViewById<TextView>(R.id.tvMedicineName).text = patient.patientName
                            patientView.findViewById<TextView>(R.id.tvDosageInfo).text = "Patient Status: Stable"
                            patientView.findViewById<View>(R.id.statusBadge).visibility = View.GONE
                            patientView.findViewById<View>(R.id.tvTime).visibility = View.GONE
                            
                            binding.llActivePatients.addView(patientView)
                        }
                    }
                }

                launch {
                    viewModel.takenTodayCount.collect { count ->
                        binding.tvTakenToday.text = "● $count Taken"
                        updateChart(count, viewModel.missedCount.value)
                    }
                }

                launch {
                    viewModel.missedCount.collect { count ->
                        binding.tvMissedToday.text = "● $count Missed"
                        updateChart(viewModel.takenTodayCount.value, count)
                    }
                }
                
                launch {
                    viewModel.caregiverAdherence.collect { adherence ->
                        binding.tvAdherenceValue.text = "${adherence.toInt()}%"
                    }
                }

                launch {
                    viewModel.attentionState.collect { info ->
                        binding.tvNeedsAttention.text = info.text
                        try {
                            binding.indicatorNeedsAttention.backgroundTintList = 
                                ColorStateList.valueOf(Color.parseColor(info.color))
                        } catch (e: Exception) {}
                    }
                }

                launch {
                    viewModel.nextMedication.collect { nextMed ->
                        if (nextMed != null) {
                            binding.ivNextMedIcon.visibility = View.VISIBLE
                            binding.tvNextMedPatient.text = nextMed.patientName
                            binding.tvNextMedName.text = nextMed.medicineName
                            binding.tvNextMedName.setTextColor(Color.WHITE)
                            binding.tvNextMedTime.text = nextMed.scheduledTime
                            binding.tvNextMedTime.setTextColor(Color.parseColor("#80FFFFFF"))
                            
                            binding.tvNextMedCountdown.text = when {
                                nextMed.diffMinutes <= 0 -> "Due now"
                                nextMed.diffMinutes < 60 -> "in ${nextMed.diffMinutes} min"
                                else -> "Next schedule"
                            }
                        } else {
                            // Phase 1: Calm Premium Empty State
                            binding.ivNextMedIcon.visibility = View.GONE
                            binding.tvNextMedPatient.text = "Today's schedule completed"
                            binding.tvNextMedName.text = "No pending medications for the rest of the day."
                            binding.tvNextMedName.setTextColor(Color.parseColor("#80FFFFFF"))
                            binding.tvNextMedName.textSize = 15f
                            binding.tvNextMedTime.text = ""
                            binding.tvNextMedCountdown.text = ""
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
