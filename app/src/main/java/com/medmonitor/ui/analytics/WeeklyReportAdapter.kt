package com.medmonitor.ui.analytics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.R
import com.medmonitor.data.model.DoseLog
import com.medmonitor.data.model.DoseStatus
import com.medmonitor.databinding.ItemWeeklyLogBinding
import java.text.SimpleDateFormat
import java.util.Locale

class WeeklyReportAdapter : RecyclerView.Adapter<WeeklyReportAdapter.ViewHolder>() {

    private var logs: List<DoseLog> = emptyList()

    fun submitList(newList: List<DoseLog>) {
        logs = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeeklyLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount() = logs.size

    class ViewHolder(private val binding: ItemWeeklyLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(log: DoseLog) {
            binding.tvMedicineName.text = log.medicineName
            
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(log.timestamp.toDate())
            val timeStr = timeFormat.format(log.timestamp.toDate())

            val slotDisplay = log.slotName.lowercase().replaceFirstChar { it.uppercase() }
            binding.tvSlotAndTime.text = "$slotDisplay Dose • $timeStr"
            binding.tvDate.text = dateStr
            binding.tvVerificationMethod.text = "via ${log.verificationMethod.name}"

            binding.tvStatus.text = log.status.name
            
            val context = binding.root.context
            when (log.status) {
                DoseStatus.TAKEN -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_taken)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.success))
                }
                DoseStatus.MISSED -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_missed)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.error))
                }
                DoseStatus.DELAYED -> {
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_status_delayed)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                }
                DoseStatus.OUT_OF_STOCK -> {
                    // 🧩 PHASE 3: Neutral state for supply issues
                    binding.tvStatus.text = "Out of Stock"
                    binding.tvStatus.setBackgroundResource(R.drawable.status_badge_bg)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }
        }
    }
}
