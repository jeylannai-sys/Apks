package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dedicated Tasks AppWidget Provider.
 * Displays pending tasks count and upcoming deadline with quick 1-tap launch to Tasks view.
 * Fully compatible with RemoteViews specifications (no prohibited View tags) and uses goAsync().
 */
class TasksWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, TasksWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, TasksWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Immediate synchronous update to prevent launcher inflation timeouts
        for (appWidgetId in appWidgetIds) {
            val placeholderViews = RemoteViews(context.packageName, R.layout.widget_tasks)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_SCREEN", "TASKS")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            placeholderViews.setOnClickPendingIntent(R.id.widget_tasks_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, placeholderViews)
        }

        // Asynchronous database query with goAsync()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val allTasks = database.taskDao().getAllTasks().first()
                val pendingTasks = allTasks.filter { !it.isCompleted }
                val pendingCount = pendingTasks.size

                val now = System.currentTimeMillis()
                val nextTask = pendingTasks
                    .filter { it.dueDateMillis != null && it.dueDateMillis >= now }
                    .minByOrNull { it.dueDateMillis!! }

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_tasks)
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("EXTRA_SCREEN", "TASKS")
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        101,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_tasks_root, pendingIntent)

                    views.setTextViewText(R.id.widget_tasks_count, pendingCount.toString())
                    views.setTextViewText(
                        R.id.widget_tasks_summary_badge,
                        if (pendingCount == 0) "All Done ✓" else "$pendingCount Active"
                    )

                    if (nextTask != null && nextTask.dueDateMillis != null) {
                        val timeFormat = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
                        val timeStr = timeFormat.format(nextTask.dueDateMillis)
                        views.setTextViewText(
                            R.id.widget_tasks_next_deadline,
                            "Next: ${nextTask.title} ($timeStr)"
                        )
                    } else {
                        views.setTextViewText(
                            R.id.widget_tasks_next_deadline,
                            if (pendingCount > 0) "No upcoming deadlines today" else "All tasks completed! Great job 🎉"
                        )
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
