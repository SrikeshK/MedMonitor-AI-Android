package com.medmonitor.ui.medicine

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.data.model.Medicine
import com.medmonitor.databinding.FragmentMedicineListBinding
import kotlinx.coroutines.launch

class MedicinesFragment : Fragment() {

    private var _binding: FragmentMedicineListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MedicineViewModel by viewModels()
    private lateinit var adapter: MedicineAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicineListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        _binding?.let { b ->
            adapter = MedicineAdapter(
                onEditClick = { medicine -> openEditMedicine(medicine) },
                onDeleteClick = { medicine, position -> showDeleteConfirmation(medicine, position) }
            )
            b.rvMedicineList.layoutManager = LinearLayoutManager(requireContext())
            b.rvMedicineList.adapter = adapter
            
            b.fabAdd.setOnClickListener {
                if (isAdded) {
                    startActivity(Intent(requireContext(), AddMedicineActivity::class.java))
                }
            }
        }
    }

    private fun openEditMedicine(medicine: Medicine) {
        if (!isAdded) return
        val intent = Intent(requireContext(), AddMedicineActivity::class.java).apply {
            putExtra("medicine_id", medicine.id)
            putExtra("name", medicine.name)
            putExtra("type", medicine.type.name)
            putExtra("dose", medicine.dosageAmount)
            putExtra("total_quantity", medicine.totalQuantity)
            putExtra("remaining_quantity", medicine.remainingQuantity)
            putExtra("food_timing", medicine.foodTiming)
            putExtra("start_date", medicine.startDate ?: 0L)
            putExtra("end_date", medicine.endDate ?: 0L)
            putExtra("schedule_times", HashMap(medicine.scheduleTimes))
            putExtra("threshold", medicine.threshold.toInt()) // Standardized field
            putExtra("image_url", medicine.imageUrl)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmation(medicine: Medicine, position: Int) {
        if (medicine.id.isEmpty() || !isAdded) {
            Log.e("DELETE", "Medicine ID is empty or fragment not added")
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Medicine")
            .setMessage("Are you sure you want to delete ${medicine.name}?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    viewModel.deleteMedicine(medicine.id)
                } catch (e: Exception) {
                    Log.e("DELETE", "Error during deletion flow: ${e.message}")
                    Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.medicines.collect { medicines ->
                        _binding?.let { b ->
                            adapter.updateData(medicines)
                        }
                    }
                }

                launch {
                    viewModel.operationStatus.collect { result ->
                        result.fold(
                            onSuccess = { message ->
                                _binding?.let {
                                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFailure = { error ->
                                _binding?.let {
                                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
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
