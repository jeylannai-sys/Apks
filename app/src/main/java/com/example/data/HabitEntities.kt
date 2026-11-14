package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: String = "Productivity",
    val iconName: String = "Check",
    val colorHex: String = "#10B981",
    val targetDaysPerWeek: Int = 7,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "habit_completions",
    indices = [
        Index(value = ["habitId", "dateString"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val dateString: String, // "YYYY-MM-DD"
    val completedAtMillis: Long = System.currentTimeMillis()
)

data class HabitWithStats(
    val habit: HabitEntity,
    val isCompletedToday: Boolean,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalCompletions: Int,
    val completionDates: Set<String>
)

/**
 * Calculates current streak and best streak from a set of "YYYY-MM-DD" completion dates.
 */
fun calculateHabitStreaks(dates: Set<String>): Pair<Int, Int> {
    if (dates.isEmpty()) return Pair(0, 0)

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayCal = Calendar.getInstance()
    val todayStr = dateFormat.format(todayCal.time)

    // Check if completed today or yesterday to continue streak
    val completedToday = dates.contains(todayStr)
    todayCal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStr = dateFormat.format(todayCal.time)
    val completedYesterday = dates.contains(yesterdayStr)

    var currentStreak = 0
    if (completedToday || completedYesterday) {
        val checkCal = Calendar.getInstance()
        if (!completedToday) {
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        }
        while (true) {
            val dateStr = dateFormat.format(checkCal.time)
            if (dates.contains(dateStr)) {
                currentStreak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
    }

    // Compute best streak by scanning all consecutive days in sorted dates
    var bestStreak = currentStreak
    val sortedDates = dates.mapNotNull {
        try { dateFormat.parse(it) } catch (_: Exception) { null }
    }.sorted()

    if (sortedDates.isNotEmpty()) {
        var tempStreak = 1
        var maxStreak = 1
        for (i in 1 until sortedDates.size) {
            val diff = (sortedDates[i].time - sortedDates[i - 1].time) / (1000 * 60 * 60 * 24)
            if (diff == 1L) {
                tempStreak++
                if (tempStreak > maxStreak) maxStreak = tempStreak
            } else if (diff > 1L) {
                tempStreak = 1
            }
        }
        bestStreak = maxOf(maxStreak, currentStreak)
    }

    return Pair(currentStreak, bestStreak)
}
