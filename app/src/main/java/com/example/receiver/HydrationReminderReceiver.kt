package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.TaskPulseRepository
import com.example.service.HydrationReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HydrationReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_HYDRATION = "com.example.ACTION_CHECK_HYDRATION"
        const val ACTION_QUICK_LOG_WATER = "com.example.ACTION_QUICK_LOG_WATER"
        const val EXTRA_WATER_ML = "extra_water_ml"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(appContext)
                if (intent.action == ACTION_QUICK_LOG_WATER) {
                    val amountMl = intent.getIntExtra(EXTRA_WATER_ML, 250).coerceIn(100, 1000)
                    val repo = TaskPulseRepository(appContext, database)
                    repo.addWaterIntake(amountMl)
                } else {
                    val now = System.currentTimeMillis()
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                    val startOfDayCal = Calendar.getInstance().apply {
                        timeInMillis = now
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startOfDay = startOfDayCal.timeInMillis
                    val endOfDay = startOfDay + 86_400_000L - 1L

                    val dailyHealth = database.healthDao().getDailyHealth(todayStr).first()
                    val todayWorkouts = database.healthDao().getWorkoutLogsForDay(startOfDay, endOfDay).first()
                    val todayCalories = database.healthDao().getCalorieLogsForDay(startOfDay, endOfDay).first()

                    HydrationReminderManager.checkAndNotifyIfDue(
                        context = appContext,
                        dailyHealth = dailyHealth,
                        todayWorkouts = todayWorkouts,
                        todayCalories = todayCalories,
                        nowMillis = now
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
