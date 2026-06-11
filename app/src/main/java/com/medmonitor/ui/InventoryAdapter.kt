package com.medmonitor.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.medmonitor.R
import com.medmonitor.data.model.Medicine
import com.medmonitor.data.model.MedicineType
import com.medmonitor.databinding.ItemInventoryBinding
import com.medmonitor.util.InventoryState
import com.medmonitor.util.getInventoryState
import com.medmonitor.util.calculateDaysLeft

class InventoryAdapter(
    private var medicines: List<Medicine>,
    private val onEditThreshold: (Medicine) -> Unit = {},
    private val onRefillClick: (Medicine) -> Unit = {}
) : RecyclerView.Adapter<InventoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInventoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medicine = medicines[position]
        val context = holder.itemView.context
        
        try {
            with(holder.binding) {
                // PART 7 — CARD ENTRY ANIMATION
                root.alpha = 0f
                root.translationY = 20f
                root.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(DecelerateInterpolator())
                    .setStartDelay(position * 40L)
                    .start()

                // PART 5 — TEXT LAYOUT IMPROVEMENT (LEFT)
                medicineName.text = medicine.name
                typeLabel.text = medicine.type.name
                
                val remaining = medicine.remainingQuantity.toInt()
                val total = medicine.totalQuantity.toInt()
                
                // 🧩 PHASE 2: CENTRALIZED INVENTORY STATE
                val state = getInventoryState(medicine)

                // PART 1 & 2 — GLASSMORPHISM & STOCK VISUAL
                when (state) {
                    InventoryState.EMPTY, InventoryState.CRITICAL -> {
                        cardContent.setBackgroundResource(R.drawable.glass_card_low_stock)
                        remainingCount.setTextColor(Color.parseColor("#FF6B6B"))
                    }
                    InventoryState.LOW -> {
                        cardContent.setBackgroundResource(R.drawable.glass_card_low_stock)
                        remainingCount.setTextColor(Color.parseColor("#FFBF00"))
                    }
                    else -> {
                        cardContent.setBackgroundResource(R.drawable.glass_card_premium)
                        remainingCount.setTextColor(Color.WHITE)
                    }
                }

                val unit = if (medicine.type == MedicineType.TABLET) "tablets" else "ml"
                remainingCount.text = "$remaining $unit remaining"

                // PART 5 — RIGHT SIDE STATS
                thresholdText.text = "Alert at ${medicine.threshold.toInt()}"
                
                // 🧩 PHASE 2: CENTRALIZED DAYS LEFT
                val daysLeftVal = calculateDaysLeft(medicine)
                
                when (state) {
                    InventoryState.EMPTY -> {
                        daysLeft.text = "Out of stock"
                        daysLeft.setTextColor(Color.parseColor("#FF6B6B"))
                    }
                    InventoryState.CRITICAL -> {
                        daysLeft.text = if (daysLeftVal <= 0) "Critical: < 1 day" else "Critical: $daysLeftVal day"
                        daysLeft.setTextColor(Color.parseColor("#FF6B6B"))
                    }
                    InventoryState.LOW -> {
                        daysLeft.text = "~$daysLeftVal days"
                        daysLeft.setTextColor(Color.parseColor("#FFBF00"))
                    }
                    else -> {
                        daysLeft.text = "~$daysLeftVal days"
                        daysLeft.setTextColor(Color.WHITE)
                    }
                }

                // RESET VISUALS
                pillScroll.visibility = View.GONE
                liquidBar.visibility = View.GONE
                pillContainer.removeAllViews()

                // PART 3 — TABLET VISUALIZATION (DYNAMIC STACK)
                if (medicine.type == MedicineType.TABLET) {
                    pillScroll.visibility = View.VISIBLE
                    val displayCount = remaining.coerceAtMost(10)
                    
                    for (i in 0 until displayCount) {
                        val pill = ImageView(context)
                        pill.setImageResource(R.drawable.inventory_pill_indicator)
                        
                        val tint = when (state) {
                            InventoryState.EMPTY, InventoryState.CRITICAL -> Color.parseColor("#FF6B6B")
                            InventoryState.LOW -> Color.parseColor("#FFBF00")
                            else -> Color.parseColor("#00E5FF")
                        }
                        pill.setColorFilter(tint)

                        val params = LinearLayout.LayoutParams(28, 14)
                        params.setMargins(6, 0, 6, 0)
                        pill.layoutParams = params
                        
                        pillContainer.addView(pill)

                        // PART 3 — FLOATING EFFECT (MICRO ANIMATION)
                        val floatAnim = ObjectAnimator.ofFloat(pill, "translationY", 0f, -6f, 0f)
                        floatAnim.duration = 1800 + (i * 120).toLong()
                        floatAnim.repeatCount = ValueAnimator.INFINITE
                        floatAnim.interpolator = DecelerateInterpolator()
                        floatAnim.start()
                    }
                } 
                // PART 4 — SYRUP VISUAL ENHANCEMENT
                else if (medicine.type == MedicineType.SYRUP) {
                    liquidBar.visibility = View.VISIBLE
                    val progress = if (total > 0) (remaining * 100 / total) else 0
                    
                    // PART 4 & 7 — SMOOTH PROGRESS ANIMATION
                    val anim = ObjectAnimator.ofInt(liquidBar, "progress", 0, progress)
                    anim.duration = 800
                    anim.interpolator = DecelerateInterpolator()
                    anim.start()
                }

                // PART 6 — ICON ENHANCEMENT
                val iconTint = when (state) {
                    InventoryState.EMPTY, InventoryState.CRITICAL -> Color.parseColor("#FF6B6B")
                    InventoryState.LOW -> Color.parseColor("#FFBF00")
                    else -> Color.parseColor("#00E5FF")
                }
                editThreshold.setColorFilter(iconTint)
                btnRefill.setColorFilter(Color.parseColor("#00E5FF"))
                
                editThreshold.setOnClickListener {
                    // PART 7 — BUTTON PRESS SCALE ANIMATION
                    it.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .withEndAction {
                            it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                            onEditThreshold(medicine)
                        }
                        .start()
                }

                btnRefill.setOnClickListener {
                    it.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(100)
                        .withEndAction {
                            it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                            onRefillClick(medicine)
                        }
                        .start()
                }
            }
        } catch (e: Exception) {
            Log.e("INVENTORY_UI", "Error rendering inventory UI", e)
        }
    }

    override fun getItemCount() = medicines.size

    fun updateData(newMedicines: List<Medicine>) {
        medicines = newMedicines
        notifyDataSetChanged()
    }
}
