package com.medmonitor.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.medmonitor.databinding.FragmentNotificationsBinding
import com.medmonitor.ui.medicine.DoseConfirmationActivity
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var adapter: NotificationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        _binding?.let { b ->
            b.toolbar.title = "Alerts & Notifications"
            
            adapter = NotificationsAdapter { alertItem ->
                if (isAdded) {
                    val medicine = alertItem.medicine
                    // 🧩 PART 4 — NULL SAFETY (DEFENSIVE)
                    if (medicine.id.isNullOrBlank()) {
                        Log.e("NAV_FIX", "Cannot navigate: medicine.id is null or blank")
                        return@NotificationsAdapter
                    }

                    // 🧩 PART 2 — ALERTS SCREEN FIX (CRITICAL)
                    Log.d("SLOT_DEBUG", "Passing slot: ${alertItem.slotName} for ${medicine.name}")
                    val intent = Intent(requireContext(), DoseConfirmationActivity::class.java).apply {
                        // 🧩 PART 3 — VERIFY SOURCE FIELD (Using medicine.id.trim())
                        putExtra("medicine_id", medicine.id.trim())
                        putExtra("medicine_name", medicine.name)
                        putExtra("dose", "${medicine.dosageAmount} ${medicine.unit}")
                        putExtra("slot", alertItem.slotName)
                    }
                    startActivity(intent)
                }
            }

            b.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
            b.rvNotifications.adapter = adapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notificationItems.collect { items ->
                    _binding?.let { b ->
                        b.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                        b.rvNotifications.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                        adapter.submitList(items)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
