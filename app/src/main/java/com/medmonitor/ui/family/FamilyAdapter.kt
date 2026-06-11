package com.medmonitor.ui.family

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.FamilyMember
import com.medmonitor.databinding.ItemFamilyMemberBinding
import java.text.SimpleDateFormat
import java.util.*

class FamilyAdapter(
    private var members: List<FamilyMember>,
    private val onDeleteClick: (FamilyMember) -> Unit,
    private val onEditClick: (FamilyMember) -> Unit
) : RecyclerView.Adapter<FamilyAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFamilyMemberBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFamilyMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        holder.binding.apply {
            tvMemberName.text = member.name
            tvRelation.text = member.relation
            tvPhone.text = member.phone
            
            if (member.email.isNotEmpty()) {
                tvEmail.visibility = android.view.View.VISIBLE
                tvEmail.text = member.email
            } else {
                tvEmail.visibility = android.view.View.GONE
            }

            if (member.backupPhone.isNotEmpty()) {
                tvBackup.visibility = android.view.View.VISIBLE
                tvBackup.text = "🚨 Backup: ${member.backupPhone}"
                divider.visibility = android.view.View.VISIBLE
            } else {
                tvBackup.visibility = android.view.View.GONE
                divider.visibility = android.view.View.GONE
            }

            // Status Indicator Logic
            val statusColor = when (member.status) {
                "Active" -> "#22C55E"
                "Not reachable" -> "#EF4444"
                else -> "#9CA3AF" // Unknown
            }
            statusIndicator.background.setTint(android.graphics.Color.parseColor(statusColor))

            // Last Alert Time
            if (member.lastAlertTime != null) {
                val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                tvLastAlert.text = "Last alert: ${sdf.format(Date(member.lastAlertTime))}"
                tvLastAlert.visibility = android.view.View.VISIBLE
            } else {
                tvLastAlert.visibility = android.view.View.GONE
            }

            // Click for edit
            root.setOnClickListener { onEditClick(member) }
            
            // Delete button click
            btnDelete.setOnClickListener {
                onDeleteClick(member)
            }
        }
    }

    override fun getItemCount() = members.size

    fun updateData(newMembers: List<FamilyMember>) {
        members = newMembers
        notifyDataSetChanged()
    }
}
