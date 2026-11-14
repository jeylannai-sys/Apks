package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.Build
import com.example.data.AppDatabase

class TaskPulseApp : Application() {

    companion object {
        const val CHANNEL_ALARMS = "channel_alarms"
        const val CHANNEL_TASKS = "channel_tasks"
        const val CHANNEL_TIMERS = "channel_timers"
        const val CHANNEL_FINANCE = "channel_finance"
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize database early
        AppDatabase.getInstance(this)
        createNotificationChannels()
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

            notificationManager.createNotificationChannels(
                listOf(alarmChannel, taskChannel, timerChannel, financeChannel)
            )
        }
    }
}
