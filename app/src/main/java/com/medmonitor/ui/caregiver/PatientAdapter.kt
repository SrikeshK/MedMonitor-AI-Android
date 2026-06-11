package com.medmonitor.ui.caregiver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.Patient
import com.medmonitor.databinding.ItemPatientBinding
import com.medmonitor.util.InventoryState
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.getInventoryState
import com.medmonitor.util.isMedicineActive

class PatientAdapter(private val onPatientClick: (Patient) -> Unit) :
    ListAdapter<PatientAdapter.PatientWithStatus, PatientAdapter.PatientViewHolder>(PatientDiffCallback()) {

    data class PatientWithStatus(
        val patient: Patient,
        val statusSummary: String,
        val statusColor: String = "#00E676",
        val priority: Int = 4 // Default to STABLE
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatientViewHolder {
        val binding = ItemPatientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PatientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PatientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PatientViewHolder(private val binding: ItemPatientBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PatientWithStatus) {
            val patient = item.patient
            binding.tvPatientName.text = patient.name
            binding.tvPatientPhone.text = patient.phone
            binding.tvInitial.text = patient.name.take(1).uppercase()
            
            binding.tvNextMedicine.text = item.statusSummary
            try {
                binding.tvNextMedicine.setTextColor(Color.parseColor(item.statusColor))
            } catch (e: Exception) {
                binding.tvNextMedicine.setTextColor(Color.WHITE)
            }

            binding.root.setOnClickListener { onPatientClick(patient) }
        }
    }

    class PatientDiffCallback : DiffUtil.ItemCallback<PatientWithStatus>() {
        override fun areItemsTheSame(oldItem: PatientWithStatus, newItem: PatientWithStatus): Boolean {
            return oldItem.patient.id == newItem.patient.id
        }

        override fun areContentsTheSame(oldItem: PatientWithStatus, newItem: PatientWithStatus): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        /**
         * 🧩 PHASE 3A: Smart Patient Status Engine
         * Priority Order:
         * 1. MISSED (0)
         * 2. DUE SOON (1)
         * 3. LOW STOCK (2)
         * 4. STABLE (3)
         * 5. NO ACTIVE MEDICINES (4)
         */
        fun calculateStatus(patient: Patient, medicines: List<Medicine>): PatientWithStatus {
            val now = System.currentTimeMillis()
            
            // 🧩 ONLY evaluate active medicines
            val activeMeds = medicines.filter { it.isMedicineActive(now) }
            
            if (activeMeds.isEmpty()) {
                return PatientWithStatus(patient, "⚪ No active medicines", "#BDBDBD", priority = 5)
            }

            var hasMissed = false
            var hasDueNow = false
            var hasDueSoon = false
            var hasLowStock = false
            var nextMedTime = Long.MAX_VALUE

            activeMeds.forEach { med ->
                val normalized = med.normalize()
                if (getInventoryState(normalized) != InventoryState.NORMAL) {
                    hasLowStock = true
                }

                normalized.scheduleTimes.forEach { (slot, time) ->
                    val status = MedicineStatusUtil.getSlotStatus(
                        time, 
                        normalized.slotStatus[slot.uppercase()], 
                        now, 
                        normalized.lastUpdatedTime, 
                        normalized.createdAt
                    )
                    
                    val millis = MedicineStatusUtil.parseTimeToTodayMillis(time, now)

                    when (status) {
                        "MISSED" -> hasMissed = true
                        "DUE_NOW" -> hasDueNow = true
                        "UPCOMING" -> {
                            if (millis > now) {
                                if ((millis - now) <= 60 * 60 * 1000L) {
                                    hasDueSoon = true
                                }
                                if (millis < nextMedTime) {
                                    nextMedTime = millis
                                }
                            }
                        }
                    }
                }
            }

            return when {
                hasMissed -> PatientWithStatus(patient, "🔴 Missed medication", "#FF5252", priority = 1)
                hasDueNow || hasDueSoon -> {
                    val summary = if (hasDueNow) "🟡 Medicine due now" else "🟡 Dose due soon"
                    PatientWithStatus(patient, summary, "#FFBF00", priority = 2)
                }
                hasLowStock -> PatientWithStatus(patient, "🟠 Low stock", "#FF9800", priority = 3)
                else -> PatientWithStatus(patient, "🟢 Stable today", "#00E676", priority = 4)
            }
        }
    }
}
