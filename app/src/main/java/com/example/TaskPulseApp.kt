package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import com.example.data.AppDatabase
import com.example.service.BudgetThresholdManager
import com.example.service.HydrationReminderManager

class TaskPulseApp : Application() {

    companion object {
        const val CHANNEL_ALARMS = "channel_alarms"
        const val CHANNEL_TASKS = "channel_tasks"
        const val CHANNEL_TIMERS = "channel_timers"
        const val CHANNEL_FINANCE = "channel_finance"
        const val CHANNEL_HYDRATION = "channel_hydration"
        const val CHANNEL_BUDGET_ALERTS = "channel_budget_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize database early
        AppDatabase.getInstance(this)
        createNotificationChannels()
        HydrationReminderManager.init(this)
        BudgetThresholdManager.init(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Alarms Channel - High Priority, Lock Screen Visible
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARMS,
                "Alarms & Wakeups",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alarms and scheduled wakeups"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // Task Deadlines Channel
            val taskChannel = NotificationChannel(
                CHANNEL_TASKS,
                "Task Deadlines",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Push notifications for upcoming task deadlines"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Timers Channel
            val timerChannel = NotificationChannel(
                CHANNEL_TIMERS,
                "Timers & Stopwatch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Timer expiration notifications"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            // Finance Channel
            val financeChannel = NotificationChannel(
                CHANNEL_FINANCE,
                "Budget & Financial Reports",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Spending alerts and monthly spending report summaries"
            }

            // Intelligent Hydration Reminder Channel (respects Do Not Disturb)
            val hydrationChannel = NotificationChannel(
                CHANNEL_HYDRATION,
                "Smart Hydration Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Health-aware water intake nudges after 3 hours of inactivity"
                setBypassDnd(false)
            }

            // Budget Threshold Alert Channel
            val budgetAlertsChannel = NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                "Budget Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when category spending exceeds your predefined monthly budget percentage"
                enableVibration(true)
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannels(
                listOf(
                    alarmChannel,
                    taskChannel,
                    timerChannel,
                    financeChannel,
                    hydrationChannel,
                    budgetAlertsChannel
                )
            )
        }
    }
}
