package com.medmonitor.ui.caregiver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.ui.caregiver.model.TimelineEvent
import com.medmonitor.databinding.ItemCareMonitorBinding
import com.medmonitor.databinding.ItemCareMonitorHeaderBinding

class CareMonitorAdapter(
    private val onMessageClick: (String, String, String) -> Unit, // phone, medName, time
    private val onCallClick: (String) -> Unit, // phone
    private val onTakenClick: (Int) -> Unit // position
) : ListAdapter<TimelineEvent, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_DOSE_EVENT = 1
        private const val TYPE_STOCK_EVENT = 2
        private const val TYPE_DUE_SOON_EVENT = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TimelineEvent.TimelineHeader -> TYPE_HEADER
            is TimelineEvent.DoseEvent -> TYPE_DOSE_EVENT
            is TimelineEvent.StockEvent -> TYPE_STOCK_EVENT
            is TimelineEvent.DueSoonEvent -> TYPE_DUE_SOON_EVENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemCareMonitorHeaderBinding.inflate(layoutInflater, parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_DOSE_EVENT, TYPE_STOCK_EVENT, TYPE_DUE_SOON_EVENT -> {
                val binding = ItemCareMonitorBinding.inflate(layoutInflater, parent, false)
                EntryViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as TimelineEvent.TimelineHeader)
            is EntryViewHolder -> holder.bind(item)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemCareMonitorHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: TimelineEvent.TimelineHeader) {
            binding.tvHeader.text = header.title
        }
    }

    inner class EntryViewHolder(private val binding: ItemCareMonitorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TimelineEvent) {
            // Reset visibilities for RecyclerView recycling safety
            binding.root.visibility = View.VISIBLE
            binding.actionLayout.visibility = View.VISIBLE
            binding.btnTaken.visibility = View.GONE
            binding.btnMessage.visibility = View.VISIBLE
            binding.btnCall.visibility = View.VISIBLE
            
            when (item) {
                is TimelineEvent.DoseEvent -> {
                    binding.tvPatientName.text = item.patient.patientName
                    binding.tvAvatar.text = item.patient.patientName.take(1).uppercase()
                    binding.tvMedDetails.text = "${item.medicineName} • ${item.relativeTime}"
                    binding.tvRelativeTime.text = if (item.status == "COMPLETED") "Taken" else item.relativeTime
                    binding.statusChip.text = item.status

                    val statusColor = when (item.status) {
                        "TAKEN", "COMPLETED" -> "#00E676" // Green
                        "DUE NOW" -> "#FFBF00" // Yellow
                        "MISSED" -> "#FF5252" // Red
                        else -> "#9AA4B2"
                    }
                    applyStatusColors(statusColor)
                    
                    // PHASE 2 — STATUS VISIBILITY LOGIC
                    when (item.status) {
                        "COMPLETED", "TAKEN" -> {
                            binding.btnTaken.visibility = View.GONE
                            binding.btnMessage.visibility = View.GONE
                            binding.btnCall.visibility = View.GONE
                            binding.actionLayout.visibility = View.GONE
                        }
                        "DUE NOW", "MISSED" -> {
                            binding.btnTaken.visibility = View.VISIBLE
                            binding.btnMessage.visibility = View.VISIBLE
                            binding.btnCall.visibility = View.VISIBLE
                            binding.actionLayout.visibility = View.VISIBLE
                        }
                        else -> {
                            // Default: Hide Mark Taken, Show Call/Message for other active states
                            binding.btnTaken.visibility = View.GONE
                            binding.btnMessage.visibility = View.VISIBLE
                            binding.btnCall.visibility = View.VISIBLE
                            binding.actionLayout.visibility = View.VISIBLE
                        }
                    }
                }
                is TimelineEvent.StockEvent -> {
                    binding.root.visibility = View.GONE
                }
                is TimelineEvent.DueSoonEvent -> {
                    binding.tvPatientName.text = item.patient.patientName
                    binding.tvAvatar.text = item.patient.patientName.take(1).uppercase()
                    binding.tvMedDetails.text = "${item.medicineName} • ${item.scheduledTime}"
                    binding.tvRelativeTime.text = item.timeUntilDue
                    
                    // PHASE 2 & 3: DYNAMIC CHIP TEXT AND COLOR
                    binding.statusChip.text = item.timeUntilDue.uppercase()
                    
                    val chipColor = when (item.timeUntilDue) {
                        "Upcoming" -> "#9AA4B2" // Neutral gray-blue
                        "Due Soon" -> "#FFBF00" // Yellow urgency
                        else -> "#9AA4B2"
                    }
                    applyStatusColors(chipColor)
                    
                    // STATUS: DUE SOON / UPCOMING -> SHOW: Message, Call; HIDE: Mark Taken
                    binding.btnTaken.visibility = View.GONE
                    binding.btnMessage.visibility = View.VISIBLE
                    binding.btnCall.visibility = View.VISIBLE
                    binding.actionLayout.visibility = View.VISIBLE
                }
                is TimelineEvent.TimelineHeader -> { }
            }

            binding.btnMessage.setOnClickListener {
                val phone = getPhone(item)
                val medName = getMedName(item)
                val time = getTime(item)
                if (phone != null && medName != null && time != null) {
                    onMessageClick(phone, medName, time)
                }
            }
            
            binding.btnCall.setOnClickListener {
                getPhone(item)?.let { onCallClick(it) }
            }

            binding.btnTaken.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onTakenClick(adapterPosition)
                }
            }
        }

        private fun getPhone(item: TimelineEvent): String? = when(item) {
            is TimelineEvent.DoseEvent -> item.patient.phoneNumber
            is TimelineEvent.DueSoonEvent -> item.patient.phoneNumber
            else -> null
        }

        private fun getMedName(item: TimelineEvent): String? = when(item) {
            is TimelineEvent.DoseEvent -> item.medicineName
            is TimelineEvent.DueSoonEvent -> item.medicineName
            else -> null
        }

        private fun getTime(item: TimelineEvent): String? = when(item) {
            is TimelineEvent.DoseEvent -> item.relativeTime
            is TimelineEvent.DueSoonEvent -> item.scheduledTime
            else -> null
        }

        private fun applyStatusColors(colorString: String) {
            try {
                val colorInt = Color.parseColor(colorString)
                binding.statusChip.setTextColor(colorInt)
                binding.statusRing.background?.setTint(colorInt)
            } catch (e: Exception) { }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TimelineEvent>() {
        override fun areItemsTheSame(oldItem: TimelineEvent, newItem: TimelineEvent): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TimelineEvent, newItem: TimelineEvent): Boolean = oldItem == newItem
    }
}
