package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.TaskPulseApp
import com.example.data.BudgetEntity
import com.example.data.ExpenseEntity
import com.example.model.AppCurrency
import com.example.model.ExpenseCategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class BudgetThresholdAlert(
    val category: String,
    val spent: Double,
    val monthlyLimit: Double,
    val thresholdPercent: Int,
    val percentUsed: Double,
    val isOverBudget: Boolean
)

data class BudgetThresholdState(
    val alertsEnabled: Boolean = true,
    val defaultThresholdPercent: Int = 80,
    val categoryThresholds: Map<String, Int> = emptyMap()
) {
    fun thresholdFor(category: String): Int {
        return (categoryThresholds[category] ?: defaultThresholdPercent).coerceIn(10, 100)
    }
}

object BudgetThresholdManager {

    private const val PREFS_NAME = "taskpulse_budget_threshold_prefs"
    private const val KEY_ALERTS_ENABLED = "budget_alerts_enabled"
    private const val KEY_DEFAULT_THRESHOLD = "budget_default_threshold_percent"
    private const val PREFIX_CAT_THRESHOLD = "cat_threshold_"
    private const val PREFIX_LAST_NOTIFIED = "cat_notified_"
    private const val NOTIFICATION_BASE_ID = 52000

    private val _state = MutableStateFlow(BudgetThresholdState())
    val state: StateFlow<BudgetThresholdState> = _state.asStateFlow()

    fun init(context: Context): BudgetThresholdState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ALERTS_ENABLED, true)
        val defaultPct = prefs.getInt(KEY_DEFAULT_THRESHOLD, 80).coerceIn(10, 100)
        val catMap = mutableMapOf<String, Int>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX_CAT_THRESHOLD) && value is Int) {
                val category = key.removePrefix(PREFIX_CAT_THRESHOLD)
                catMap[category] = value.coerceIn(10, 100)
            }
        }
        val loaded = BudgetThresholdState(
            alertsEnabled = enabled,
            defaultThresholdPercent = defaultPct,
            categoryThresholds = catMap
        )
        _state.value = loaded
        return loaded
    }

    fun setAlertsEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        val current = init(appCtx)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALERTS_ENABLED, enabled)
            .apply()
        _state.value = current.copy(alertsEnabled = enabled)
    }

    fun setDefaultThresholdPercent(context: Context, percent: Int) {
        val appCtx = context.applicationContext
        val clamped = percent.coerceIn(10, 100)
        val current = init(appCtx)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DEFAULT_THRESHOLD, clamped)
            .apply()
        _state.value = current.copy(defaultThresholdPercent = clamped)
    }

    fun setCategoryThresholdPercent(context: Context, category: String, percent: Int?) {
        val appCtx = context.applicationContext
        val current = init(appCtx)
        val editor = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val updatedMap = current.categoryThresholds.toMutableMap()
        if (percent == null) {
            editor.remove(PREFIX_CAT_THRESHOLD + category)
            updatedMap.remove(category)
        } else {
            val clamped = percent.coerceIn(10, 100)
            editor.putInt(PREFIX_CAT_THRESHOLD + category, clamped)
            updatedMap[category] = clamped
        }
        // Reset previous notification dedupe so the new threshold can alert cleanly
        editor.remove(PREFIX_LAST_NOTIFIED + category)
        editor.apply()
        _state.value = current.copy(categoryThresholds = updatedMap)
    }

    fun evaluateCategoryThresholds(
        transactions: List<ExpenseEntity>,
        budgets: List<BudgetEntity>,
        state: BudgetThresholdState,
        nowMillis: Long = System.currentTimeMillis()
    ): List<BudgetThresholdAlert> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val monthEnd = cal.timeInMillis

        val spentByCategory = transactions
            .filter { it.isExpense && it.dateMillis in monthStart..monthEnd }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val limitMap = mutableMapOf<String, Double>()
        ExpenseCategories.list
            .filter { it.name != "Salary & Work" && it.defaultBudget > 0.0 }
            .forEach { limitMap[it.name] = it.defaultBudget }
        budgets.forEach { b ->
            limitMap[b.category] = b.monthlyLimit.coerceAtLeast(0.0)
        }

        val alerts = mutableListOf<BudgetThresholdAlert>()
        for ((category, limit) in limitMap) {
            if (limit <= 0.0) continue
            val spent = spentByCategory[category] ?: 0.0
            val thresholdPercent = state.thresholdFor(category)
            val percentUsed = (spent / limit) * 100.0
            val thresholdAmount = limit * (thresholdPercent / 100.0)

            if (spent >= thresholdAmount - 0.001) {
                alerts.add(
                    BudgetThresholdAlert(
                        category = category,
                        spent = spent,
                        monthlyLimit = limit,
                        thresholdPercent = thresholdPercent,
                        percentUsed = percentUsed,
                        isOverBudget = spent > limit
                    )
                )
            }
        }
        return alerts.sortedByDescending { it.percentUsed }
    }

    fun checkAndNotifyThresholds(
        context: Context,
        transactions: List<ExpenseEntity>,
        budgets: List<BudgetEntity>,
        currency: AppCurrency,
        targetCategory: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): List<BudgetThresholdAlert> {
        val appCtx = context.applicationContext
        val current = init(appCtx)
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(nowMillis)

        val activeAlerts = evaluateCategoryThresholds(transactions, budgets, current, nowMillis)
        val activeCategories = activeAlerts.map { it.category }.toSet()

        // Clear dedupe state for categories that fell back under their threshold (e.g., deleted expense or raised limit)
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(PREFIX_LAST_NOTIFIED) }
            .forEach { key ->
                val cat = key.removePrefix(PREFIX_LAST_NOTIFIED)
                if (cat !in activeCategories) {
                    editor.remove(key)
                }
            }

        if (!current.alertsEnabled) {
            editor.apply()
            return activeAlerts
        }

        val alertsToEvaluate = if (targetCategory != null) {
            activeAlerts.filter { it.category.equals(targetCategory, ignoreCase = true) }
        } else {
            activeAlerts
        }

        for (alert in alertsToEvaluate) {
            val tierTag = if (alert.isOverBudget) "OVER_100" else "THRESHOLD_${alert.thresholdPercent}"
            val marker = "$monthKey:$tierTag"
            val prevMarker = prefs.getString(PREFIX_LAST_NOTIFIED + alert.category, null)
            if (prevMarker != marker) {
                postBudgetAlertNotification(appCtx, alert, currency)
                editor.putString(PREFIX_LAST_NOTIFIED + alert.category, marker)
            }
        }
        editor.apply()
        return activeAlerts
    }

    private fun postBudgetAlertNotification(
        context: Context,
        alert: BudgetThresholdAlert,
        currency: AppCurrency
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_SCREEN", "FINANCE")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_BASE_ID + (alert.category.hashCode() and 0x7FFF),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val usedPct = alert.percentUsed.toInt()
        val title = if (alert.isOverBudget) {
            "Over Budget Alert: ${alert.category}"
        } else {
            "Budget Threshold (${alert.thresholdPercent}%): ${alert.category}"
        }
        val message = if (alert.isOverBudget) {
            "Spent ${currency.format(alert.spent)} ($usedPct%) — exceeded your ${currency.format(alert.monthlyLimit)} monthly limit by ${currency.format(alert.spent - alert.monthlyLimit)}."
        } else {
            "Spent ${currency.format(alert.spent)} ($usedPct%) of your ${currency.format(alert.monthlyLimit)} monthly limit (alert at ${alert.thresholdPercent}%)."
        }

        val notification = NotificationCompat.Builder(context, TaskPulseApp.CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            nm.notify(NOTIFICATION_BASE_ID + (alert.category.hashCode() and 0x7FFF), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+
        }
    }
}
