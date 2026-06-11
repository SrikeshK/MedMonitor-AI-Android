package com.medmonitor.ui.caregiver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.R
import com.medmonitor.databinding.ItemCaregiverMedicineV2Binding
import com.medmonitor.databinding.ItemCaregiverPatientHeaderBinding
import com.medmonitor.ui.caregiver.viewmodel.MedicineWithStatus
import com.medmonitor.ui.caregiver.viewmodel.PatientWithMedicines

class CaregiverMedicinesAdapterV2(
    private val onCallClick: (String) -> Unit,
    private val onMessageClick: (String, String) -> Unit,
    private val onEditClick: (MedicineWithStatus) -> Unit,
    private val onDeleteClick: (MedicineWithStatus) -> Unit
) : ListAdapter<PatientWithMedicines, CaregiverMedicinesAdapterV2.PatientViewHolder>(DiffCallback()) {

    private val expandedPatientIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val binding = ItemCaregiverPatientHeaderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PatientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PatientViewHolder(private val binding: ItemCaregiverPatientHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PatientWithMedicines) {
            binding.tvPatientName.text = item.patient.patientName
            binding.tvPatientPhone.text = item.patient.phoneNumber

            val isExpanded = expandedPatientIds.contains(item.patient.patientId)
            
            // Update expansion state UI
            binding.rvPatientMedicines.visibility = if (isExpanded && item.activeMedicines.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvNoMedicines.visibility = if (isExpanded && item.activeMedicines.isEmpty()) View.VISIBLE else View.GONE
            binding.btnExpand.rotation = if (isExpanded) 180f else 0f

            // Setup nested RecyclerView
            if (isExpanded) {
                val nestedAdapter = NestedMedicineAdapter(
                    item.patient.phoneNumber,
                    onCallClick,
                    onMessageClick,
                    onEditClick,
                    onDeleteClick
                )
                binding.rvPatientMedicines.layoutManager = LinearLayoutManager(binding.root.context)
                binding.rvPatientMedicines.adapter = nestedAdapter
                nestedAdapter.submitList(item.activeMedicines + item.pastMedicines)
            }

            binding.patientCardContent.setOnClickListener {
                toggleExpansion(item.patient.patientId)
            }

            binding.btnExpand.setOnClickListener {
                toggleExpansion(item.patient.patientId)
            }
        }

        private fun toggleExpansion(patientId: String) {
            if (expandedPatientIds.contains(patientId)) {
                expandedPatientIds.remove(patientId)
            } else {
                expandedPatientIds.add(patientId)
            }
            notifyItemChanged(adapterPosition)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PatientWithMedicines>() {
        override fun areItemsTheSame(oldItem: PatientWithMedicines, newItem: PatientWithMedicines): Boolean {
            return oldItem.patient.id == newItem.patient.id
        }

        override fun areContentsTheSame(oldItem: PatientWithMedicines, newItem: PatientWithMedicines): Boolean {
            return oldItem == newItem
        }
    }

    // Nested Adapter for Medicines
    private class NestedMedicineAdapter(
        private val patientPhone: String,
        private val onCallClick: (String) -> Unit,
        private val onMessageClick: (String, String) -> Unit,
        private val onEditClick: (MedicineWithStatus) -> Unit,
        private val onDeleteClick: (MedicineWithStatus) -> Unit
    ) : ListAdapter<MedicineWithStatus, NestedMedicineAdapter.MedicineViewHolder>(MedicineDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
            val binding = ItemCaregiverMedicineV2Binding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return MedicineViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class MedicineViewHolder(private val binding: ItemCaregiverMedicineV2Binding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: MedicineWithStatus) {
                binding.tvMedicineName.text = item.medicine.medicineName
                binding.tvDosage.text = "Dosage: ${item.medicine.dosage}"
                binding.tvSchedule.text = item.medicine.scheduleTimes.values.joinToString(", ")
                binding.statusChip.text = item.status

                when (item.status) {
                    "DUE_NOW" -> binding.statusChip.setBackgroundResource(R.drawable.bg_status_delayed)
                    "MISSED" -> binding.statusChip.setBackgroundResource(R.drawable.bg_status_missed)
                    "TAKEN" -> binding.statusChip.setBackgroundResource(R.drawable.bg_status_taken)
                    else -> binding.statusChip.setBackgroundResource(R.drawable.bg_status_badge)
                }

                binding.btnCall.setOnClickListener { onCallClick(patientPhone) }
                binding.btnMessage.setOnClickListener { onMessageClick(patientPhone, item.medicine.medicineName) }
                binding.btnEdit.setOnClickListener { onEditClick(item) }
                binding.btnDelete.setOnClickListener { onDeleteClick(item) }
            }
        }

        class MedicineDiffCallback : DiffUtil.ItemCallback<MedicineWithStatus>() {
            override fun areItemsTheSame(oldItem: MedicineWithStatus, newItem: MedicineWithStatus): Boolean {
                return oldItem.medicine.id == newItem.medicine.id
            }

            override fun areContentsTheSame(oldItem: MedicineWithStatus, newItem: MedicineWithStatus): Boolean {
                return oldItem == newItem
            }
        }
    }
}
