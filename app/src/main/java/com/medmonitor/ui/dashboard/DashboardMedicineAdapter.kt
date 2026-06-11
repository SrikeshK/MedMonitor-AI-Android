package com.medmonitor.ui.dashboard

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.Medicine
import com.medmonitor.databinding.ItemDoseSlotBinding
import com.medmonitor.databinding.ItemMedicineCompactBinding
import com.medmonitor.util.MedicineStatusUtil
import com.medmonitor.util.InventoryState
import com.medmonitor.util.getInventoryState
import java.text.SimpleDateFormat
import java.util.*

class DashboardMedicineAdapter(private var medicines: List<Medicine>) : RecyclerView.Adapter<DashboardMedicineAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMedicineCompactBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicineCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val medicine = medicines.getOrNull(position) ?: return

            holder.binding.apply {
                tvMedicineName.text = medicine.name
                
                val dosageBase = "${medicine.dosageAmount.toInt()} ${medicine.unit}"
                
                // 🧩 PHASE 2: Unified Inventory Badges
                val inventoryState = getInventoryState(medicine)
                applyInventoryBadge(inventoryBadge, inventoryState)

                if (MedicineStatusUtil.isMultiDose(medicine)) {
                    val foodTimingDisplay = medicine.foodTiming.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    tvDosageInfo.text = "$dosageBase • $foodTimingDisplay"

                    val sortedSlots = medicine.scheduleTimes.toList().sortedBy { it.second }
                    
                    divider.visibility = View.VISIBLE
                    doseTimelineContainer.visibility = View.VISIBLE
                    doseTimelineContainer.removeAllViews()

                    sortedSlots.forEach { (slotName, timeStr) ->
                        val normalizedSlot = MedicineStatusUtil.normalizeSlot(slotName)
                        val slotState = medicine.slotStatus[normalizedSlot]
                            ?: medicine.slotStatus[slotName]
                            ?: medicine.slotStatus[slotName.uppercase()]
                            ?: medicine.slotStatus[slotName.lowercase()]

                        val status = MedicineStatusUtil.getSlotStatus(
                            timeStr,
                            slotState,
                            System.currentTimeMillis(),
                            medicine.lastUpdatedTime,
                            medicine.createdAt
                        )
                        
                        val slotBinding = ItemDoseSlotBinding.inflate(
                            LayoutInflater.from(root.context),
                            doseTimelineContainer,
                            false
                        )
                        slotBinding.tvSlotName.text = slotName.lowercase().replaceFirstChar { it.uppercase() }
                        slotBinding.tvSlotTime.text = formatTo12Hr(timeStr)
                        updateSlotUI(slotBinding, status)
                        doseTimelineContainer.addView(slotBinding.root)
                    }

                    applyStatusToBadge(statusBadge, medicine.displayStatus)

                } else {
                    val timeStr = medicine.scheduleTimes.values.firstOrNull()
                    val safeTime = if (!timeStr.isNullOrBlank()) formatTo12Hr(timeStr) else ""

                    tvDosageInfo.text = if (safeTime.isNotEmpty()) {
                        "$dosageBase  •  $safeTime"
                    } else {
                        dosageBase
                    }

                    divider.visibility = View.GONE
                    doseTimelineContainer.visibility = View.GONE
                    
                    applyStatusToBadge(statusBadge, medicine.displayStatus)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardAdapter", "Error binding medicine at position $position", e)
        }
    }

    private fun applyInventoryBadge(badge: android.widget.TextView, state: InventoryState) {
        when (state) {
            InventoryState.EMPTY -> {
                badge.text = "EMPTY"
                badge.setTextColor(Color.parseColor("#FF6B6B"))
                badge.visibility = View.VISIBLE
            }
            InventoryState.CRITICAL -> {
                badge.text = "CRITICAL"
                badge.setTextColor(Color.parseColor("#FF6B6B"))
                badge.visibility = View.VISIBLE
            }
            InventoryState.LOW -> {
                badge.text = "LOW STOCK"
                badge.setTextColor(Color.parseColor("#FFBF00"))
                badge.visibility = View.VISIBLE
            }
            InventoryState.NORMAL -> {
                badge.visibility = View.GONE
            }
        }
    }

    private fun applyStatusToBadge(badge: android.widget.TextView, status: String) {
        val displayLabel = status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        badge.text = displayLabel
        
        when(status) {
            "MISSED" -> badge.setTextColor(Color.parseColor("#FF6B6B"))
            "COMPLETED" -> badge.setTextColor(Color.parseColor("#4ADE80"))
            "DUE_NOW" -> badge.setTextColor(Color.parseColor("#00E5FF"))
            "IN_PROGRESS" -> badge.setTextColor(Color.parseColor("#00E5FF"))
            "UPCOMING" -> badge.setTextColor(Color.parseColor("#3B82F6"))
            "PARTIAL" -> badge.setTextColor(Color.parseColor("#F59E0B"))
            else -> badge.setTextColor(Color.parseColor("#00E5FF"))
        }
    }

    private fun updateSlotUI(binding: ItemDoseSlotBinding, status: String) {
        when (status) {
            "COMPLETED" -> {
                binding.statusDot.setBackgroundColor(Color.parseColor("#4ADE80"))
                binding.tvSlotStatus.text = "Completed"
                binding.tvSlotStatus.setTextColor(Color.parseColor("#4ADE80"))
            }
            "MISSED" -> {
                binding.statusDot.setBackgroundColor(Color.parseColor("#FF6B6B"))
                binding.tvSlotStatus.text = "Missed"
                binding.tvSlotStatus.setTextColor(Color.parseColor("#FF6B6B"))
            }
            "DUE_NOW" -> {
                binding.statusDot.setBackgroundColor(Color.parseColor("#00E5FF"))
                binding.tvSlotStatus.text = "Due Now"
                binding.tvSlotStatus.setTextColor(Color.parseColor("#00E5FF"))
            }
            "UPCOMING" -> {
                binding.statusDot.setBackgroundColor(Color.parseColor("#3B82F6"))
                binding.tvSlotStatus.text = "Upcoming"
                binding.tvSlotStatus.setTextColor(Color.parseColor("#3B82F6"))
            }
        }
    }

    private fun formatTo12Hr(timeStr: String): String {
        if (timeStr.isEmpty()) return "Anytime"
        val outFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        return try {
            val date = SimpleDateFormat("hh:mm a", Locale.getDefault()).parse(timeStr)
            if (date != null) return outFormat.format(date) else timeStr
        } catch (_: Exception) {
            try {
                val date = SimpleDateFormat("HH:mm", Locale.getDefault()).parse(timeStr)
                if (date != null) outFormat.format(date) else timeStr
            } catch (_: Exception) {
                timeStr
            }
        }
    }

    override fun getItemCount() = medicines.size

    fun updateData(newMedicines: List<Medicine>) {
        medicines = newMedicines
        notifyDataSetChanged()
    }
}
