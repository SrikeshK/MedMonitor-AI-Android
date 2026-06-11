package com.medmonitor.ui.profile

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.medmonitor.R
import com.medmonitor.data.SettingsManager
import com.medmonitor.data.model.Medicine
import com.medmonitor.databinding.FragmentProfileBinding
import com.medmonitor.ui.ModeSelectionActivity
import com.medmonitor.ui.SettingsActivity
import com.medmonitor.ui.auth.LoginActivity
import com.medmonitor.ui.family.FamilyActivity
import com.medmonitor.util.MedicineStatusUtil
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var settingsManager: SettingsManager
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        
        setupUI()
        applyEntranceAnimations()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        updateModeUI()
        loadProfileData()
    }

    private fun loadProfileData() {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId).collection("profile").document("info")
            .get()
            .addOnSuccessListener { document ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                
                if (document.exists()) {
                    val name = document.getString("name") ?: auth.currentUser?.displayName ?: "User"
                    val email = document.getString("email") ?: auth.currentUser?.email ?: "Email not set"
                    val age = document.getString("age") ?: "Not set"
                    val gender = document.getString("gender") ?: "Not set"

                    binding.tvUserName.text = name
                    binding.tvPersonalName.text = name
                    binding.tvUserEmail.text = email
                    binding.tvPersonalEmail.text = email
                    binding.tvUserAge.text = age
                    binding.tvUserGender.text = gender

                    val avatarLetter = name.trim().firstOrNull()?.uppercase() ?: "U"
                    binding.tvAvatarLetter.text = avatarLetter
                } else {
                    updateAvatar()
                }
            }
            .addOnFailureListener {
                if (_binding == null || !isAdded) return@addOnFailureListener
                updateAvatar()
            }
    }

    private fun updateAvatar() {
        val userName = auth.currentUser?.displayName ?: "User"
        val avatarLetter = userName.trim().firstOrNull()?.uppercase() ?: "U"
        binding.tvAvatarLetter.text = avatarLetter
    }

    private fun setupUI() {
        _binding?.let { b ->
            val user = auth.currentUser
            b.tvUserName.text = user?.displayName ?: "User"
            b.tvPersonalName.text = user?.displayName ?: "User"
            b.tvUserEmail.text = user?.email ?: "Email not set"
            b.tvPersonalEmail.text = user?.email ?: "Email not set"
            
            b.tvUserAge.text = "Not set"
            b.tvUserGender.text = "Not set"

            b.btnFamilyMgmt.setOnClickListener {
                if (isAdded) startActivity(Intent(requireContext(), FamilyActivity::class.java))
            }

            b.btnLogout.setOnClickListener {
                showLogoutConfirmation()
            }

            b.btnEditAvatar.setOnClickListener {
                if (isAdded) startActivity(Intent(requireContext(), EditProfileActivity::class.java))
            }

            setupPressAnimation(b.cardFamilyMgmt)
            setupPressAnimation(b.cardSettings)
            setupPressAnimation(b.btnEditAvatar)
            setupPressAnimation(b.cardSwitchMode)
            
            b.cardSettings.setOnClickListener {
                if (isAdded) startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }
            
            b.btnSettings.setOnClickListener {
                if (isAdded) startActivity(Intent(requireContext(), SettingsActivity::class.java))
            }

            b.cardSwitchMode.setOnClickListener {
                showSwitchModeConfirmation()
            }
        }
    }

    private fun updateModeUI() {
        _binding?.let { b ->
            val mode = settingsManager.getUserMode() ?: "Not Set"
            b.tvCurrentMode.text = "Current Mode: ${mode.lowercase().replaceFirstChar { it.uppercase() }}"
        }
    }

    private fun showSwitchModeConfirmation() {
        if (!isAdded) return
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Switch Role?")
            .setMessage("Do you want to switch between Patient and Caregiver mode?")
            .setPositiveButton("Switch") { _, _ ->
                settingsManager.setUserMode("") 
                startActivity(Intent(requireContext(), ModeSelectionActivity::class.java))
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.medicines.collect { medicines ->
                        updateMedicationsUI(medicines)
                    }
                }
                
                launch {
                    viewModel.healthSnapshot.collect { snapshot ->
                        updateSnapshotUI(snapshot)
                    }
                }
            }
        }
    }

    private var lastSnapshot = ProfileHealthSnapshot()

    private fun updateSnapshotUI(snapshot: ProfileHealthSnapshot) {
        _binding?.let { b ->
            if (snapshot.medicineCount != lastSnapshot.medicineCount) {
                animateNumber(lastSnapshot.medicineCount, snapshot.medicineCount, b.tvMedsCount)
            }
            if (snapshot.adherencePercent != lastSnapshot.adherencePercent) {
                animateNumber(lastSnapshot.adherencePercent, snapshot.adherencePercent, b.tvAdherencePercent, true)
            }
            if (snapshot.streak != lastSnapshot.streak) {
                animateNumber(lastSnapshot.streak, snapshot.streak, b.tvStreakDays)
            }
            lastSnapshot = snapshot
        }
    }

    private fun updateMedicationsUI(medicines: List<Medicine>) {
        _binding?.let { b ->
            // Optimize: Only refresh if content changed significantly or first load
            if (b.layoutMedsList.childCount > 0 && medicines.isEmpty()) {
                b.layoutMedsList.removeAllViews()
            } else if (medicines.isEmpty() && b.layoutMedsList.childCount == 0) {
                 val emptyView = TextView(requireContext()).apply {
                    text = "No medications added"
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                    setPadding(0, 16, 0, 16)
                }
                b.layoutMedsList.addView(emptyView)
                return
            }

            // Simple diffing to avoid full reinflation if possible
            if (shouldReinflateMeds(medicines)) {
                b.layoutMedsList.removeAllViews()
                medicines.forEachIndexed { index, medicine ->
                    val medView = layoutInflater.inflate(R.layout.item_medicine_compact, b.layoutMedsList, false)
                    
                    val tvName = medView.findViewById<TextView>(R.id.tvMedicineName)
                    val tvDosage = medView.findViewById<TextView>(R.id.tvDosageInfo)
                    val statusBadge = medView.findViewById<TextView>(R.id.statusBadge)

                    tvName.text = medicine.name
                    tvDosage.text = "${medicine.dosageAmount.toInt()} ${medicine.unit} • ${medicine.foodTiming.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}"
                    
                    val status = medicine.displayStatus.ifEmpty { MedicineStatusUtil.getMedicineStatus(medicine, requireContext()) }
                    val displayLabel = status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                    statusBadge.text = displayLabel
                    
                    when(status) {
                        "MISSED" -> statusBadge.setTextColor(Color.parseColor("#FF6B6B"))
                        "COMPLETED" -> statusBadge.setTextColor(Color.parseColor("#4ADE80"))
                        "UPCOMING", "DUE_NOW" -> statusBadge.setTextColor(Color.parseColor("#00E5FF"))
                        else -> statusBadge.setTextColor(Color.parseColor("#00E5FF"))
                    }
                    
                    medView.alpha = 0f
                    medView.translationX = 20f
                    medView.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(300)
                        .setStartDelay(index * 50L)
                        .start()

                    b.layoutMedsList.addView(medView)
                    
                    if (index < medicines.size - 1) {
                        val divider = View(requireContext()).apply {
                            setBackgroundColor(resources.getColor(R.color.glass_stroke, null))
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                (1 * resources.displayMetrics.density).toInt()
                            ).apply {
                                setMargins(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
                            }
                        }
                        b.layoutMedsList.addView(divider)
                    }
                }
            }
        }
    }

    private var lastMedListHash = 0
    private fun shouldReinflateMeds(medicines: List<Medicine>): Boolean {
        val currentHash = medicines.hashCode()
        if (currentHash != lastMedListHash) {
            lastMedListHash = currentHash
            return true
        }
        return false
    }

    private fun showLogoutConfirmation() {
        if (!isAdded) return
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                settingsManager.clearAll()
                auth.signOut()
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyEntranceAnimations() {
        _binding?.let { b ->
            b.headerBackground.translationY = -200f
            b.headerBackground.alpha = 0f
            
            b.cardProfile.scaleX = 0.8f
            b.cardProfile.scaleY = 0.8f
            b.cardProfile.alpha = 0f
            
            b.btnEditAvatar.scaleX = 0f
            b.btnEditAvatar.scaleY = 0f
            
            val viewsToFadeUp = listOf(
                b.tvUserName,
                b.labelSnapshot,
                b.layoutSnapshot,
                b.cardPersonalInfo,
                b.cardCurrentMeds,
                b.cardFamilyMgmt,
                b.cardSwitchMode,
                b.cardSettings,
                b.btnLogout
            )

            viewsToFadeUp.forEach { 
                it.translationY = 50f
                it.alpha = 0f
            }

            b.headerBackground.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(DecelerateInterpolator())
                .start()

            b.cardProfile.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(100)
                .setInterpolator(DecelerateInterpolator())
                .start()

            b.btnEditAvatar.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setStartDelay(400)
                .setInterpolator(DecelerateInterpolator())
                .start()

            viewsToFadeUp.forEachIndexed { index, view ->
                view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay(200L + (index * 50L))
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun animateNumber(from: Int, to: Int, view: TextView, isPercentage: Boolean = false) {
        val animator = ValueAnimator.ofInt(from, to)
        animator.duration = 800
        animator.addUpdateListener { 
            val value = it.animatedValue as Int
            view.text = if (isPercentage) "$value%" else value.toString()
        }
        animator.start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPressAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
