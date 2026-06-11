package com.medmonitor.ui.family

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.FamilyMember
import com.medmonitor.data.repository.FamilyRepository
import com.medmonitor.databinding.ActivityFamilyBinding
import com.medmonitor.databinding.DialogAddCareMemberBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FamilyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFamilyBinding
    private val repository = FamilyRepository()
    private lateinit var adapter: FamilyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamilyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeFamilyMembers()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = FamilyAdapter(
            members = emptyList(),
            onDeleteClick = { member -> showDeleteConfirmation(member) },
            onEditClick = { member -> showMemberDialog(member) }
        )
        binding.rvFamily.layoutManager = LinearLayoutManager(this)
        binding.rvFamily.adapter = adapter

        binding.btnAddMember.setOnClickListener {
            showMemberDialog()
        }
    }

    private fun observeFamilyMembers() {
        lifecycleScope.launch {
            val settingsManager = SettingsManager(this@FamilyActivity)
            repository.getFamilyMembers().collectLatest { members ->
                adapter.updateData(members)
                
                // STEP 1 — SYNC LOCAL CACHE
                val cached = members.map { 
                    SettingsManager.CachedCaregiver(it.name, it.phone) 
                }
                settingsManager.setCachedCaregivers(cached)
            }
        }
    }

    private fun showMemberDialog(member: FamilyMember? = null) {
        val dialogBinding = DialogAddCareMemberBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_MedMonitor_Dialog)
            .setView(dialogBinding.root)
            .create()

        // Setup Relation Dropdown
        val relations = arrayOf("Mother", "Father", "Friend", "Doctor", "Brother", "Sister", "Other")
        val relationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, relations)
        dialogBinding.actRelation.setAdapter(relationAdapter)

        // Pre-fill if editing
        member?.let {
            dialogBinding.apply {
                etName.setText(it.name)
                actRelation.setText(it.relation, false)
                etPhone.setText(it.phone)
                etEmail.setText(it.email)
                etBackupPhone.setText(it.backupPhone)
                btnAdd.text = "Update Member"
            }
        }

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnAdd.setOnClickListener {
            val name = dialogBinding.etName.text.toString()
            val relation = dialogBinding.actRelation.text.toString()
            val phone = dialogBinding.etPhone.text.toString()
            val email = dialogBinding.etEmail.text.toString()
            val backupPhone = dialogBinding.etBackupPhone.text.toString()

            if (name.isNotEmpty() && phone.isNotEmpty() && relation.isNotEmpty()) {
                val newMember = member?.copy(
                    name = name,
                    relation = relation,
                    phone = phone,
                    email = email,
                    backupPhone = backupPhone
                ) ?: FamilyMember(
                    name = name,
                    relation = relation,
                    phone = phone,
                    email = email,
                    backupPhone = backupPhone,
                    notifyAfterMissedDose = true, // Default to true if not specified
                    notifyImmediately = false
                )

                saveOrUpdateMember(newMember)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun saveOrUpdateMember(member: FamilyMember) {
        lifecycleScope.launch {
            try {
                if (member.id.isEmpty()) {
                    repository.addFamilyMember(member)
                    Toast.makeText(this@FamilyActivity, "Member added successfully", Toast.LENGTH_SHORT).show()
                } else {
                    repository.updateFamilyMember(member)
                    Toast.makeText(this@FamilyActivity, "Member updated successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FamilyActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmation(member: FamilyMember) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Member?")
            .setMessage("Are you sure you want to remove this contact?")
            .setPositiveButton("Delete") { _, _ ->
                deleteMember(member)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMember(member: FamilyMember) {
        lifecycleScope.launch {
            try {
                if (member.id.isNotEmpty()) {
                    repository.deleteFamilyMember(member.id)
                    Toast.makeText(this@FamilyActivity, "Member removed successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FamilyActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
