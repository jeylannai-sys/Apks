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
import java.util.Calendar

/**
 * Dedicated Income/Expenses AppWidget Provider.
 * Displays today's Income, Expenses, and Net Balance with active currency formatting.
 * Fully compliant with RemoteViews restrictions and uses goAsync() for reliability.
 */
class FinanceWidgetProvider : AppWidgetProvider() {

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FinanceWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val intent = Intent(context, FinanceWidgetProvider::class.java).apply {
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
        // Immediate synchronous placeholder
        for (appWidgetId in appWidgetIds) {
            val placeholderViews = RemoteViews(context.packageName, R.layout.widget_finance)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_SCREEN", "FINANCE")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                202,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            placeholderViews.setOnClickPendingIntent(R.id.widget_finance_root, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, placeholderViews)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfToday = cal.timeInMillis

                val transactions = database.expenseDao().getAllTransactions().first()
                val todayTransactions = transactions.filter { it.dateMillis >= startOfToday }

                val todayIncome = todayTransactions.filter { !it.isExpense }.sumOf { it.amount }
                val todayExpense = todayTransactions.filter { it.isExpense }.sumOf { it.amount }
                val netBalance = todayIncome - todayExpense

                val savedCurrencyCode = context.getSharedPreferences("taskpulse_prefs", Context.MODE_PRIVATE)
                    .getString("selected_currency", "PHP")
                val currency = AppCurrency.fromCode(savedCurrencyCode)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_finance)
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("EXTRA_SCREEN", "FINANCE")
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        202,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_finance_root, pendingIntent)

                    views.setTextViewText(R.id.widget_finance_income, "+${currency.format(todayIncome)}")
                    views.setTextViewText(R.id.widget_finance_expense, "-${currency.format(todayExpense)}")

                    val netSign = if (netBalance >= 0) "+" else ""
                    views.setTextViewText(
                        R.id.widget_finance_net,
                        "Today Net: $netSign${currency.format(netBalance)}"
                    )

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
