package com.medmonitor.ui.medicine

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.MedicineType
import com.medmonitor.databinding.ItemMedicineFullBinding
import com.medmonitor.R
import com.medmonitor.util.MedicineStatusUtil
import java.text.SimpleDateFormat
import java.util.*

class MedicineAdapter(
    private var medicines: List<Medicine> = emptyList(),
    private val onEditClick: (Medicine) -> Unit = {},
    private val onDeleteClick: (Medicine, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<MedicineAdapter.ViewHolder>() {

    private val expandedMedicineIds = mutableSetOf<String>()

    class ViewHolder(val binding: ItemMedicineFullBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicineFullBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medicine = medicines.getOrNull(position) ?: return

        holder.itemView.alpha = 0f
        holder.itemView.animate().alpha(1f).setDuration(300).start()

        try {
            val isExpanded = expandedMedicineIds.contains(medicine.id)
            
            holder.binding.apply {
                tvMedName.text = medicine.name
                
                if (medicine.type == MedicineType.TABLET) {
                    ivMedIcon.setImageResource(R.drawable.ic_pill)
                } else {
                    ivMedIcon.setImageResource(R.drawable.ic_refill)
                }
                
                ivMedIcon.scaleX = 0.9f
                ivMedIcon.scaleY = 0.9f
                ivMedIcon.animate().scaleX(1f).scaleY(1f).setDuration(200).start()

                val context = root.context
                // Priority: Use displayStatus from ViewModel, fallback to utility with Context
                val status = medicine.displayStatus.ifEmpty { MedicineStatusUtil.getMedicineStatus(medicine, context) }
                
                // Format status for display (e.g., "DUE_NOW" -> "Due Now")
                val displayLabel = status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                statusBadge.text = displayLabel
                
                when (status) {
                    "COMPLETED" -> statusBadge.setTextColor(context.getColor(R.color.success))
                    "UPCOMING" -> statusBadge.setTextColor(context.getColor(R.color.primary))
                    "DUE_NOW" -> statusBadge.setTextColor(context.getColor(R.color.secondary))
                    "MISSED" -> statusBadge.setTextColor(context.getColor(R.color.error))
                    else -> statusBadge.setTextColor(context.getColor(R.color.primary))
                }
                
                tvDosageValue.text = "${medicine.dosageAmount.toInt()} ${medicine.unit}"
                tvFoodValue.text = medicine.foodTiming.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                val startDateStr = medicine.startDate?.let { sdf.format(Date(it)) } ?: "N/A"
                val endDateStr = medicine.endDate?.let { sdf.format(Date(it)) } ?: "N/A"
                tvDurationValue.text = "$startDateStr - $endDateStr"
                
                tvMorning.visibility = if (medicine.scheduleTimes.containsKey("MORNING")) View.VISIBLE else View.GONE
                tvMorning.text = "Morning · ${medicine.scheduleTimes["MORNING"]}"
                
                tvAfternoon.visibility = if (medicine.scheduleTimes.containsKey("AFTERNOON")) View.VISIBLE else View.GONE
                tvAfternoon.text = "Noon · ${medicine.scheduleTimes["AFTERNOON"]}"
                
                tvNight.visibility = if (medicine.scheduleTimes.containsKey("NIGHT")) View.VISIBLE else View.GONE
                tvNight.text = "Night · ${medicine.scheduleTimes["NIGHT"]}"

                layoutExpanded.visibility = if (isExpanded) View.VISIBLE else View.GONE
                ivExpandArrow.rotation = if (isExpanded) 180f else 0f
                
                layoutHeader.setOnClickListener {
                    it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }.start()

                    val currentPos = holder.adapterPosition
                    if (currentPos == RecyclerView.NO_POSITION) return@setOnClickListener
                    
                    if (isExpanded) {
                        expandedMedicineIds.remove(medicine.id)
                    } else {
                        expandedMedicineIds.add(medicine.id)
                    }
                    notifyItemChanged(currentPos)
                }

                btnEdit.setOnClickListener {
                    it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }.start()
                    onEditClick(medicine)
                }
                
                btnDelete.setOnClickListener {
                    it.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }.start()
                    val currentPos = holder.adapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        onDeleteClick(medicine, currentPos)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MED_LIST_UI", "Error binding medicine at position $position", e)
        }
    }

    override fun getItemCount() = medicines.size

    fun getMedicinesList(): List<Medicine> = medicines

    fun updateData(newMedicines: List<Medicine>) {
        medicines = newMedicines
        notifyDataSetChanged()
    }
}
