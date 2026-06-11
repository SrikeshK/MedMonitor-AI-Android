package com.medmonitor.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.medmonitor.R
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.MedicineType
import com.medmonitor.databinding.ActivityInventoryBinding
import com.medmonitor.databinding.DialogEditThresholdBinding
import com.medmonitor.databinding.DialogRefillMedicineBinding
import com.medmonitor.ui.medicine.MedicineViewModel
import com.medmonitor.util.NetworkUtil
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventoryBinding
    private val viewModel: MedicineViewModel by viewModels()
    private lateinit var inventoryAdapter: InventoryAdapter
    private var targetMedicineId: String? = null
    private var refillDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityInventoryBinding.inflate(layoutInflater)
            setContentView(binding.root)

            targetMedicineId = intent.getStringExtra("medicine_id")

            setupRecyclerView()
            observeData()
        } catch (e: Exception) {
            Log.e("InventoryActivity", "Error in onCreate", e)
            finish()
        }
    }

    private fun setupRecyclerView() {
        try {
            inventoryAdapter = InventoryAdapter(
                medicines = emptyList(),
                onEditThreshold = { medicine -> showEditThresholdDialog(medicine) },
                onRefillClick = { medicine -> showRefillDialog(medicine) }
            )
            binding.inventoryRecycler.apply {
                layoutManager = LinearLayoutManager(this@InventoryActivity)
                adapter = inventoryAdapter
            }
        } catch (e: Exception) {
            Log.e("InventoryActivity", "Error setting up RecyclerView", e)
        }
    }

    private fun showRefillDialog(medicine: Medicine) {
        val dialog = BottomSheetDialog(this, R.style.GlassBottomSheetDialog)
        val dialogBinding = DialogRefillMedicineBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val unit = if (medicine.type == MedicineType.TABLET) "tablets" else "ml"
        dialogBinding.dialogTitle.text = "Add Stock: ${medicine.name}"
        dialogBinding.refillLabel.text = "Enter number of $unit to add"
        dialogBinding.refillInput.hint = "0 $unit"
        dialogBinding.refillInput.requestFocus()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnAdd.setOnClickListener {
            val input = dialogBinding.refillInput.text.toString()
            val amount = input.toDoubleOrNull()

            if (amount != null && amount > 0) {
                viewModel.refillMedicine(medicine.id, amount)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid amount > 0", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showEditThresholdDialog(medicine: Medicine) {
        val dialog = BottomSheetDialog(this, R.style.GlassBottomSheetDialog)
        val dialogBinding = DialogEditThresholdBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val unit = if (medicine.type == MedicineType.TABLET) "tablets" else "ml"
        dialogBinding.thresholdLabel.text = "Threshold ($unit)"
        dialogBinding.thresholdInput.setText(medicine.threshold.toString())
        dialogBinding.thresholdInput.requestFocus()

        dialogBinding.saveButton.setOnClickListener {
            val input = dialogBinding.thresholdInput.text.toString()
            if (input.isNotEmpty()) {
                val newThreshold = input.toDoubleOrNull() ?: medicine.threshold
                viewModel.updateThreshold(medicine.id, newThreshold)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a value", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun observeData() {
        lifecycleScope.launch {
            try {
                viewModel.medicines.collectLatest { medicines ->
                    if (medicines.isNullOrEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.inventoryRecycler.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.inventoryRecycler.visibility = View.VISIBLE
                        inventoryAdapter.updateData(medicines)

                        // 🧩 PHASE 1 FIX: Auto-open refill dialog for redirected medicine
                        if (!refillDialogShown && targetMedicineId != null) {
                            val target = medicines.find { it.id == targetMedicineId }
                            if (target != null) {
                                refillDialogShown = true
                                showRefillDialog(target)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InventoryActivity", "Error observing medicines", e)
                binding.emptyState.visibility = View.VISIBLE
                binding.inventoryRecycler.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.operationStatus.collectLatest { result ->
                result.onSuccess { message ->
                    // 🧩 STEP C3: Safe Offline UX
                    // If refill succeeds but we are offline, show a specific sync message.
                    val displayMessage = if (message == "Stock updated" && !NetworkUtil.isNetworkAvailable(this@InventoryActivity)) {
                        "Refill saved locally. Will sync automatically when internet returns."
                    } else {
                        message
                    }
                    Toast.makeText(this@InventoryActivity, displayMessage, Toast.LENGTH_SHORT).show()
                }
                result.onFailure { error ->
                    Toast.makeText(this@InventoryActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
