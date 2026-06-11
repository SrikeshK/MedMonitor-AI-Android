package com.medmonitor.ui.notifications

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.R
import com.medmonitor.databinding.ItemNotificationActionBinding
import com.medmonitor.databinding.ItemNotificationHeaderBinding
import com.medmonitor.databinding.ItemNotificationEmptyBinding

class NotificationsAdapter(private val onConfirmClick: (AlertItem) -> Unit) :
    ListAdapter<NotificationListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationListItem.Header -> TYPE_HEADER
            is NotificationListItem.Alert -> TYPE_ALERT
            is NotificationListItem.EmptyState -> TYPE_EMPTY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemNotificationHeaderBinding.inflate(inflater, parent, false))
            TYPE_ALERT -> AlertViewHolder(ItemNotificationActionBinding.inflate(inflater, parent, false))
            TYPE_EMPTY -> EmptyViewHolder(ItemNotificationEmptyBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as NotificationListItem.Header)
            is AlertViewHolder -> holder.bind((item as NotificationListItem.Alert).alert)
            is EmptyViewHolder -> holder.bind(item as NotificationListItem.EmptyState)
        }
    }

    inner class HeaderViewHolder(private val binding: ItemNotificationHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NotificationListItem.Header) {
            binding.tvHeaderTitle.text = item.title
            
            val (iconRes, colorHex) = when (item.status) {
                AlertStatus.MISSED -> R.drawable.ic_warning to "#FF6B6B"
                AlertStatus.DUE -> R.drawable.ic_clock to "#00E5FF"
                AlertStatus.UPCOMING -> R.drawable.ic_calendar to "#008080"
                else -> R.drawable.ic_notification to "#FFFFFF"
            }
            binding.ivHeaderIcon.setImageResource(iconRes)
            binding.ivHeaderIcon.setColorFilter(Color.parseColor(colorHex))

            // REFINED GLASS HEADER STYLING
            when (item.status) {
                AlertStatus.MISSED -> {
                    binding.headerCard.setCardBackgroundColor(Color.parseColor("#1A1A2E"))
                    binding.headerCard.strokeColor = Color.parseColor("#4DFF5252") // 30% Alpha
                }
                AlertStatus.DUE -> {
                    binding.headerCard.setCardBackgroundColor(Color.parseColor("#121A2F"))
                    binding.headerCard.strokeColor = Color.parseColor("#4D00E5FF") // 30% Alpha
                }
                AlertStatus.UPCOMING -> {
                    binding.headerCard.setCardBackgroundColor(Color.parseColor("#0B1221"))
                    binding.headerCard.strokeColor = Color.parseColor("#2A3A5F")
                }
                else -> {
                    binding.headerCard.setCardBackgroundColor(Color.parseColor("#121A2F"))
                    binding.headerCard.strokeColor = Color.TRANSPARENT
                }
            }
        }
    }

    inner class AlertViewHolder(private val binding: ItemNotificationActionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AlertItem) {
            binding.medicineName.text = item.medicine.name
            binding.timeText.text = item.time
            binding.statusText.text = when (item.status) {
                AlertStatus.DUE -> "Due Now"
                AlertStatus.MISSED -> "Missed Dose"
                AlertStatus.UPCOMING -> "Upcoming"
                else -> ""
            }

            // UI Reset & Base Styling
            binding.confirmBtn.visibility = View.VISIBLE
            binding.upcomingSpacer.visibility = View.GONE
            binding.timeText.setTextColor(Color.parseColor("#9CA3AF"))
            binding.statusText.setTextColor(Color.parseColor("#9CA3AF"))
            binding.statusText.visibility = View.VISIBLE
            
            // Refined Icon Tint based on status
            val statusColor = when (item.status) {
                AlertStatus.MISSED -> "#FF6B6B"
                AlertStatus.DUE -> "#00E5FF"
                else -> "#00E5FF"
            }
            binding.medicineIcon.setColorFilter(Color.parseColor(statusColor))

            when (item.status) {
                AlertStatus.DUE -> {
                    binding.statusText.setTextColor(Color.parseColor("#00E5FF"))
                    binding.confirmBtn.text = "Confirm"
                }
                AlertStatus.MISSED -> {
                    binding.timeText.setTextColor(Color.parseColor("#FF6B6B"))
                    binding.statusText.setTextColor(Color.parseColor("#FF6B6B"))
                    binding.confirmBtn.text = "Confirm Now"
                }
                AlertStatus.UPCOMING -> {
                    binding.confirmBtn.visibility = View.GONE
                    binding.statusText.visibility = View.GONE
                    binding.upcomingSpacer.visibility = View.VISIBLE // Dynamic balance
                }
                else -> {}
            }

            binding.confirmBtn.setOnClickListener { onConfirmClick(item) }
        }
    }

    inner class EmptyViewHolder(private val binding: ItemNotificationEmptyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NotificationListItem.EmptyState) {
            binding.tvEmptyMessage.text = item.message
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NotificationListItem>() {
        override fun areItemsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
            return oldItem == newItem
        }
        override fun areContentsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ALERT = 1
        private const val TYPE_EMPTY = 2
    }
}
