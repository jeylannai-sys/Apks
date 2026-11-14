package com.example.receiver

import android.app.AlarmManager
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

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_ALARM = "com.example.ACTION_FIRE_ALARM"
        const val ACTION_DISMISS_ALARM = "com.example.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.ACTION_SNOOZE_ALARM"
        const val ACTION_FIRE_TIMER = "com.example.ACTION_FIRE_TIMER"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_RINGTONE_ID = "extra_ringtone_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 1001L).toInt()
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Alarm"
        val ringtoneId = intent.getStringExtra(EXTRA_RINGTONE_ID) ?: RingtoneOption.RADAR_PULSE.id
        val ringtone = RingtoneOption.fromId(ringtoneId)

        when (action) {
            ACTION_FIRE_ALARM -> {
                // Play sound and vibrate in loop
                SoundManager.playRingtone(context, ringtone, loop = true, vibrate = true)

                // Launch full-screen lock-screen AlarmActivity
                val alarmActivityIntent = Intent(context, com.example.AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_ALARM_ID, alarmId.toLong())
                    putExtra(EXTRA_ALARM_LABEL, label)
                    putExtra(EXTRA_RINGTONE_ID, ringtoneId)
                }

                val fullScreenPendingIntent = PendingIntent.getActivity(
                    context,
                    alarmId,
                    alarmActivityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Try starting activity directly to wake screen on sleep mode
                try {
                    context.startActivity(alarmActivityIntent)
                } catch (_: Exception) {}

                // Dismiss intent
                val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
                    this.action = ACTION_DISMISS_ALARM
                    putExtra(EXTRA_ALARM_ID, alarmId.toLong())
                }
                val dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarmId + 1,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Snooze intent (1 minute as requested)
                val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                    this.action = ACTION_SNOOZE_ALARM
                    putExtra(EXTRA_ALARM_ID, alarmId.toLong())
                    putExtra(EXTRA_ALARM_LABEL, label)
                    putExtra(EXTRA_RINGTONE_ID, ringtoneId)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarmId + 2,
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, TaskPulseApp.CHANNEL_ALARMS)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("⏰ Alarm: $label")
                    .setContentText("Tap to stop or ignore for 1 minute")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setFullScreenIntent(fullScreenPendingIntent, true)
                    .setContentIntent(fullScreenPendingIntent)
                    .addAction(R.drawable.ic_launcher_foreground, "STOP", dismissPendingIntent)
                    .addAction(R.drawable.ic_launcher_foreground, "Ignore (1 min)", snoozePendingIntent)
                    .build()

                notificationManager.notify(alarmId, notification)
            }

            ACTION_DISMISS_ALARM -> {
                SoundManager.stop()
                notificationManager.cancel(alarmId)
            }

            ACTION_SNOOZE_ALARM -> {
                SoundManager.stop()
                notificationManager.cancel(alarmId)

                // Reschedule in exactly 1 minute
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val nextFireIntent = Intent(context, AlarmReceiver::class.java).apply {
                    this.action = ACTION_FIRE_ALARM
                    putExtra(EXTRA_ALARM_ID, alarmId.toLong())
                    putExtra(EXTRA_ALARM_LABEL, "$label (1m Snoozed)")
                    putExtra(EXTRA_RINGTONE_ID, ringtoneId)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    alarmId,
                    nextFireIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAtMillis = System.currentTimeMillis() + 60 * 1000L // 1 minute
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pending
                    )
                } catch (_: Exception) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                }
            }

            ACTION_FIRE_TIMER -> {
                val timerLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Timer Finished"
                SoundManager.playRingtone(context, ringtone, loop = false, vibrate = true)

                val openAppIntent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context,
                    999,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, TaskPulseApp.CHANNEL_TIMERS)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("⏱️ $timerLabel")
                    .setContentText("Your countdown timer has completed!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setContentIntent(pending)
                    .build()

                notificationManager.notify(999, notification)
            }
        }
    }
}
