package com.medmonitor.ui.medicine

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.medmonitor.data.model.Medicine
import com.medmonitor.databinding.ActivityMedicineListBinding
import kotlinx.coroutines.launch

class MedicineListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicineListBinding
    private val viewModel: MedicineViewModel by viewModels()
    private lateinit var adapter: MedicineAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicineListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddMedicineActivity::class.java))
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = MedicineAdapter(
            onEditClick = { medicine -> openEditMedicine(medicine) },
            onDeleteClick = { medicine, _ -> showDeleteConfirmation(medicine) }
        )
        binding.rvMedicineList.layoutManager = LinearLayoutManager(this)
        binding.rvMedicineList.adapter = adapter
    }

    private fun openEditMedicine(medicine: Medicine) {
        val intent = Intent(this, AddMedicineActivity::class.java).apply {
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
            putExtra("threshold", medicine.threshold) // Pass as Double
            putExtra("dosage_per_day", medicine.dosagePerDay) // Pass for Auto mode detection
            putExtra("image_url", medicine.imageUrl)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmation(medicine: Medicine) {
        if (medicine.id.isEmpty()) {
            Log.e("DELETE", "Medicine ID is empty")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete ${medicine.name}")
            .setMessage("Are you sure you want to permanently delete this medicine?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    viewModel.deleteMedicine(medicine.id)
                } catch (e: Exception) {
                    Log.e("DELETE", "Error during deletion flow: ${e.message}")
                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.medicines.collect { medicines ->
                        adapter.updateData(medicines)
                    }
                }

                launch {
                    viewModel.operationStatus.collect { result ->
                        result.onSuccess { message ->
                            Toast.makeText(this@MedicineListActivity, message, Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            Toast.makeText(this@MedicineListActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
