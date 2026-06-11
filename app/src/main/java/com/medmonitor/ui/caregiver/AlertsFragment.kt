package com.medmonitor.ui.caregiver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.databinding.FragmentAlertsCaregiverBinding
import com.medmonitor.ui.caregiver.mapper.CaregiverTimelineMapper
import com.medmonitor.ui.caregiver.model.TimelineEvent
import com.medmonitor.ui.caregiver.viewmodel.CaregiverAlertsViewModelV2
import com.medmonitor.util.CaregiverSmsManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AlertsFragment : Fragment() {
    private var _binding: FragmentAlertsCaregiverBinding? = null
    private val binding get() = _binding!!
    private val smsManager = CaregiverSmsManager()
    private lateinit var adapter: CareMonitorAdapter

    private val alertsViewModel: CaregiverAlertsViewModelV2 by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsCaregiverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeViewModel()
        
        // Phase 9: Remove Low Stock from Alerts screen
        try {
            binding.countLowStock.parent?.let { 
                if (it is View) it.visibility = View.GONE 
            }
        } catch (e: Exception) {
            Log.e("AlertsFragment", "Error hiding low stock UI", e)
        }
    }

    private fun setupRecyclerView() {
        adapter = CareMonitorAdapter(
            onMessageClick = { phone, medName, time ->
                try {
                    smsManager.sendReminder(requireContext(), phone, medName, time)
                } catch (e: Exception) {
                    Log.e("AlertsFragment", "Error sending message", e)
                }
            },
            onCallClick = { phone ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("AlertsFragment", "Error initiating call", e)
                }
            },
            onTakenClick = { position ->
                if (position != RecyclerView.NO_POSITION) {
                    try {
                        val item = adapter.currentList.getOrNull(position)
                        if (item is TimelineEvent.DoseEvent && item.sourceItem != null) {
                            alertsViewModel.markMedicineTaken(item.sourceItem)
                        }
                    } catch (e: Exception) {
                        Log.e("AlertsFragment", "Error in onTakenClick", e)
                    }
                }
            }
        )
        binding.rvAlerts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAlerts.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            alertsViewModel.timelineItems.collectLatest { items ->
                try {
                    val mappedEvents = CaregiverTimelineMapper.map(items)
                    adapter.submitList(mappedEvents) {
                        // Ensure binding is still valid when callback fires
                        if (_binding != null) {
                            updateEmptyState(mappedEvents.isEmpty())
                            updateCounters(items)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AlertsFragment", "Error in UI collection", e)
                }
            }
        }
    }

    private fun updateCounters(items: List<com.medmonitor.ui.caregiver.model.CaregiverTimelineItem>) {
        try {
            val dueCount = items.count { it.status == "DUE_NOW" }
            val missedCount = items.count { it.status == "MISSED" }
            
            binding.countDue.text = dueCount.toString()
            binding.countMissed.text = missedCount.toString()
        } catch (e: Exception) {
            Log.e("AlertsFragment", "Error updating counters", e)
        }
    }

    private fun updateEmptyState(isListEmpty: Boolean) {
        try {
            binding.emptyState.visibility = if (isListEmpty) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e("AlertsFragment", "Error updating empty state", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
