package com.medmonitor.ui.caregiver

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.caregiver.CaregiverPatient
import com.medmonitor.databinding.ItemPatientSelectBinding

class PatientSelectAdapter(private val onAddClick: (CaregiverPatient) -> Unit) :
    ListAdapter<CaregiverPatient, PatientSelectAdapter.ViewHolder>(PatientDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPatientSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(patient: CaregiverPatient) {
            binding.tvPatientName.text = patient.patientName
            binding.tvPatientPhone.text = patient.phoneNumber
            binding.tvInitial.text = patient.patientName.take(1).uppercase()
            binding.btnAdd.setOnClickListener { onAddClick(patient) }
        }
    }

    class PatientDiffCallback : DiffUtil.ItemCallback<CaregiverPatient>() {
        override fun areItemsTheSame(oldItem: CaregiverPatient, newItem: CaregiverPatient): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CaregiverPatient, newItem: CaregiverPatient): Boolean {
            return oldItem == newItem
        }
    }
}
