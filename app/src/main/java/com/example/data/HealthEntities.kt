package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calorie_logs")
data class CalorieLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val foodName: String,
    val calories: Int,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val mealType: String = "Lunch", // Breakfast, Lunch, Dinner, Snack
    val portionSize: String = "1 standard serving",
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "workout_logs")
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val activityName: String,
    val caloriesBurned: Int,
    val durationMinutes: Int,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_health")
data class DailyHealthSummaryEntity(
    @PrimaryKey
    val dateString: String, // Format: YYYY-MM-DD
    val waterIntakeMl: Int = 0,
    val waterTargetMl: Int = 2500,
    val calorieTarget: Int = 2200,
    val stepsCount: Int = 0,
    val weightKg: Double = 0.0
)
