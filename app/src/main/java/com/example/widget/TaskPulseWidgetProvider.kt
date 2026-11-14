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
import com.example.model.AppCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Combined TaskPulse Glance Widget Provider.
 * Also delegates update broadcasts to TasksWidgetProvider and FinanceWidgetProvider
 * so that any data changes in tasks or finance refresh all active widgets simultaneously.
 */
class TaskPulseWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Update TaskPulse Glance Widget
            val glanceComponent = ComponentName(context, TaskPulseWidgetProvider::class.java)
            val glanceIds = appWidgetManager.getAppWidgetIds(glanceComponent)
            if (glanceIds != null && glanceIds.isNotEmpty()) {
                val intent = Intent(context, TaskPulseWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, glanceIds)
                }
                context.sendBroadcast(intent)
            }

            // Update Tasks Widget
            TasksWidgetProvider.updateAllWidgets(context)

            // Update Finance Widget
            FinanceWidgetProvider.updateAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Immediate synchronous placeholder
        for (appWidgetId in appWidgetIds) {
            val placeholderViews = RemoteViews(context.packageName, R.layout.widget_task_pulse)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            placeholderViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, placeholderViews)
        }

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

                // Calculate today's total spent
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfToday = cal.timeInMillis

                val transactions = database.expenseDao().getAllTransactions().first()
                val spentToday = transactions
                    .filter { it.isExpense && it.dateMillis >= startOfToday }
                    .sumOf { it.amount }

                val savedCurrencyCode = context.getSharedPreferences("taskpulse_prefs", Context.MODE_PRIVATE)
                    .getString("selected_currency", "PHP")
                val currency = AppCurrency.fromCode(savedCurrencyCode)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_task_pulse)

                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    views.setTextViewText(R.id.widget_pending_count, pendingCount.toString())
                    views.setTextViewText(R.id.widget_today_spent, currency.format(spentToday))

                    if (nextTask != null && nextTask.dueDateMillis != null) {
                        val timeFormat = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
                        val timeStr = timeFormat.format(nextTask.dueDateMillis)
                        views.setTextViewText(R.id.widget_next_deadline, "Next: ${nextTask.title} (${timeStr})")
                    } else {
                        views.setTextViewText(R.id.widget_next_deadline, "No upcoming deadlines today")
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
