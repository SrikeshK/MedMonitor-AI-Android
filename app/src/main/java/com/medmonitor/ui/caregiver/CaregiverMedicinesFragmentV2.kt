package com.medmonitor.ui.caregiver

import android.content.Intent
import android.net.Uri
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medmonitor.databinding.FragmentCaregiverMedicinesV2Binding
import com.medmonitor.ui.caregiver.viewmodel.CaregiverMedicinesUiState
import com.medmonitor.ui.caregiver.viewmodel.CaregiverMedicinesViewModelV2
import com.medmonitor.ui.caregiver.viewmodel.MedicineWithStatus
import kotlinx.coroutines.launch

class CaregiverMedicinesFragmentV2 : Fragment() {

    private var _binding: FragmentCaregiverMedicinesV2Binding? = null
    private val binding get() = _binding!!
    private val viewModel: CaregiverMedicinesViewModelV2 by viewModels()
    private lateinit var adapter: CaregiverMedicinesAdapterV2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCaregiverMedicinesV2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeUiState()
    }

    private fun setupRecyclerView() {
        adapter = CaregiverMedicinesAdapterV2(
            onCallClick = { phone -> makeCall(phone) },
            onMessageClick = { phone, medName -> sendMessage(phone, medName) },
            onEditClick = { medWithStatus -> editMedicine(medWithStatus) },
            onDeleteClick = { medWithStatus -> showDeleteConfirmation(medWithStatus) }
        )
        binding.rvMedicines.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CaregiverMedicinesFragmentV2.adapter
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is CaregiverMedicinesUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is CaregiverMedicinesUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            if (state.groupedData.isEmpty()) {
                                binding.tvEmptyState.visibility = View.VISIBLE
                            } else {
                                binding.tvEmptyState.visibility = View.GONE
                                // Pass the grouped data directly to the adapter without flattening
                                adapter.submitList(state.groupedData)
                            }
                        }
                        is CaregiverMedicinesUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not initiate call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage(phoneNumber: String, medicineName: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", "Reminder: Please take your medicine: $medicineName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open SMS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editMedicine(medicine: MedicineWithStatus) {
        val intent = Intent(requireContext(), AddCaregiverMedicineActivityV2::class.java).apply {
            putExtra("MEDICINE_ID", medicine.medicine.id)
            putExtra("PATIENT_ID", medicine.medicine.patientId)
            putExtra("IS_EDIT", true)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmation(medicine: MedicineWithStatus) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Medicine")
            .setMessage("Are you sure you want to delete ${medicine.medicine.medicineName}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteMedicine(medicine.medicine)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
