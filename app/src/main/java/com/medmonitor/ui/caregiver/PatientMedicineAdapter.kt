package com.medmonitor.ui.caregiver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.PatientMedicine
import com.medmonitor.databinding.ItemCareMonitorHeaderBinding
import com.medmonitor.databinding.ItemPatientMedicineBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class PatientMedicineItem {
    data class Header(val title: String) : PatientMedicineItem()
    data class Entry(val medicine: PatientMedicine) : PatientMedicineItem()
}

class PatientMedicineAdapter(
    private val onRemindClick: (PatientMedicine) -> Unit
) : ListAdapter<PatientMedicineItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PatientMedicineItem.Header -> TYPE_HEADER
            is PatientMedicineItem.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val binding = ItemCareMonitorHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemPatientMedicineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            EntryViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is PatientMedicineItem.Header) {
            holder.bind(item)
        } else if (holder is EntryViewHolder && item is PatientMedicineItem.Entry) {
            holder.bind(item.medicine)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemCareMonitorHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: PatientMedicineItem.Header) {
            binding.tvHeader.text = header.title
        }
    }

    inner class EntryViewHolder(private val binding: ItemPatientMedicineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(medicine: PatientMedicine) {
            binding.tvMedicineName.text = medicine.name
            
            val timesText = medicine.scheduleTimes.entries.joinToString(" • ") { "${it.key} (${it.value})" }
            
            binding.tvDosageInfo.text = if (medicine.dosage.isNotEmpty()) {
                "${medicine.dosage} • $timesText"
            } else {
                timesText
            }

            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            val dateRange = if (medicine.startDate != null && medicine.endDate != null) {
                "${sdf.format(Date(medicine.startDate))} - ${sdf.format(Date(medicine.endDate))}"
            } else {
                "No date range"
            }
            
            binding.tvDosageInfo.text = "${binding.tvDosageInfo.text}\n$dateRange"
            
            val now = System.currentTimeMillis()
            val isExpired = medicine.endDate != null && now > medicine.endDate
            if (isExpired) {
                binding.root.alpha = 0.6f
                binding.tvMedicineName.text = "${medicine.name} (Past)"
                binding.btnRemind.visibility = View.GONE
            } else {
                binding.root.alpha = 1.0f
                binding.btnRemind.visibility = View.VISIBLE
            }
            
            binding.btnRemind.setOnClickListener {
                onRemindClick(medicine)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PatientMedicineItem>() {
        override fun areItemsTheSame(oldItem: PatientMedicineItem, newItem: PatientMedicineItem): Boolean {
            return if (oldItem is PatientMedicineItem.Header && newItem is PatientMedicineItem.Header) {
                oldItem.title == newItem.title
            } else if (oldItem is PatientMedicineItem.Entry && newItem is PatientMedicineItem.Entry) {
                oldItem.medicine.medicineId == newItem.medicine.medicineId
            } else false
        }
        override fun areContentsTheSame(oldItem: PatientMedicineItem, newItem: PatientMedicineItem): Boolean {
            return oldItem == newItem
        }
    }
}
