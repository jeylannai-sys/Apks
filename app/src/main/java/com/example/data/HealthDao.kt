package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    // Calorie Logs
    @Query("SELECT * FROM calorie_logs ORDER BY timestampMillis DESC")
    fun getAllCalorieLogs(): Flow<List<CalorieLogEntity>>

    @Query("SELECT * FROM calorie_logs WHERE timestampMillis >= :start AND timestampMillis <= :end ORDER BY timestampMillis DESC")
    fun getCalorieLogsForDay(start: Long, end: Long): Flow<List<CalorieLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalorieLog(log: CalorieLogEntity): Long

    @Delete
    suspend fun deleteCalorieLog(log: CalorieLogEntity)

    // Workout Logs
    @Query("SELECT * FROM workout_logs ORDER BY timestampMillis DESC")
    fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>>

    @Query("SELECT * FROM workout_logs WHERE timestampMillis >= :start AND timestampMillis <= :end ORDER BY timestampMillis DESC")
    fun getWorkoutLogsForDay(start: Long, end: Long): Flow<List<WorkoutLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutLog(log: WorkoutLogEntity): Long

    @Delete
    suspend fun deleteWorkoutLog(log: WorkoutLogEntity)

    // Daily Health (Water, Steps, Targets)
    @Query("SELECT * FROM daily_health WHERE dateString = :dateString")
    fun getDailyHealth(dateString: String): Flow<DailyHealthSummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDailyHealth(health: DailyHealthSummaryEntity)
}
