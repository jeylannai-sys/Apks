package com.example.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.TaskPulseApp
import com.example.model.RingtoneOption
import com.example.service.SoundManager
import com.example.widget.TaskPulseWidgetProvider

class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TASK_DEADLINE = "com.example.ACTION_TASK_DEADLINE"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_TASK_RINGTONE = "extra_task_ringtone"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Deadline"
        val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY) ?: "MEDIUM"
        val ringtoneId = intent.getStringExtra(EXTRA_TASK_RINGTONE) ?: RingtoneOption.DIGITAL_CHIME.id

        // Play customized ringtone
        SoundManager.playRingtone(
            context = context,
            ringtone = RingtoneOption.fromId(ringtoneId),
            loop = false,
            vibrate = true
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, TaskPulseApp.CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📌 Deadline Due: $title")
            .setContentText("Priority: $priority • Tap to review and complete.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((20000 + taskId).toInt(), notification)

        // Refresh home screen widget
        TaskPulseWidgetProvider.updateAllWidgets(context)
    }
}
