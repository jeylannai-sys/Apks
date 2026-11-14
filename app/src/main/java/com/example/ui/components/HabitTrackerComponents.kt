package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.core.graphics.toColorInt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HabitCompletionEntity
import com.example.data.HabitEntity
import com.example.data.HabitWithStats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Visual GitHub-style 70-day (10 weeks x 7 days) Streak Contribution Heatmap.
 */
@Composable
fun HabitStreakHeatmap(
    allCompletions: List<HabitCompletionEntity>,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayCal = remember { Calendar.getInstance() }

    // Count completions per dateString
    val countsByDate = remember(allCompletions) {
        allCompletions.groupBy { it.dateString }.mapValues { it.value.size }
    }

    // Build 10 columns of 7 days (70 days total leading up to today)
    val columns = remember(allCompletions) {
        val list = mutableListOf<List<Pair<String, Int>>>()
        val c = todayCal.clone() as Calendar
        // Rewind 69 days
        c.add(Calendar.DAY_OF_YEAR, -69)

        for (col in 0 until 10) {
            val weekDays = mutableListOf<Pair<String, Int>>()
            for (row in 0 until 7) {
                val dStr = dateFormat.format(c.time)
                val count = countsByDate[dStr] ?: 0
                weekDays.add(Pair(dStr, count))
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
            list.add(weekDays)
        }
        list
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF97316).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Consistency Streak Heatmap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Past 70 Days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 10 Columns x 7 Rows Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                columns.forEach { weekColumn ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        weekColumn.forEach { (dateStr, count) ->
                            val color = when {
                                count == 0 -> Color(0xFF334155).copy(alpha = 0.4f)
                                count == 1 -> Color(0xFF065F46)
                                count == 2 -> Color(0xFF059669)
                                else -> Color(0xFF10B981)
                            }

                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Less", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                listOf(
                    Color(0xFF334155).copy(alpha = 0.4f),
                    Color(0xFF065F46),
                    Color(0xFF059669),
                    Color(0xFF10B981)
                ).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "More", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Individual Habit Card with 1-tap completion checkmark and streak flame.
 */
@Composable
fun HabitCard(
    habitWithStats: HabitWithStats,
    onToggleToday: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStats.habit
    val isDone = habitWithStats.isCompletedToday
    val habitColor = try { Color(habit.colorHex.toColorInt()) } catch (_: Exception) { Color(0xFF10B981) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon & Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(habitColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getHabitIcon(habit.iconName),
                        contentDescription = null,
                        tint = habitColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF97316).copy(alpha = 0.18f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🔥", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${habitWithStats.currentStreak}d streak",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF97316)
                                )
                            }
                        }

                        Text(
                            text = "Best: ${habitWithStats.bestStreak}d • ${habit.targetDaysPerWeek}x/wk",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1-Tap Completion Check Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isDone) Color(0xFF10B981) else MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = if (isDone) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable(onClick = onToggleToday),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dialog to add a new habit with preset suggestions.
 */
@Composable
fun CreateHabitDialog(
    onDismiss: () -> Unit,
    onSave: (HabitEntity) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Productivity") }
    var selectedColor by remember { mutableStateOf("#10B981") }
    var targetDays by remember { mutableStateOf(7) }

    val presetHabits = listOf(
        Triple("💧 Drink 2.5L Water Daily", "Health", "#0EA5E9"),
        Triple("🏃 30-Min Cardio & Workout", "Fitness", "#F97316"),
        Triple("💰 Track Daily Income & Expenses", "Finance", "#10B981"),
        Triple("🎯 Complete Top 3 Tasks", "Productivity", "#8B5CF6"),
        Triple("📚 Read 20 Mins / Study", "Personal", "#EC4899"),
        Triple("🧘 10-Min Mindfulness / Meditation", "Wellness", "#14B8A6")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Habit", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Quick Presets:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetHabits) { (presetName, cat, col) ->
                        FilterChip(
                            selected = title == presetName,
                            onClick = {
                                title = presetName
                                category = cat
                                selectedColor = col
                            },
                            label = { Text(presetName, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Habit Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Target Frequency:", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 5, 7).forEach { days ->
                            FilterChip(
                                selected = targetDays == days,
                                onClick = { targetDays = days },
                                label = { Text("${days}d/wk", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            HabitEntity(
                                title = title.trim(),
                                category = category,
                                colorHex = selectedColor,
                                targetDaysPerWeek = targetDays
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Create Habit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun getHabitIcon(name: String): ImageVector {
    return when (name) {
        "WaterDrop" -> Icons.Default.WaterDrop
        "FitnessCenter" -> Icons.Default.FitnessCenter
        "MenuBook" -> Icons.AutoMirrored.Filled.MenuBook
        "SelfImprovement" -> Icons.Default.SelfImprovement
        else -> Icons.Default.Check
    }
}
