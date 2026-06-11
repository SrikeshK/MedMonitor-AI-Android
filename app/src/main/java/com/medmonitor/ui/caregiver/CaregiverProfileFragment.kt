package com.medmonitor.ui.caregiver

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.databinding.FragmentCaregiverProfileBinding
import com.medmonitor.ui.ModeSelectionActivity
import com.medmonitor.ui.auth.LoginActivity
import com.medmonitor.ui.caregiver.viewmodel.CaregiverDashboardViewModelV2
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CaregiverProfileFragment : Fragment() {
    private var _binding: FragmentCaregiverProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsManager: SettingsManager
    
    private val viewModel by viewModels<CaregiverDashboardViewModelV2>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCaregiverProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())

        setupUserInfo()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupUserInfo() {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName ?: "Caregiver"
        binding.tvCaregiverName.text = name
        binding.tvCaregiverEmail.text = user?.email ?: "No email"
        binding.tvAvatarLetter.text = name.take(1).uppercase()
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finishAffinity()
        }

        binding.cardSwitchMode.setOnClickListener {
            settingsManager.setUserMode("PATIENT")
            startActivity(Intent(requireContext(), ModeSelectionActivity::class.java))
            requireActivity().finishAffinity()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.totalPatients.collect { count ->
                        binding.tvTotalPatients.text = count.toString()
                    }
                }

                launch {
                    viewModel.missedCount.collect { count ->
                        binding.tvMissedCount.text = count.toString()
                    }
                }

                launch {
                    viewModel.caregiverAdherence.collect { adherence ->
                        binding.tvAdherencePercent.text = "${adherence.toInt()}%"
                    }
                }

                launch {
                    viewModel.attentionState.collect { info ->
                        binding.tvStatusMessage.text = info.text
                        try {
                            binding.indicatorStatus.backgroundTintList = 
                                ColorStateList.valueOf(info.color.toColorInt())
                        } catch (e: Exception) {}
                    }
                }

                launch {
                    viewModel.patients.collect { patients ->
                        updatePatientsPreview(patients)
                    }
                }
            }
        }
    }

    private fun updatePatientsPreview(patients: List<CaregiverPatient>) {
        binding.layoutPatientsList.removeAllViews()
        val displayPatients = patients.take(3)
        
        if (displayPatients.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "No active patients"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(32, 16, 32, 16)
                textSize = 14f
            }
            binding.layoutPatientsList.addView(emptyView)
            return
        }

        displayPatients.forEachIndexed { index, patient ->
            val patientView = TextView(requireContext()).apply {
                text = "👤 ${patient.patientName}"
                setTextColor(Color.WHITE)
                setPadding(32, 16, 32, 16)
                textSize = 15f
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            binding.layoutPatientsList.addView(patientView)
            
            // Add a divider
            if (index < displayPatients.size - 1) {
                val divider = View(requireContext()).apply {
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    )
                    lp.setMargins(32, 0, 32, 0)
                    layoutParams = lp
                    setBackgroundColor(ContextCompat.getColor(context, R.color.glass_stroke))
                }
                binding.layoutPatientsList.addView(divider)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
