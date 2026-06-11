package com.medmonitor.ui.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medmonitor.data.model.DailyStats
import com.medmonitor.data.model.Medicine
import com.medmonitor.ui.dashboard.DashboardViewModel
import com.medmonitor.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onAddClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val medicines by viewModel.todayMedicines.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderSection(userName = viewModel.getUserName(), dailyStats = dailyStats)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Today's Medicines",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }

                items(medicines) { medicine ->
                    MedicineCard(medicine)
                }

                item {
                    Text(
                        text = "Quick Actions",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                    QuickActionsGrid(onAddClick)
                }

                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onConfirmClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = Primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
            text = { Text("Confirm Intake") }
        )
    }
}

@Composable
fun HeaderSection(userName: String, dailyStats: DailyStats) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                brush = Brush.horizontalGradient(listOf(Color(0xFF3AA6FF), Color(0xFF00E0C6))),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = "Hello, $userName",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Stay healthy today!",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            ComplianceCard(dailyStats)
        }
    }
}

@Composable
fun ComplianceCard(dailyStats: DailyStats) {
    val score = dailyStats.adherence
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "ComplianceProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(Primary.copy(alpha = 0.5f), Secondary.copy(alpha = 0.5f)))
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Secondary.copy(alpha = 0.15f), Color.Transparent),
                                center = center,
                                radius = size.minDimension * 0.8f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = 1f,
                    modifier = Modifier.fillMaxSize(),
                    color = Error.copy(alpha = 0.3f),
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                )
                CircularProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxSize(),
                    color = Success,
                    strokeWidth = 8.dp,
                    strokeCap = StrokeCap.Round,
                    trackColor = Color.Transparent
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Weekly Compliance",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$score%",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${dailyStats.taken} taken • ${dailyStats.missed} missed",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MedicineCard(medicine: Medicine) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GlassStroke)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = GlassOverlay
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Primary)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medicine.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${medicine.dosageAmount.toInt()} ${medicine.unit} • ${medicine.foodTiming.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
                
                // Existing single status label
                Text(
                    text = if (medicine.scheduleTimes.size > 1) "Daily" else "08:00 AM",
                    color = Secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // --- MULTI-DOSE TIMELINE ENHANCEMENT ---
            if (medicine.scheduleTimes.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                val sortedSlots = medicine.scheduleTimes.toList().sortedBy { it.second }
                val currentTime = System.currentTimeMillis()
                
                val nextDoseKey = sortedSlots
                    .map { it.first to parseTimeToMillis(it.second) }
                    .filter { it.second >= currentTime }
                    .minByOrNull { it.second }?.first

                sortedSlots.forEach { (slotName, timeStr) ->
                    DoseTimelineItem(
                        slotName = slotName,
                        timeStr = timeStr,
                        medicine = medicine,
                        currentTime = currentTime,
                        isNext = slotName == nextDoseKey
                    )
                }
            }
        }
    }
}

@Composable
fun DoseTimelineItem(
    slotName: String,
    timeStr: String,
    medicine: Medicine,
    currentTime: Long,
    isNext: Boolean
) {
    val slotTimeMillis = parseTimeToMillis(timeStr)
    val status = when {
        slotTimeMillis < currentTime && medicine.isCompleted -> "COMPLETED"
        slotTimeMillis < currentTime -> "MISSED"
        isNext -> "DUE_NOW"
        else -> "UPCOMING"
    }

    val (color, label, icon) = when (status) {
        "COMPLETED" -> Triple(Success, "✔ Completed", Icons.Filled.CheckCircle)
        "MISSED" -> Triple(Error, "✖ Missed", Icons.Filled.Error)
        "DUE_NOW" -> Triple(Secondary, "🔔 Due Now", Icons.Filled.NotificationsActive)
        else -> Triple(Primary.copy(alpha = 0.7f), "⏳ Upcoming", Icons.Filled.Schedule)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = slotName.lowercase().replaceFirstChar { it.uppercase() },
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = formatTo12Hr(timeStr),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = label,
            color = if (status == "UPCOMING") TextSecondary else color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun parseTimeToMillis(timeStr: String): Long {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return try {
        val date = sdf.parse(timeStr) ?: return 0L
        val calendar = Calendar.getInstance()
        val timeCal = Calendar.getInstance().apply { time = date }
        calendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
        calendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    } catch (e: Exception) {
        0L
    }
}

private fun formatTo12Hr(time24: String): String {
    return try {
        val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sdf12 = SimpleDateFormat("hh:mm A", Locale.getDefault())
        val date = sdf24.parse(time24)
        if (date != null) sdf12.format(date) else time24
    } catch (e: Exception) {
        time24
    }
}

@Composable
fun QuickActionsGrid(onAddClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        QuickActionItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Add,
            text = "Add Medicine",
            color = Primary,
            onClick = onAddClick
        )
        QuickActionItem(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Analytics,
            text = "Analytics",
            color = Secondary,
            onClick = {}
        )
    }
}

@Composable
fun QuickActionItem(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = text, color = Color.White, fontSize = 14.sp)
        }
    }
}
