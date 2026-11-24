package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlarmEntity
import com.example.data.AppDatabase
import com.example.data.BudgetEntity
import com.example.data.CalorieLogEntity
import com.example.data.DailyHealthSummaryEntity
import com.example.data.ExpenseEntity
import com.example.data.TaskEntity
import com.example.data.TaskPulseRepository
import com.example.data.VaultFileEntity
import com.example.data.WorkoutLogEntity
import com.example.data.DiagramConnectionEntity
import com.example.data.DiagramEntity
import com.example.data.DiagramNodeEntity
import com.example.data.DiagramStyle
import com.example.model.AppCurrency
import com.example.service.BackgroundMusicManager
import com.example.service.BackgroundMusicState
import com.example.service.BudgetThresholdAlert
import com.example.service.BudgetThresholdManager
import com.example.service.BudgetThresholdState
import com.example.service.HydrationReminderManager
import com.example.service.HydrationReminderState
import com.example.service.ReceiptScanUiState
import com.example.service.ReceiptScannerService
import com.example.ui.theme.ThemeMode
import com.example.data.HabitCompletionEntity
import com.example.data.HabitEntity
import com.example.data.HabitWithStats
import com.example.data.calculateHabitStreaks
import com.example.ui.components.SmartGoalActionType
import com.example.ui.components.SmartGoalSuggestion
import com.example.util.DisplayRefreshRateHelper
import com.example.util.RefreshRatePreference
import com.example.util.VaultBatchRestoreResult
import com.example.util.VaultImportResult
import com.example.util.VaultRestoreOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val repository = TaskPulseRepository(application, database)

    val tasks: StateFlow<List<TaskEntity>> = repository.tasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alarms: StateFlow<List<AlarmEntity>> = repository.alarmsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<ExpenseEntity>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = repository.budgetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flowchart & Diagram Planning StateFlows
    val diagrams: StateFlow<List<DiagramEntity>> = repository.diagramsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDiagramId = MutableStateFlow<String?>(null)
    val selectedDiagramId: StateFlow<String?> = _selectedDiagramId.asStateFlow()

    val activeDiagram: StateFlow<DiagramEntity?> = combine(_selectedDiagramId, diagrams) { selectedId, list ->
        list.find { it.id == selectedId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDiagramNodes: StateFlow<List<DiagramNodeEntity>> = activeDiagram
        .flatMapLatest { diagram ->
            if (diagram != null) repository.getNodesForDiagram(diagram.id) else MutableStateFlow(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDiagramConnections: StateFlow<List<DiagramConnectionEntity>> = activeDiagram
        .flatMapLatest { diagram ->
            if (diagram != null) repository.getConnectionsForDiagram(diagram.id) else MutableStateFlow(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Health & Fitness StateFlows
    val calorieLogs: StateFlow<List<CalorieLogEntity>> = repository.calorieLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workoutLogs: StateFlow<List<WorkoutLogEntity>> = repository.workoutLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val todayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
    val dailyHealth: StateFlow<DailyHealthSummaryEntity?> = repository.getDailyHealthFlow(todayString)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Habit Tracker StateFlows & Combined Stats
    val habits: StateFlow<List<HabitEntity>> = repository.habitsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHabitCompletions: StateFlow<List<HabitCompletionEntity>> = repository.allHabitCompletionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitsWithStats: StateFlow<List<HabitWithStats>> = combine(habits, allHabitCompletions) { habitList, completions ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
        habitList.map { habit ->
            val habitDates = completions.filter { it.habitId == habit.id }.map { it.dateString }.toSet()
            val (currentStreak, bestStreak) = calculateHabitStreaks(habitDates)
            HabitWithStats(
                habit = habit,
                isCompletedToday = habitDates.contains(todayStr),
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                totalCompletions = habitDates.size,
                completionDates = habitDates
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Private File Safe Vault StateFlow
    val vaultFiles: StateFlow<List<VaultFileEntity>> = repository.vaultFilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Finance Privacy Mask State (Show / Hide Income & Expenses as ******)
    private val financePrefs = application.getSharedPreferences("taskpulse_finance_prefs", Context.MODE_PRIVATE)
    private val _hideFinanceAmounts = MutableStateFlow(financePrefs.getBoolean("hide_finance_amounts", false))
    val hideFinanceAmounts: StateFlow<Boolean> = _hideFinanceAmounts.asStateFlow()

    fun toggleHideFinanceAmounts() {
        val next = !_hideFinanceAmounts.value
        _hideFinanceAmounts.value = next
        financePrefs.edit().putBoolean("hide_finance_amounts", next).apply()
    }

    // Camera Receipt Scanner AI State
    private val receiptScannerService by lazy { ReceiptScannerService() }
    private val _receiptScanState = MutableStateFlow<ReceiptScanUiState>(ReceiptScanUiState.Idle)
    val receiptScanState: StateFlow<ReceiptScanUiState> = _receiptScanState.asStateFlow()

    fun scanReceiptBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _receiptScanState.value = ReceiptScanUiState.Scanning
            _receiptScanState.value = receiptScannerService.extractReceipt(bitmap)
        }
    }

    fun resetReceiptScanState() {
        _receiptScanState.value = ReceiptScanUiState.Idle
    }

    // Currency State
    private val _currency = MutableStateFlow(AppCurrency.PHP)
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    fun setCurrency(newCurrency: AppCurrency) {
        _currency.value = newCurrency
    }

    // Theme Mode State
    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    // Ultra / Max Display Refresh Rate Preference
    private val _refreshRatePreference = MutableStateFlow(DisplayRefreshRateHelper.loadPreference(application))
    val refreshRatePreference: StateFlow<RefreshRatePreference> = _refreshRatePreference.asStateFlow()

    fun setRefreshRatePreference(pref: RefreshRatePreference) {
        _refreshRatePreference.value = pref
        DisplayRefreshRateHelper.savePreference(getApplication(), pref)
    }

    // Background Music, Hydration Reminder, and Budget Threshold State & Actions
    init {
        BackgroundMusicManager.init(application)
        HydrationReminderManager.init(application)
        BudgetThresholdManager.init(application)
    }

    val bgmState: StateFlow<BackgroundMusicState> = BackgroundMusicManager.state
    val hydrationReminderState: StateFlow<HydrationReminderState> = HydrationReminderManager.state
    val budgetThresholdState: StateFlow<BudgetThresholdState> = BudgetThresholdManager.state

    val activeBudgetAlerts: StateFlow<List<BudgetThresholdAlert>> = combine(
        transactions,
        budgets,
        budgetThresholdState
    ) { txList, budgetList, thresholdConfig ->
        BudgetThresholdManager.evaluateCategoryThresholds(
            transactions = txList,
            budgets = budgetList,
            state = thresholdConfig
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun importBackgroundMusic(uri: Uri, onResult: (Result<String>) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                BackgroundMusicManager.importMusicFile(getApplication(), uri)
            }
            onResult(result)
        }
    }

    fun clearBackgroundMusic() {
        BackgroundMusicManager.clearCustomTrack(getApplication())
    }

    fun toggleBgmPlayPause() {
        BackgroundMusicManager.togglePlayPause(getApplication())
    }

    fun setBgmEnabled(enabled: Boolean) {
        BackgroundMusicManager.setEnabled(getApplication(), enabled)
    }

    fun setBgmAutoPlayOnOpen(autoPlay: Boolean) {
        BackgroundMusicManager.setAutoPlayOnOpen(getApplication(), autoPlay)
    }

    fun setBgmLoop(loop: Boolean) {
        BackgroundMusicManager.setLoop(getApplication(), loop)
    }

    fun setBgmPauseOnLeave(pause: Boolean) {
        BackgroundMusicManager.setPauseWhenLeavingApp(getApplication(), pause)
    }

    fun setBgmVolume(volume: Float) {
        BackgroundMusicManager.setVolume(getApplication(), volume)
    }

    // Task Actions
    fun saveTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.saveTask(task)
        }
    }

    fun toggleTaskComplete(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(id, completed)
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Alarm Actions
    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            repository.saveAlarm(alarm)
        }
    }

    fun toggleAlarm(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlarmEnabled(id, enabled)
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
        }
    }

    // Expense & Budget Threshold Actions
    fun saveExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.saveExpense(expense, _currency.value)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense, _currency.value)
        }
    }

    fun setBudget(category: String, limit: Double) {
        viewModelScope.launch {
            repository.setBudget(category, limit, _currency.value)
        }
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        BudgetThresholdManager.setAlertsEnabled(getApplication(), enabled)
        if (enabled) {
            BudgetThresholdManager.checkAndNotifyThresholds(
                context = getApplication(),
                transactions = transactions.value,
                budgets = budgets.value,
                currency = _currency.value
            )
        }
    }

    fun setDefaultBudgetThresholdPercent(percent: Int) {
        BudgetThresholdManager.setDefaultThresholdPercent(getApplication(), percent)
        BudgetThresholdManager.checkAndNotifyThresholds(
            context = getApplication(),
            transactions = transactions.value,
            budgets = budgets.value,
            currency = _currency.value
        )
    }

    fun setCategoryBudgetThresholdPercent(category: String, percent: Int?) {
        BudgetThresholdManager.setCategoryThresholdPercent(getApplication(), category, percent)
        BudgetThresholdManager.checkAndNotifyThresholds(
            context = getApplication(),
            transactions = transactions.value,
            budgets = budgets.value,
            currency = _currency.value,
            targetCategory = category
        )
    }

    fun setHydrationRemindersEnabled(enabled: Boolean) {
        HydrationReminderManager.setRemindersEnabled(getApplication(), enabled)
    }

    fun refreshHydrationReminderStatus() {
        HydrationReminderManager.refreshDndStatus(getApplication())
        val now = System.currentTimeMillis()
        val startOfDayCal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = startOfDayCal.timeInMillis
        val todayWorkouts = workoutLogs.value.filter { it.timestampMillis >= startOfDay }
        val todayMeals = calorieLogs.value.filter { it.timestampMillis >= startOfDay }
        HydrationReminderManager.checkAndNotifyIfDue(
            context = getApplication(),
            dailyHealth = dailyHealth.value,
            todayWorkouts = todayWorkouts,
            todayCalories = todayMeals,
            nowMillis = now
        )
    }

    // Health Actions
    fun saveCalorieLog(log: CalorieLogEntity) {
        viewModelScope.launch {
            repository.saveCalorieLog(log)
        }
    }

    fun deleteCalorieLog(log: CalorieLogEntity) {
        viewModelScope.launch {
            repository.deleteCalorieLog(log)
        }
    }

    fun saveWorkoutLog(log: WorkoutLogEntity) {
        viewModelScope.launch {
            repository.saveWorkoutLog(log)
        }
    }

    fun deleteWorkoutLog(log: WorkoutLogEntity) {
        viewModelScope.launch {
            repository.deleteWorkoutLog(log)
        }
    }

    fun addWaterIntake(amountMl: Int) {
        viewModelScope.launch {
            repository.addWaterIntake(amountMl)
        }
    }

    fun resetWaterIntake() {
        viewModelScope.launch {
            repository.resetWaterIntake()
        }
    }

    fun updateWaterLimits(targetMl: Int, maxLimitMl: Int) {
        viewModelScope.launch {
            repository.updateWaterLimits(targetMl, maxLimitMl)
        }
    }

    // Private File Safe Vault Actions
    fun importFilesToVault(
        uris: List<Uri>,
        autoHideOrigin: Boolean = true,
        onComplete: (VaultImportResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.importFilesToVault(uris, autoHideOrigin)
            onComplete(result)
        }
    }

    fun exportVaultFile(vaultFile: VaultFileEntity, destinationUri: Uri, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.exportVaultFile(vaultFile, destinationUri)
            onResult(ok)
        }
    }

    fun restoreVaultFileToGallery(
        vaultFile: VaultFileEntity,
        removeFromVault: Boolean = true,
        onResult: (VaultRestoreOutcome) -> Unit = {}
    ) {
        viewModelScope.launch {
            val outcome = repository.restoreVaultFileToGallery(vaultFile, removeFromVault)
            onResult(outcome)
        }
    }

    fun restoreAllVaultFilesToGallery(
        files: List<VaultFileEntity>,
        removeFromVault: Boolean = true,
        onResult: (VaultBatchRestoreResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.restoreAllVaultFilesToGallery(files, removeFromVault)
            onResult(result)
        }
    }

    fun deleteVaultFile(vaultFile: VaultFileEntity) {
        viewModelScope.launch {
            repository.deleteVaultFile(vaultFile)
        }
    }

    // Flowchart & Diagram Planning Actions
    fun selectDiagram(diagram: DiagramEntity) {
        _selectedDiagramId.value = diagram.id
    }

    fun createDiagram(title: String, style: DiagramStyle) {
        viewModelScope.launch {
            val newDiagram = DiagramEntity(
                id = java.util.UUID.randomUUID().toString(),
                title = title,
                style = style.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveDiagram(newDiagram)
            _selectedDiagramId.value = newDiagram.id
        }
    }

    fun updateDiagramStyle(diagram: DiagramEntity, style: DiagramStyle) {
        viewModelScope.launch {
            repository.updateDiagram(diagram.copy(style = style.name, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteDiagram(diagram: DiagramEntity) {
        viewModelScope.launch {
            repository.deleteDiagram(diagram)
            if (_selectedDiagramId.value == diagram.id) {
                _selectedDiagramId.value = null
            }
        }
    }

    private suspend fun ensureActiveDiagramId(preferredId: String?): String {
        if (!preferredId.isNullOrBlank()) {
            return preferredId
        }
        val existing = activeDiagram.value ?: diagrams.value.firstOrNull()
        if (existing != null) {
            _selectedDiagramId.value = existing.id
            return existing.id
        }
        val now = System.currentTimeMillis()
        val created = DiagramEntity(
            id = java.util.UUID.randomUUID().toString(),
            title = "Planning Flowchart",
            description = "Custom interactive flowchart",
            style = DiagramStyle.CLASSIC_FLOWCHART.name,
            createdAt = now,
            updatedAt = now
        )
        repository.saveDiagram(created)
        _selectedDiagramId.value = created.id
        return created.id
    }

    fun saveNode(node: DiagramNodeEntity) {
        viewModelScope.launch {
            val resolvedDiagramId = ensureActiveDiagramId(node.diagramId)
            repository.saveNode(node.copy(diagramId = resolvedDiagramId))
        }
    }

    fun addShapeWithOptionalConnection(
        node: DiagramNodeEntity,
        connectFromNodeId: String?,
        connectionLabel: String
    ) {
        viewModelScope.launch {
            val resolvedDiagramId = ensureActiveDiagramId(node.diagramId)
            val savedNode = node.copy(diagramId = resolvedDiagramId)
            repository.saveNode(savedNode)
            if (!connectFromNodeId.isNullOrBlank()) {
                repository.saveConnection(
                    DiagramConnectionEntity(
                        diagramId = resolvedDiagramId,
                        fromNodeId = connectFromNodeId,
                        toNodeId = savedNode.id,
                        label = connectionLabel.trim(),
                        colorHex = savedNode.colorHex
                    )
                )
            }
        }
    }

    fun deleteNode(node: DiagramNodeEntity) {
        viewModelScope.launch {
            repository.deleteNode(node)
        }
    }

    fun moveNode(nodeId: String, newX: Float, newY: Float) {
        viewModelScope.launch {
            val currentNodes = currentDiagramNodes.value
            val target = currentNodes.find { it.id == nodeId }
            if (target != null) {
                repository.saveNode(target.copy(posX = newX, posY = newY))
            }
        }
    }

    fun saveConnection(connection: DiagramConnectionEntity) {
        viewModelScope.launch {
            repository.saveConnection(connection)
        }
    }

    fun deleteConnection(connection: DiagramConnectionEntity) {
        viewModelScope.launch {
            repository.deleteConnection(connection)
        }
    }

    fun autoArrangeDiagram(diagramId: String) {
        viewModelScope.launch {
            repository.autoArrangeDiagram(diagramId)
        }
    }

    fun resetDiagramCanvas(diagramId: String, addStarterTopic: Boolean = false) {
        viewModelScope.launch {
            repository.clearDiagramCanvas(diagramId, addStarterTopic)
        }
    }

    fun renameDiagram(diagram: DiagramEntity, newTitle: String, newDescription: String) {
        viewModelScope.launch {
            repository.updateDiagram(
                diagram.copy(
                    title = newTitle.trim().ifBlank { diagram.title },
                    description = newDescription.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun loadDiagramTemplate(templateName: String) {
        viewModelScope.launch {
            val d = repository.createTemplateDiagram(templateName)
            _selectedDiagramId.value = d.id
        }
    }

    // Habit Actions
    fun saveHabit(habit: HabitEntity) {
        viewModelScope.launch {
            repository.saveHabit(habit)
        }
    }

    fun deleteHabit(habit: HabitEntity) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    fun toggleHabitToday(habitId: Long) {
        viewModelScope.launch {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
            repository.toggleHabitCompletion(habitId, todayStr)
        }
    }

    // Smart Goal Adoption Action
    fun adoptSmartGoal(goal: SmartGoalSuggestion) {
        viewModelScope.launch {
            when (goal.actionType) {
                SmartGoalActionType.SET_FOOD_BUDGET -> {
                    repository.setBudget("Food & Dining", goal.defaultTargetValue)
                }
                SmartGoalActionType.SET_SAVINGS_TARGET -> {
                    repository.setBudget("Savings & Reserves", goal.defaultTargetValue)
                }
                SmartGoalActionType.SET_SHOPPING_BUDGET -> {
                    repository.setBudget("Shopping & Retail", goal.defaultTargetValue)
                }
                SmartGoalActionType.CREATE_HABIT_WATER -> {
                    repository.saveHabit(
                        HabitEntity(
                            title = "💧 Drink 2.5L Water Daily",
                            category = "Health",
                            iconName = "WaterDrop",
                            colorHex = "#0EA5E9",
                            targetDaysPerWeek = 7
                        )
                    )
                }
                SmartGoalActionType.CREATE_HABIT_POMODORO -> {
                    repository.saveHabit(
                        HabitEntity(
                            title = "🍅 4x Pomodoro Focus Blocks",
                            category = "Productivity",
                            iconName = "Check",
                            colorHex = "#F97316",
                            targetDaysPerWeek = 5
                        )
                    )
                }
            }
        }
    }
}
