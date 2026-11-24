package com.example.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.TaskPulseApp
import com.example.data.CalorieLogEntity
import com.example.data.DailyHealthSummaryEntity
import com.example.data.WorkoutLogEntity
import com.example.receiver.HydrationReminderReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

data class HydrationReminderState(
    val enabled: Boolean = true,
    val lastWaterIntakeMillis: Long = 0L,
    val lastNudgeMillis: Long = 0L,
    val isDoNotDisturbActive: Boolean = false,
    val suppressedByDnd: Boolean = false,
    val nextCheckAtMillis: Long = 0L
)

data class HydrationNudgeEvaluation(
    val shouldNotify: Boolean,
    val reason: String,
    val title: String,
    val message: String,
    val isDoNotDisturbActive: Boolean,
    val elapsedMillis: Long,
    val suggestedIntakeMl: Int
)

object HydrationReminderManager {

    const val INACTIVITY_THRESHOLD_MS = 3L * 60L * 60L * 1000L // 3 hours
    const val DND_DEFER_INTERVAL_MS = 30L * 60L * 1000L // 30 mins retry when DND ends
    const val NOTIFICATION_ID = 41001
    private const val REQUEST_CODE_CHECK = 41000

    private const val PREFS_NAME = "taskpulse_hydration_prefs"
    private const val KEY_ENABLED = "hydration_reminder_enabled"
    private const val KEY_LAST_INTAKE_MS = "hydration_last_intake_millis"
    private const val KEY_LAST_NUDGE_MS = "hydration_last_nudge_millis"
    private const val KEY_SUPPRESSED_DND = "hydration_suppressed_by_dnd"
    private const val KEY_NEXT_CHECK_MS = "hydration_next_check_millis"

    private val _state = MutableStateFlow(HydrationReminderState())
    val state: StateFlow<HydrationReminderState> = _state.asStateFlow()

    fun init(context: Context, nowMillis: Long = System.currentTimeMillis()): HydrationReminderState {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        var lastIntake = prefs.getLong(KEY_LAST_INTAKE_MS, 0L)
        if (lastIntake == 0L) {
            // Establish initial baseline timestamp when tracker starts
            lastIntake = nowMillis
            prefs.edit().putLong(KEY_LAST_INTAKE_MS, lastIntake).apply()
        }
        val lastNudge = prefs.getLong(KEY_LAST_NUDGE_MS, 0L)
        val suppressed = prefs.getBoolean(KEY_SUPPRESSED_DND, false)
        val nextCheck = prefs.getLong(KEY_NEXT_CHECK_MS, lastIntake + INACTIVITY_THRESHOLD_MS)
        val dndActive = isDoNotDisturbActive(appCtx)

        val current = HydrationReminderState(
            enabled = enabled,
            lastWaterIntakeMillis = lastIntake,
            lastNudgeMillis = lastNudge,
            isDoNotDisturbActive = dndActive,
            suppressedByDnd = suppressed,
            nextCheckAtMillis = nextCheck
        )
        _state.value = current
        if (enabled && nextCheck <= nowMillis) {
            scheduleCheckAt(appCtx, lastIntake + INACTIVITY_THRESHOLD_MS)
        } else if (enabled) {
            scheduleCheckAt(appCtx, nextCheck)
        }
        return current
    }

    fun refreshDndStatus(context: Context) {
        val appCtx = context.applicationContext
        if (_state.value.lastWaterIntakeMillis == 0L) {
            init(appCtx)
            return
        }
        _state.value = _state.value.copy(isDoNotDisturbActive = isDoNotDisturbActive(appCtx))
    }

    fun setRemindersEnabled(context: Context, enabled: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        val appCtx = context.applicationContext
        val current = init(appCtx, nowMillis)
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = current.copy(
            enabled = enabled,
            isDoNotDisturbActive = isDoNotDisturbActive(appCtx)
        )
        if (enabled) {
            val base = maxOf(current.lastWaterIntakeMillis, nowMillis - INACTIVITY_THRESHOLD_MS)
            scheduleCheckAt(appCtx, base + INACTIVITY_THRESHOLD_MS)
        } else {
            cancelScheduledCheck(appCtx)
        }
    }

    fun recordWaterLogged(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val appCtx = context.applicationContext
        val current = init(appCtx, nowMillis)
        val nextCheck = nowMillis + INACTIVITY_THRESHOLD_MS
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_INTAKE_MS, nowMillis)
            .putBoolean(KEY_SUPPRESSED_DND, false)
            .putLong(KEY_NEXT_CHECK_MS, nextCheck)
            .apply()

        _state.value = current.copy(
            lastWaterIntakeMillis = nowMillis,
            suppressedByDnd = false,
            isDoNotDisturbActive = isDoNotDisturbActive(appCtx),
            nextCheckAtMillis = nextCheck
        )

        if (current.enabled) {
            scheduleCheckAt(appCtx, nextCheck)
        }
        // Dismiss any active hydration nudge notification since the user just drank water
        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }

    fun isDoNotDisturbActive(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        val filter = nm.currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun evaluateHydrationStatus(
        nowMillis: Long,
        lastIntakeMillis: Long,
        lastNudgeMillis: Long,
        remindersEnabled: Boolean,
        isDndActive: Boolean,
        dailyHealth: DailyHealthSummaryEntity?,
        todayWorkouts: List<WorkoutLogEntity>,
        todayCalories: List<CalorieLogEntity>
    ): HydrationNudgeEvaluation {
        val elapsed = (nowMillis - lastIntakeMillis).coerceAtLeast(0L)
        val workoutMinutes = todayWorkouts.sumOf { it.durationMinutes }
        val workoutBurned = todayWorkouts.sumOf { it.caloriesBurned }
        val currentWater = dailyHealth?.waterIntakeMl ?: 0
        val targetWater = (dailyHealth?.waterTargetMl ?: 2500).coerceAtLeast(1)
        val maxLimit = (dailyHealth?.waterMaxLimitMl ?: 3700).coerceAtLeast(targetWater)
        val steps = dailyHealth?.stepsCount ?: 0

        val suggestedMl = if (workoutBurned >= 250 || workoutMinutes >= 30 || steps >= 8000) 500 else 250
        val elapsedHours = TimeUnit.MILLISECONDS.toHours(elapsed).coerceAtLeast(1L)
        val remainingToGoal = (targetWater - currentWater).coerceAtLeast(0)

        val title = when {
            workoutBurned > 0 -> "Rehydrate After Your Workout"
            currentWater == 0 -> "Time for Your First Glass of Water"
            else -> "Smart Hydration Nudge"
        }

        val message = when {
            workoutBurned > 0 ->
                "You burned $workoutBurned kcal (${workoutMinutes}m workout) and haven't logged water in ${elapsedHours}h. Drink +$suggestedMl ml to recover ($currentWater / $targetWater ml)."
            steps >= 6000 ->
                "With ${steps} steps and ${todayCalories.size} meal(s) today, you haven't logged water in ${elapsedHours}h. Have +$suggestedMl ml ($remainingToGoal ml left to goal)."
            currentWater == 0 ->
                "0 / $targetWater ml logged today and no water in ${elapsedHours}h. Take a quick +$suggestedMl ml sip!"
            else ->
                "No water logged in ${elapsedHours}h. You're at $currentWater / $targetWater ml ($remainingToGoal ml to go) — drink +$suggestedMl ml."
        }

        if (!remindersEnabled) {
            return HydrationNudgeEvaluation(
                shouldNotify = false,
                reason = "Hydration reminders are turned off",
                title = title,
                message = message,
                isDoNotDisturbActive = isDndActive,
                elapsedMillis = elapsed,
                suggestedIntakeMl = suggestedMl
            )
        }

        if (currentWater >= maxLimit) {
            return HydrationNudgeEvaluation(
                shouldNotify = false,
                reason = "Everyday maximum ($maxLimit ml) already reached",
                title = title,
                message = message,
                isDoNotDisturbActive = isDndActive,
                elapsedMillis = elapsed,
                suggestedIntakeMl = suggestedMl
            )
        }

        if (elapsed < INACTIVITY_THRESHOLD_MS) {
            return HydrationNudgeEvaluation(
                shouldNotify = false,
                reason = "Water logged within the last 3 hours",
                title = title,
                message = message,
                isDoNotDisturbActive = isDndActive,
                elapsedMillis = elapsed,
                suggestedIntakeMl = suggestedMl
            )
        }

        if (lastNudgeMillis > 0L && (nowMillis - lastNudgeMillis) < INACTIVITY_THRESHOLD_MS) {
            return HydrationNudgeEvaluation(
                shouldNotify = false,
                reason = "Already nudged within the last 3 hours",
                title = title,
                message = message,
                isDoNotDisturbActive = isDndActive,
                elapsedMillis = elapsed,
                suggestedIntakeMl = suggestedMl
            )
        }

        if (isDndActive) {
            return HydrationNudgeEvaluation(
                shouldNotify = false,
                reason = "Respecting Do Not Disturb mode",
                title = title,
                message = message,
                isDoNotDisturbActive = true,
                elapsedMillis = elapsed,
                suggestedIntakeMl = suggestedMl
            )
        }

        return HydrationNudgeEvaluation(
            shouldNotify = true,
            reason = "3+ hours without water intake",
            title = title,
            message = message,
            isDoNotDisturbActive = false,
            elapsedMillis = elapsed,
            suggestedIntakeMl = suggestedMl
        )
    }

    fun checkAndNotifyIfDue(
        context: Context,
        dailyHealth: DailyHealthSummaryEntity?,
        todayWorkouts: List<WorkoutLogEntity>,
        todayCalories: List<CalorieLogEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): HydrationNudgeEvaluation {
        val appCtx = context.applicationContext
        val current = init(appCtx, nowMillis)
        val dndActive = isDoNotDisturbActive(appCtx)
        val eval = evaluateHydrationStatus(
            nowMillis = nowMillis,
            lastIntakeMillis = current.lastWaterIntakeMillis,
            lastNudgeMillis = current.lastNudgeMillis,
            remindersEnabled = current.enabled,
            isDndActive = dndActive,
            dailyHealth = dailyHealth,
            todayWorkouts = todayWorkouts,
            todayCalories = todayCalories
        )

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (eval.shouldNotify) {
            postHydrationNotification(appCtx, eval.title, eval.message, eval.suggestedIntakeMl)
            val nextCheck = nowMillis + INACTIVITY_THRESHOLD_MS
            prefs.edit()
                .putLong(KEY_LAST_NUDGE_MS, nowMillis)
                .putBoolean(KEY_SUPPRESSED_DND, false)
                .putLong(KEY_NEXT_CHECK_MS, nextCheck)
                .apply()
            _state.value = _state.value.copy(
                lastNudgeMillis = nowMillis,
                isDoNotDisturbActive = false,
                suppressedByDnd = false,
                nextCheckAtMillis = nextCheck
            )
            scheduleCheckAt(appCtx, nextCheck)
        } else if (eval.isDoNotDisturbActive && eval.elapsedMillis >= INACTIVITY_THRESHOLD_MS && current.enabled) {
            // Defer cleanly during DND and re-check later without disturbing user
            val retryAt = nowMillis + DND_DEFER_INTERVAL_MS
            prefs.edit()
                .putBoolean(KEY_SUPPRESSED_DND, true)
                .putLong(KEY_NEXT_CHECK_MS, retryAt)
                .apply()
            _state.value = _state.value.copy(
                isDoNotDisturbActive = true,
                suppressedByDnd = true,
                nextCheckAtMillis = retryAt
            )
            scheduleCheckAt(appCtx, retryAt)
        } else if (current.enabled) {
            val remainingMs = (INACTIVITY_THRESHOLD_MS - eval.elapsedMillis).coerceAtLeast(DND_DEFER_INTERVAL_MS)
            val nextCheck = nowMillis + remainingMs
            prefs.edit()
                .putBoolean(KEY_SUPPRESSED_DND, false)
                .putLong(KEY_NEXT_CHECK_MS, nextCheck)
                .apply()
            _state.value = _state.value.copy(
                isDoNotDisturbActive = dndActive,
                suppressedByDnd = false,
                nextCheckAtMillis = nextCheck
            )
            scheduleCheckAt(appCtx, nextCheck)
        }
        return eval
    }

    private fun postHydrationNotification(
        context: Context,
        title: String,
        message: String,
        suggestedMl: Int
    ) {
        // Double-check DND immediately prior to posting
        if (isDoNotDisturbActive(context)) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SCREEN", "HEALTH")
        }
        val contentPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quickDrinkIntent = Intent(context, HydrationReminderReceiver::class.java).apply {
            action = HydrationReminderReceiver.ACTION_QUICK_LOG_WATER
            putExtra(HydrationReminderReceiver.EXTRA_WATER_ML, suggestedMl)
        }
        val quickDrinkPending = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID + 1,
            quickDrinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, TaskPulseApp.CHANNEL_HYDRATION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Log +$suggestedMl ml",
                quickDrinkPending
            )
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+
        }
    }

    fun scheduleCheckAt(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = createCheckPendingIntent(context)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } catch (_: Exception) {}
        }
    }

    fun cancelScheduledCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(createCheckPendingIntent(context))
    }

    private fun createCheckPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HydrationReminderReceiver::class.java).apply {
            action = HydrationReminderReceiver.ACTION_CHECK_HYDRATION
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CHECK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun formatElapsedLabel(lastIntakeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (lastIntakeMillis <= 0L) return "Not logged yet"
        val diff = (nowMillis - lastIntakeMillis).coerceAtLeast(0L)
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            totalMinutes < 1 -> "Just now"
            hours == 0L -> "${mins}m ago"
            mins == 0L -> "${hours}h ago"
            else -> "${hours}h ${mins}m ago"
        }
    }
}
