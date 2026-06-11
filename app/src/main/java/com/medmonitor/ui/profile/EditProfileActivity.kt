package com.medmonitor.ui.profile

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.medmonitor.R

class EditProfileActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var tvAvatarLetter: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        tvAvatarLetter = findViewById(R.id.tvEditAvatarLetter)
        
        setupToolbar()
        setupUI()
        loadExistingData()
        applyEntranceAnimations()
    }

    private fun updateAvatar(name: String) {
        val avatarLetter = name.trim().firstOrNull()?.uppercase() ?: "U"
        tvAvatarLetter?.text = avatarLetter
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar?.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupUI() {
        val genders = arrayOf("Male", "Female", "Other", "Prefer not to say")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        findViewById<AutoCompleteTextView>(R.id.actvGender)?.setAdapter(adapter)

        findViewById<MaterialButton>(R.id.btnSaveProfile)?.setOnClickListener {
            saveProfileData()
        }
        
        findViewById<TextInputEditText>(R.id.etName)?.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = findViewById<TextInputEditText>(R.id.etName)?.text.toString()
                updateAvatar(name)
            }
        }
    }

    private fun loadExistingData() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).collection("profile").document("info")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val name = document.getString("name") ?: ""
                    findViewById<TextInputEditText>(R.id.etName)?.setText(name)
                    findViewById<TextInputEditText>(R.id.etEmail)?.setText(document.getString("email"))
                    findViewById<TextInputEditText>(R.id.etAge)?.setText(document.getString("age"))
                    findViewById<AutoCompleteTextView>(R.id.actvGender)?.setText(document.getString("gender"), false)
                    updateAvatar(name)
                } else {
                    val user = auth.currentUser
                    val name = user?.displayName ?: ""
                    findViewById<TextInputEditText>(R.id.etName)?.setText(name)
                    findViewById<TextInputEditText>(R.id.etEmail)?.setText(user?.email)
                    updateAvatar(name)
                }
            }
    }

    private fun saveProfileData() {
        val userId = auth.currentUser?.uid ?: return
        val name = findViewById<TextInputEditText>(R.id.etName)?.text.toString().trim()
        val email = findViewById<TextInputEditText>(R.id.etEmail)?.text.toString().trim()
        val age = findViewById<TextInputEditText>(R.id.etAge)?.text.toString().trim()
        val gender = findViewById<AutoCompleteTextView>(R.id.actvGender)?.text.toString().trim()

        if (name.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Name and Email are required", Toast.LENGTH_SHORT).show()
            return
        }

        val dataMap = hashMapOf(
            "name" to name,
            "email" to email,
            "age" to age,
            "gender" to gender
        )

        db.collection("users").document(userId).collection("profile").document("info")
            .set(dataMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyEntranceAnimations() {
        val root = findViewById<View>(android.R.id.content)
        root.translationX = 1000f
        root.animate()
            .translationX(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val viewsToFade = listOf(
            R.id.cardEditPhoto,
            R.id.tilName,
            R.id.tilEmail,
            R.id.layoutAgeGender,
            R.id.btnSaveProfile
        )

        viewsToFade.forEachIndexed { index, viewId ->
            val view = findViewById<View>(viewId)
            view?.alpha = 0f
            view?.translationY = 20f
            view?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setDuration(400)
                ?.setStartDelay(200L + (index * 50L))
                ?.setInterpolator(DecelerateInterpolator())
                ?.start()
        }
    }
}
