package com.example

import android.app.Application
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
import com.example.data.WorkoutLogEntity
import com.example.data.DiagramConnectionEntity
import com.example.data.DiagramEntity
import com.example.data.DiagramNodeEntity
import com.example.data.DiagramStyle
import com.example.model.AppCurrency
import com.example.ui.theme.ThemeMode
import com.example.data.HabitCompletionEntity
import com.example.data.HabitEntity
import com.example.data.HabitWithStats
import com.example.data.MilestoneEntity
import com.example.data.calculateHabitStreaks
import com.example.ui.components.SmartGoalActionType
import com.example.ui.components.SmartGoalSuggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDiagramNodes: StateFlow<List<DiagramNodeEntity>> = _selectedDiagramId
        .flatMapLatest { id ->
            if (id != null) repository.getNodesForDiagram(id) else MutableStateFlow(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentDiagramConnections: StateFlow<List<DiagramConnectionEntity>> = _selectedDiagramId
        .flatMapLatest { id ->
            if (id != null) repository.getConnectionsForDiagram(id) else MutableStateFlow(emptyList())
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

    // User Milestones & Badges StateFlow
    val milestones: StateFlow<List<MilestoneEntity>> = repository.milestonesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Expense Actions
    fun saveExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.saveExpense(expense)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun setBudget(category: String, limit: Double) {
        viewModelScope.launch {
            repository.setBudget(category, limit)
        }
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

    fun saveNode(node: DiagramNodeEntity) {
        viewModelScope.launch {
            repository.saveNode(node)
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
            triggerMilestonesEvaluation()
        }
    }

    // Milestones Evaluation Action
    fun triggerMilestonesEvaluation() {
        viewModelScope.launch {
            repository.milestoneEvaluator.evaluateAllMilestones(
                habits = habitsWithStats.value,
                transactions = transactions.value,
                budgets = budgets.value,
                dailyHealth = dailyHealth.value,
                workouts = workoutLogs.value,
                tasks = tasks.value,
                diagrams = diagrams.value
            )
        }
    }
}
