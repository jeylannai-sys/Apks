package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.model.AppCurrency
import com.example.receiver.AlarmReceiver
import com.example.receiver.TaskReminderReceiver
import com.example.service.BudgetThresholdManager
import com.example.service.HydrationReminderManager
import com.example.util.PrivateFileSafeHelper
import com.example.util.VaultBatchRestoreResult
import com.example.util.VaultImportResult
import com.example.util.VaultRestoreOutcome
import com.example.widget.TaskPulseWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CategorySpendingSummary(
    val category: String,
    val totalSpent: Double,
    val budgetLimit: Double,
    val remainingBalance: Double,
    val percentageOfBudget: Float,
    val percentageOfTotalSpending: Float
)

data class MonthlyReport(
    val monthYear: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val netSavings: Double,
    val totalBudgetLimit: Double,
    val remainingBudgetBalance: Double,
    val savingsRate: Float,
    val topSpendingCategory: String,
    val dailyAverageSpent: Double,
    val categoryBreakdown: List<CategorySpendingSummary>
)

class TaskPulseRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    val tasksFlow: Flow<List<TaskEntity>> = database.taskDao().getAllTasks()
    val alarmsFlow: Flow<List<AlarmEntity>> = database.alarmDao().getAllAlarms()
    val transactionsFlow: Flow<List<ExpenseEntity>> = database.expenseDao().getAllTransactions()
    val budgetsFlow: Flow<List<BudgetEntity>> = database.budgetDao().getAllBudgets()

    // Health & Fitness Flows
    val calorieLogsFlow: Flow<List<CalorieLogEntity>> = database.healthDao().getAllCalorieLogs()
    val workoutLogsFlow: Flow<List<WorkoutLogEntity>> = database.healthDao().getAllWorkoutLogs()

    // Flowchart & Diagram Planning Flows
    val diagramsFlow: Flow<List<DiagramEntity>> = database.diagramDao().getAllDiagrams()
    fun getNodesForDiagram(diagramId: String): Flow<List<DiagramNodeEntity>> =
        database.diagramDao().getNodesForDiagram(diagramId)
    fun getConnectionsForDiagram(diagramId: String): Flow<List<DiagramConnectionEntity>> =
        database.diagramDao().getConnectionsForDiagram(diagramId)

    // Habit Tracker Flows
    val habitsFlow: Flow<List<HabitEntity>> = database.habitDao().getAllHabits()
    val allHabitCompletionsFlow: Flow<List<HabitCompletionEntity>> = database.habitDao().getAllCompletions()

    suspend fun saveHabit(habit: HabitEntity): Long = database.habitDao().insertHabit(habit)
    suspend fun deleteHabit(habit: HabitEntity) = database.habitDao().deleteHabit(habit)
    suspend fun toggleHabitCompletion(habitId: Long, dateString: String) {
        val existing = database.habitDao().getCompletion(habitId, dateString)
        if (existing != null) {
            database.habitDao().removeCompletion(habitId, dateString)
        } else {
            database.habitDao().recordCompletion(HabitCompletionEntity(habitId = habitId, dateString = dateString))
        }
    }

    // Private File Safe Flows
    val vaultFilesFlow: Flow<List<VaultFileEntity>> = database.vaultDao().getAllVaultFiles()

    fun getDailyHealthFlow(dateString: String): Flow<DailyHealthSummaryEntity?> =
        database.healthDao().getDailyHealth(dateString)

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // Task Operations
    suspend fun saveTask(task: TaskEntity): Long {
        val id = database.taskDao().insertTask(task)
        val savedTask = if (task.id == 0L) task.copy(id = id) else task

        if (savedTask.hasAlarm && savedTask.dueDateMillis != null && savedTask.dueDateMillis > System.currentTimeMillis()) {
            scheduleTaskAlarm(savedTask)
        } else {
            cancelTaskAlarm(savedTask.id)
        }
        TaskPulseWidgetProvider.updateAllWidgets(context)
        return id
    }

    suspend fun setTaskCompleted(id: Long, completed: Boolean) {
        val task = database.taskDao().getTaskById(id)
        if (task != null && completed && task.isRepeating()) {
            // Task has repeating day schedule! Reschedule to next selected day
            val nextDue = task.calculateNextDueDate()
            val updated = task.copy(
                isCompleted = false,
                dueDateMillis = nextDue
            )
            database.taskDao().updateTask(updated)
            if (updated.hasAlarm && nextDue != null) {
                scheduleTaskAlarm(updated)
            }
        } else {
            database.taskDao().setTaskCompleted(id, completed)
            if (completed) {
                cancelTaskAlarm(id)
            }
        }
        TaskPulseWidgetProvider.updateAllWidgets(context)
    }

    suspend fun deleteTask(task: TaskEntity) {
        cancelTaskAlarm(task.id)
        database.taskDao().deleteTask(task)
        TaskPulseWidgetProvider.updateAllWidgets(context)
    }

    private fun scheduleTaskAlarm(task: TaskEntity) {
        val triggerTime = task.dueDateMillis ?: return
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_TASK_DEADLINE
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(TaskReminderReceiver.EXTRA_TASK_PRIORITY, task.priority)
            putExtra(TaskReminderReceiver.EXTRA_TASK_RINGTONE, task.alarmRingtone)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (10000 + task.id).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pending)
        }
    }

    private fun cancelTaskAlarm(taskId: Long) {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_TASK_DEADLINE
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (10000 + taskId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    // Alarm Operations
    suspend fun saveAlarm(alarm: AlarmEntity): Long {
        val id = database.alarmDao().insertAlarm(alarm)
        val savedAlarm = if (alarm.id == 0L) alarm.copy(id = id) else alarm
        if (savedAlarm.isEnabled) {
            scheduleAlarmClock(savedAlarm)
        } else {
            cancelAlarmClock(savedAlarm.id)
        }
        return id
    }

    suspend fun setAlarmEnabled(id: Long, enabled: Boolean) {
        database.alarmDao().setAlarmEnabled(id, enabled)
        val alarm = database.alarmDao().getAlarmById(id)
        if (alarm != null) {
            if (enabled) {
                scheduleAlarmClock(alarm)
            } else {
                cancelAlarmClock(id)
            }
        }
    }

    suspend fun deleteAlarm(alarm: AlarmEntity) {
        cancelAlarmClock(alarm.id)
        database.alarmDao().deleteAlarm(alarm)
    }

    private fun scheduleAlarmClock(alarm: AlarmEntity) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_RINGTONE_ID, alarm.ringtone)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (30000 + alarm.id).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
        }
    }

    private fun cancelAlarmClock(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (30000 + alarmId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    // Expense & Budget Operations
    suspend fun saveExpense(expense: ExpenseEntity, currency: AppCurrency = AppCurrency.PHP): Long {
        val id = database.expenseDao().insertExpense(expense)
        TaskPulseWidgetProvider.updateAllWidgets(context)
        if (expense.isExpense || expense.id > 0L) {
            val allTx = database.expenseDao().getAllTransactions().first()
            val allBudgets = database.budgetDao().getAllBudgets().first()
            BudgetThresholdManager.checkAndNotifyThresholds(
                context = context,
                transactions = allTx,
                budgets = allBudgets,
                currency = currency,
                targetCategory = if (expense.id > 0L) null else expense.category
            )
        }
        return id
    }

    suspend fun deleteExpense(expense: ExpenseEntity, currency: AppCurrency = AppCurrency.PHP) {
        database.expenseDao().deleteExpense(expense)
        TaskPulseWidgetProvider.updateAllWidgets(context)
        val allTx = database.expenseDao().getAllTransactions().first()
        val allBudgets = database.budgetDao().getAllBudgets().first()
        BudgetThresholdManager.checkAndNotifyThresholds(
            context = context,
            transactions = allTx,
            budgets = allBudgets,
            currency = currency
        )
    }

    suspend fun setBudget(category: String, limit: Double, currency: AppCurrency = AppCurrency.PHP) {
        database.budgetDao().setBudget(BudgetEntity(category, limit))
        val allTx = database.expenseDao().getAllTransactions().first()
        val allBudgets = database.budgetDao().getAllBudgets().first()
        BudgetThresholdManager.checkAndNotifyThresholds(
            context = context,
            transactions = allTx,
            budgets = allBudgets,
            currency = currency,
            targetCategory = category
        )
    }

    // Health & Fitness Operations
    suspend fun saveCalorieLog(log: CalorieLogEntity): Long {
        return database.healthDao().insertCalorieLog(log)
    }

    suspend fun deleteCalorieLog(log: CalorieLogEntity) {
        database.healthDao().deleteCalorieLog(log)
    }

    suspend fun saveWorkoutLog(log: WorkoutLogEntity): Long {
        return database.healthDao().insertWorkoutLog(log)
    }

    suspend fun deleteWorkoutLog(log: WorkoutLogEntity) {
        database.healthDao().deleteWorkoutLog(log)
    }

    suspend fun saveDailyHealth(health: DailyHealthSummaryEntity) {
        database.healthDao().saveDailyHealth(health)
    }

    suspend fun addWaterIntake(amountMl: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
        val current = database.healthDao().getDailyHealth(todayStr).first()
        val updated = current?.copy(waterIntakeMl = (current.waterIntakeMl + amountMl).coerceAtLeast(0))
            ?: DailyHealthSummaryEntity(dateString = todayStr, waterIntakeMl = amountMl.coerceAtLeast(0))
        database.healthDao().saveDailyHealth(updated)
        if (amountMl > 0) {
            HydrationReminderManager.recordWaterLogged(context)
        }
    }

    suspend fun resetWaterIntake() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
        val current = database.healthDao().getDailyHealth(todayStr).first()
        val updated = current?.copy(waterIntakeMl = 0)
            ?: DailyHealthSummaryEntity(dateString = todayStr, waterIntakeMl = 0)
        database.healthDao().saveDailyHealth(updated)
        HydrationReminderManager.recordWaterLogged(context)
    }

    suspend fun updateWaterLimits(targetMl: Int, maxLimitMl: Int) {
        val safeTarget = targetMl.coerceIn(500, 6000)
        val safeMax = maxLimitMl.coerceIn(safeTarget, 8000)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
        val current = database.healthDao().getDailyHealth(todayStr).first()
        val updated = current?.copy(waterTargetMl = safeTarget, waterMaxLimitMl = safeMax)
            ?: DailyHealthSummaryEntity(
                dateString = todayStr,
                waterIntakeMl = 0,
                waterTargetMl = safeTarget,
                waterMaxLimitMl = safeMax
            )
        database.healthDao().saveDailyHealth(updated)
    }

    // Private File Safe Operations
    suspend fun importFilesToVault(
        uris: List<Uri>,
        autoHideOrigin: Boolean = true
    ): VaultImportResult = withContext(Dispatchers.IO) {
        var importedCount = 0
        var autoHiddenCount = 0
        val pendingDeleteUris = mutableListOf<Uri>()
        val recoverableIntents = mutableListOf<android.content.IntentSender>()

        uris.forEach { uri ->
            val entity = PrivateFileSafeHelper.importUriToVault(context, uri)
            if (entity != null) {
                database.vaultDao().insertVaultFile(entity)
                importedCount++

                if (autoHideOrigin) {
                    when (val outcome = PrivateFileSafeHelper.tryHideOriginalFromGallery(context, uri, entity.mimeType)) {
                        is PrivateFileSafeHelper.OriginHideOutcome.RemovedSilently -> {
                            autoHiddenCount++
                        }
                        is PrivateFileSafeHelper.OriginHideOutcome.NeedsMediaStoreDeleteRequest -> {
                            pendingDeleteUris.add(outcome.mediaUri)
                        }
                        is PrivateFileSafeHelper.OriginHideOutcome.NeedsRecoverableSecurityIntent -> {
                            recoverableIntents.add(outcome.intentSender)
                        }
                        is PrivateFileSafeHelper.OriginHideOutcome.NotRemovable -> Unit
                    }
                }
            }
        }

        VaultImportResult(
            importedCount = importedCount,
            autoHiddenFromOriginCount = autoHiddenCount,
            pendingMediaStoreDeleteUris = pendingDeleteUris.distinct(),
            recoverableSecurityIntents = recoverableIntents
        )
    }

    suspend fun exportVaultFile(vaultFile: VaultFileEntity, destinationUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            PrivateFileSafeHelper.exportVaultFileToUri(context, vaultFile, destinationUri)
        }

    suspend fun restoreVaultFileToGallery(
        vaultFile: VaultFileEntity,
        removeFromVault: Boolean = true
    ): VaultRestoreOutcome = withContext(Dispatchers.IO) {
        val outcome = PrivateFileSafeHelper.restoreVaultFileToGallery(context, vaultFile)
        if (outcome is VaultRestoreOutcome.RestoredToGallery && removeFromVault) {
            PrivateFileSafeHelper.deleteFromDisk(context, vaultFile)
            database.vaultDao().deleteVaultFile(vaultFile)
            outcome.copy(removedFromVault = true)
        } else {
            outcome
        }
    }

    suspend fun restoreAllVaultFilesToGallery(
        files: List<VaultFileEntity>,
        removeFromVault: Boolean = true
    ): VaultBatchRestoreResult = withContext(Dispatchers.IO) {
        var restored = 0
        var failed = 0
        var lastPath = ""
        val fallbackList = mutableListOf<VaultFileEntity>()
        for (file in files) {
            when (val outcome = restoreVaultFileToGallery(file, removeFromVault)) {
                is VaultRestoreOutcome.RestoredToGallery -> {
                    restored++
                    lastPath = outcome.destinationPath
                }
                is VaultRestoreOutcome.NeedsCustomExportPicker -> {
                    fallbackList.add(file)
                }
                is VaultRestoreOutcome.Failed -> {
                    failed++
                }
            }
        }
        VaultBatchRestoreResult(
            restoredCount = restored,
            fallbackExportNeeded = fallbackList,
            failedCount = failed,
            lastDestinationPath = lastPath
        )
    }

    suspend fun deleteVaultFile(vaultFile: VaultFileEntity) = withContext(Dispatchers.IO) {
        PrivateFileSafeHelper.deleteFromDisk(context, vaultFile)
        database.vaultDao().deleteVaultFile(vaultFile)
    }

    // Financial Analysis & Automated Monthly Report generator
    fun generateMonthlyReport(
        transactions: List<ExpenseEntity>,
        budgets: List<BudgetEntity>,
        targetMonthCal: Calendar = Calendar.getInstance()
    ): MonthlyReport {
        val cal = targetMonthCal.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val monthStart = cal.timeInMillis

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, daysInMonth)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        val monthEnd = cal.timeInMillis

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthYearName = monthFormat.format(targetMonthCal.time)

        val monthTransactions = transactions.filter { it.dateMillis in monthStart..monthEnd }

        var incomeTotal = 0.0
        var expenseTotal = 0.0
        val categoryExpenses = mutableMapOf<String, Double>()

        for (item in monthTransactions) {
            if (item.isExpense) {
                expenseTotal += item.amount
                categoryExpenses[item.category] = (categoryExpenses[item.category] ?: 0.0) + item.amount
            } else {
                incomeTotal += item.amount
            }
        }

        val budgetMap = budgets.associate { it.category to it.monthlyLimit }

        val breakdown = categoryExpenses.map { (cat, spent) ->
            val limit = budgetMap[cat] ?: 0.0
            val remaining = if (limit > 0) limit - spent else 0.0
            val budgetPercent = if (limit > 0) (spent / limit).toFloat() else 0f
            val totalPercent = if (expenseTotal > 0) (spent / expenseTotal).toFloat() else 0f
            CategorySpendingSummary(
                category = cat,
                totalSpent = spent,
                budgetLimit = limit,
                remainingBalance = remaining,
                percentageOfBudget = budgetPercent,
                percentageOfTotalSpending = totalPercent
            )
        }.sortedByDescending { it.totalSpent }

        val topCategory = breakdown.firstOrNull()?.category ?: "None"
        val netSavings = incomeTotal - expenseTotal
        val totalBudgetLimit = budgets.sumOf { it.monthlyLimit }
        val remainingBudgetBalance = if (totalBudgetLimit > 0) totalBudgetLimit - expenseTotal else netSavings
        val savingsRate = if (incomeTotal > 0) ((netSavings / incomeTotal) * 100).toFloat().coerceAtLeast(0f) else 0f
        val dailyAverage = if (daysInMonth > 0) expenseTotal / daysInMonth else 0.0

        return MonthlyReport(
            monthYear = monthYearName,
            totalIncome = incomeTotal,
            totalExpenses = expenseTotal,
            netSavings = netSavings,
            totalBudgetLimit = totalBudgetLimit,
            remainingBudgetBalance = remainingBudgetBalance,
            savingsRate = savingsRate,
            topSpendingCategory = topCategory,
            dailyAverageSpent = dailyAverage,
            categoryBreakdown = breakdown
        )
    }

    // Flowchart & Diagram Planning Operations
    suspend fun saveDiagram(diagram: DiagramEntity) {
        database.diagramDao().insertDiagram(diagram)
    }

    suspend fun updateDiagram(diagram: DiagramEntity) {
        database.diagramDao().updateDiagram(diagram)
    }

    suspend fun deleteDiagram(diagram: DiagramEntity) {
        database.diagramDao().deleteDiagram(diagram)
    }

    suspend fun saveNode(node: DiagramNodeEntity) {
        database.diagramDao().insertNode(node)
    }

    suspend fun deleteNode(node: DiagramNodeEntity) {
        database.diagramDao().deleteConnectionsForNode(node.id)
        database.diagramDao().deleteNode(node)
    }

    suspend fun saveConnection(connection: DiagramConnectionEntity) {
        database.diagramDao().insertConnection(connection)
    }

    suspend fun deleteConnection(connection: DiagramConnectionEntity) {
        database.diagramDao().deleteConnection(connection)
    }

    suspend fun autoArrangeDiagram(diagramId: String) {
        val nodes = database.diagramDao().getNodesForDiagramOnce(diagramId)
        val updatedNodes = nodes.mapIndexed { index, node ->
            val col = index % 2
            val row = index / 2
            node.copy(
                posX = 120f + col * 280f,
                posY = 80f + row * 190f
            )
        }
        database.diagramDao().insertNodes(updatedNodes)
    }

    suspend fun clearDiagramCanvas(diagramId: String, addStarterTopic: Boolean = false) {
        database.diagramDao().deleteAllConnectionsInDiagram(diagramId)
        database.diagramDao().deleteAllNodesInDiagram(diagramId)
        if (addStarterTopic) {
            val starter = DiagramNodeEntity(
                diagramId = diagramId,
                title = "Main Topic",
                description = "Tap Edit or drag shapes to expand",
                type = NodeType.START_END.name,
                posX = 160f,
                posY = 80f,
                colorHex = "#3B82F6",
                status = NodeStatus.TODO.name,
                orderIndex = 1
            )
            database.diagramDao().insertNode(starter)
        }
    }

    suspend fun createTemplateDiagram(templateName: String): DiagramEntity {
        val diagramId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        when (templateName) {
            "Decision Making Logic Tree" -> {
                val d = DiagramEntity(diagramId, "Decision Making Logic Tree", "Systematic evaluation workflow with Yes/No branches", DiagramStyle.CLASSIC_FLOWCHART.name, now, now)
                database.diagramDao().insertDiagram(d)
                val n1 = DiagramNodeEntity("dt_1_$diagramId", diagramId, "Problem Definition", "Clearly specify core objective", NodeType.START_END.name, 180f, 60f, "#3B82F6", NodeStatus.COMPLETED.name, 1)
                val n2 = DiagramNodeEntity("dt_2_$diagramId", diagramId, "Cost < Budget?", "Verify if cost is within monthly allocation", NodeType.DECISION.name, 180f, 200f, "#F59E0B", NodeStatus.IN_PROGRESS.name, 2)
                val n3 = DiagramNodeEntity("dt_3_$diagramId", diagramId, "Execute Solution", "Proceed with purchase / implementation", NodeType.PROCESS.name, 80f, 360f, "#10B981", NodeStatus.TODO.name, 3)
                val n4 = DiagramNodeEntity("dt_4_$diagramId", diagramId, "Reject or Postpone", "Re-negotiate terms or find alternatives", NodeType.PROCESS.name, 320f, 360f, "#EF4444", NodeStatus.TODO.name, 4)
                database.diagramDao().insertNodes(listOf(n1, n2, n3, n4))
                database.diagramDao().insertConnections(listOf(
                    DiagramConnectionEntity("c1_$diagramId", diagramId, n1.id, n2.id, "Evaluate"),
                    DiagramConnectionEntity("c2_$diagramId", diagramId, n2.id, n3.id, "Yes (< Budget)", "#10B981"),
                    DiagramConnectionEntity("c3_$diagramId", diagramId, n2.id, n4.id, "No (> Budget)", "#EF4444")
                ))
                return d
            }
            "Agile Sprint Pipeline" -> {
                val d = DiagramEntity(diagramId, "Agile Sprint Pipeline", "Sprint delivery pipeline with progress tracking", DiagramStyle.AGILE_ROADMAP.name, now, now)
                database.diagramDao().insertDiagram(d)
                val n1 = DiagramNodeEntity("ag_1_$diagramId", diagramId, "Sprint Backlog", "Groom user stories & estimate effort", NodeType.START_END.name, 160f, 60f, "#64748B", NodeStatus.COMPLETED.name, 1)
                val n2 = DiagramNodeEntity("ag_2_$diagramId", diagramId, "Sprint 1: Core Features", "Develop API endpoints & Compose UI", NodeType.PROCESS.name, 160f, 200f, "#3B82F6", NodeStatus.IN_PROGRESS.name, 2)
                val n3 = DiagramNodeEntity("ag_3_$diagramId", diagramId, "Code Review & Unit Tests", "Verify Robolectric tests pass", NodeType.DECISION.name, 160f, 340f, "#8B5CF6", NodeStatus.TODO.name, 3)
                val n4 = DiagramNodeEntity("ag_4_$diagramId", diagramId, "Production Release", "Ship version 1.0 to end users", NodeType.MILESTONE.name, 160f, 480f, "#10B981", NodeStatus.TODO.name, 4)
                database.diagramDao().insertNodes(listOf(n1, n2, n3, n4))
                database.diagramDao().insertConnections(listOf(
                    DiagramConnectionEntity("c1_$diagramId", diagramId, n1.id, n2.id, "Sprint Start"),
                    DiagramConnectionEntity("c2_$diagramId", diagramId, n2.id, n3.id, "Dev Done"),
                    DiagramConnectionEntity("c3_$diagramId", diagramId, n3.id, n4.id, "All Pass")
                ))
                return d
            }
            "Daily Morning Routine" -> {
                val d = DiagramEntity(diagramId, "Daily Success Routine", "Structured morning workflow for productivity & energy", DiagramStyle.STICKY_NOTES.name, now, now)
                database.diagramDao().insertDiagram(d)
                val n1 = DiagramNodeEntity("mr_1_$diagramId", diagramId, "6:00 AM - Wake & Hydrate", "500ml water with lemon electrolytes", NodeType.START_END.name, 120f, 60f, "#F59E0B", NodeStatus.COMPLETED.name, 1)
                val n2 = DiagramNodeEntity("mr_2_$diagramId", diagramId, "6:30 AM - 20m Morning Cardio", "Light jogging & stretching workout", NodeType.PROCESS.name, 120f, 210f, "#3B82F6", NodeStatus.COMPLETED.name, 2)
                val n3 = DiagramNodeEntity("mr_3_$diagramId", diagramId, "7:15 AM - High Protein Breakfast", "Eggs, avocado, whole wheat & black coffee", NodeType.PROCESS.name, 120f, 360f, "#10B981", NodeStatus.IN_PROGRESS.name, 3)
                val n4 = DiagramNodeEntity("mr_4_$diagramId", diagramId, "8:00 AM - Deep Work Session", "Top priority tasks with zero distractions", NodeType.MILESTONE.name, 120f, 510f, "#8B5CF6", NodeStatus.TODO.name, 4)
                database.diagramDao().insertNodes(listOf(n1, n2, n3, n4))
                database.diagramDao().insertConnections(listOf(
                    DiagramConnectionEntity("c1_$diagramId", diagramId, n1.id, n2.id, "Next"),
                    DiagramConnectionEntity("c2_$diagramId", diagramId, n2.id, n3.id, "Next"),
                    DiagramConnectionEntity("c3_$diagramId", diagramId, n3.id, n4.id, "Next")
                ))
                return d
            }
            else -> {
                // Product Launch Roadmap
                val d = DiagramEntity(diagramId, "Product Launch Roadmap", "End-to-end plan from concept to public release", DiagramStyle.MIND_MAP.name, now, now)
                database.diagramDao().insertDiagram(d)
                val n1 = DiagramNodeEntity("pl_1_$diagramId", diagramId, "Project Vision", "Define target users and problem statement", NodeType.START_END.name, 180f, 60f, "#10B981", NodeStatus.COMPLETED.name, 1)
                val n2 = DiagramNodeEntity("pl_2_$diagramId", diagramId, "Build MVP Features", "Implement offline maps, timers & budget", NodeType.PROCESS.name, 180f, 200f, "#3B82F6", NodeStatus.IN_PROGRESS.name, 2)
                val n3 = DiagramNodeEntity("pl_3_$diagramId", diagramId, "Beta User Feedback", "Gather usability insights and tune UX", NodeType.PROCESS.name, 180f, 340f, "#F59E0B", NodeStatus.TODO.name, 3)
                val n4 = DiagramNodeEntity("pl_4_$diagramId", diagramId, "Global Launch", "Publish APK and announce release", NodeType.MILESTONE.name, 180f, 480f, "#8B5CF6", NodeStatus.TODO.name, 4)
                database.diagramDao().insertNodes(listOf(n1, n2, n3, n4))
                database.diagramDao().insertConnections(listOf(
                    DiagramConnectionEntity("c1_$diagramId", diagramId, n1.id, n2.id, "Phase 1"),
                    DiagramConnectionEntity("c2_$diagramId", diagramId, n2.id, n3.id, "Phase 2"),
                    DiagramConnectionEntity("c3_$diagramId", diagramId, n3.id, n4.id, "Phase 3")
                ))
                return d
            }
        }
    }
}
