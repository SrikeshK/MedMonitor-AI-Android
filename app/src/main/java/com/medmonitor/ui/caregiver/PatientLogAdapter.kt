package com.medmonitor.ui.caregiver

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.PatientDoseLog
import com.medmonitor.databinding.ItemMedicineCompactBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PatientLogAdapter : ListAdapter<PatientDoseLog, PatientLogAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicineCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMedicineCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: PatientDoseLog) {
            // 🧩 SAFE FIX: Display patient name instead of ID
            // Fallback for old logs: show shortened ID if name is missing or default
            val displayName = if (log.patientName.isNotEmpty() && log.patientName != "Unknown Patient") {
                log.patientName
            } else if (log.patientId.isNotEmpty()) {
                "Patient: ...${log.patientId.takeLast(4)}"
            } else {
                "Unknown Patient"
            }
            
            binding.tvMedicineName.text = displayName
            
            val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            val dateStr = sdf.format(log.timestamp.toDate())
            
            // 🧩 SAFE FIX: Show medicine name in subtitle if available
            val medName = if (log.medicineName.isNotEmpty()) log.medicineName else "Medicine"
            binding.tvDosageInfo.text = "$medName ${log.status.lowercase()} at $dateStr"

            binding.statusBadge.text = log.status
            
            if (log.status == "MISSED") {
                binding.statusBadge.setTextColor(android.graphics.Color.parseColor("#FF5252"))
            } else {
                binding.statusBadge.setTextColor(android.graphics.Color.parseColor("#00E676"))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PatientDoseLog>() {
        override fun areItemsTheSame(oldItem: PatientDoseLog, newItem: PatientDoseLog): Boolean {
            return oldItem.logId == newItem.logId
        }
        override fun areContentsTheSame(oldItem: PatientDoseLog, newItem: PatientDoseLog): Boolean {
            return oldItem == newItem
        }
    }
}
