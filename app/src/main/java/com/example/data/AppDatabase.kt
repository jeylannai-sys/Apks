package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Database(
    entities = [
        TaskEntity::class,
        AlarmEntity::class,
        ExpenseEntity::class,
        BudgetEntity::class,
        CalorieLogEntity::class,
        WorkoutLogEntity::class,
        DailyHealthSummaryEntity::class,
        DiagramEntity::class,
        DiagramNodeEntity::class,
        DiagramConnectionEntity::class,
        HabitEntity::class,
        HabitCompletionEntity::class,
        MilestoneEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun alarmDao(): AlarmDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun healthDao(): HealthDao
    abstract fun diagramDao(): DiagramDao
    abstract fun habitDao(): HabitDao
    abstract fun milestoneDao(): MilestoneDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taskpulse_database.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(context)
                                seedInitialData(database)
                            }
                        }
                    }).build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(database: AppDatabase) {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()

            // Seed Budgets
            val defaultBudgets = listOf(
                BudgetEntity("Food & Dining", 15000.0),
                BudgetEntity("Groceries", 12000.0),
                BudgetEntity("Housing & Rent", 25000.0),
                BudgetEntity("Utilities & Bills", 8000.0),
                BudgetEntity("Transportation", 5000.0),
                BudgetEntity("Entertainment", 4000.0),
                BudgetEntity("Health & Fitness", 3500.0)
            )
            defaultBudgets.forEach { database.budgetDao().setBudget(it) }

            // Seed Tasks with repeat schedules
            cal.timeInMillis = now
            cal.add(Calendar.HOUR_OF_DAY, 2)
            val task1Deadline = cal.timeInMillis

            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val task2Deadline = cal.timeInMillis

            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, 3)
            val task3Deadline = cal.timeInMillis

            database.taskDao().insertTask(
                TaskEntity(
                    title = "Review monthly budget & investments",
                    description = "Analyze category spending velocity and adjust savings targets.",
                    dueDateMillis = task1Deadline,
                    priority = "HIGH",
                    category = "Finance",
                    isCompleted = false,
                    hasAlarm = true,
                    alarmRingtone = "DIGITAL_CHIME",
                    repeatDaysBitmask = 2 or 16, // Monday & Thursday
                    repeatMode = "CUSTOM_DAYS"
                )
            )
            database.taskDao().insertTask(
                TaskEntity(
                    title = "Morning workout & calorie tracking",
                    description = "Cardio session + track post-workout nutrition.",
                    dueDateMillis = task2Deadline,
                    priority = "MEDIUM",
                    category = "Health",
                    isCompleted = false,
                    hasAlarm = true,
                    alarmRingtone = "ENERGY_SYNTH",
                    repeatDaysBitmask = 127, // Daily
                    repeatMode = "DAILY"
                )
            )
            database.taskDao().insertTask(
                TaskEntity(
                    title = "Prepare quarterly project deliverables",
                    description = "Sync with engineering team on prototype verification.",
                    dueDateMillis = task3Deadline,
                    priority = "MEDIUM",
                    category = "Work",
                    isCompleted = false,
                    hasAlarm = false,
                    alarmRingtone = "RADAR_PULSE"
                )
            )

            // Seed Alarms
            database.alarmDao().insertAlarm(
                AlarmEntity(
                    label = "Morning Focus & Workout",
                    hour = 7,
                    minute = 0,
                    daysBitmask = 127,
                    isEnabled = true,
                    ringtone = "ENERGY_SYNTH",
                    vibrate = true
                )
            )
            database.alarmDao().insertAlarm(
                AlarmEntity(
                    label = "Midday Financial & Calorie Check",
                    hour = 12,
                    minute = 30,
                    daysBitmask = 62, // Mon-Fri
                    isEnabled = true,
                    ringtone = "DIGITAL_CHIME",
                    vibrate = true
                )
            )

            // Seed sample transactions
            val expenseDao = database.expenseDao()
            cal.timeInMillis = now
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val startOfMonth = cal.timeInMillis

            expenseDao.insertExpense(
                ExpenseEntity(
                    title = "Monthly Salary Deposit",
                    amount = 85000.0,
                    category = "Salary & Work",
                    dateMillis = startOfMonth + 86400000L,
                    isExpense = false,
                    note = "Direct payroll deposit"
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    title = "Condo / Apartment Lease",
                    amount = 22000.0,
                    category = "Housing & Rent",
                    dateMillis = startOfMonth + (86400000L * 2),
                    isExpense = true,
                    note = "Monthly lease"
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    title = "Supermarket & Produce",
                    amount = 3850.50,
                    category = "Groceries",
                    dateMillis = now - (86400000L * 3),
                    isExpense = true,
                    note = "Fresh vegetables, eggs, lean meats"
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    title = "Bistro Lunch with colleagues",
                    amount = 840.00,
                    category = "Food & Dining",
                    dateMillis = now - (86400000L * 1),
                    isExpense = true,
                    note = "Team lunch meal"
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    title = "High-speed Fiber & Utility",
                    amount = 2650.00,
                    category = "Utilities & Bills",
                    dateMillis = now - (86400000L * 2),
                    isExpense = true,
                    note = "Electricity & internet"
                )
            )
            expenseDao.insertExpense(
                ExpenseEntity(
                    title = "Commute & Fuel recharge",
                    amount = 1200.00,
                    category = "Transportation",
                    dateMillis = now - 3600000L * 4,
                    isExpense = true,
                    note = "Transit pass"
                )
            )

            // Seed Health & Fitness Starter Data
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
            database.healthDao().saveDailyHealth(
                DailyHealthSummaryEntity(
                    dateString = todayStr,
                    waterIntakeMl = 1750,
                    waterTargetMl = 2500,
                    calorieTarget = 2200,
                    stepsCount = 6420,
                    weightKg = 68.5
                )
            )

            database.healthDao().insertCalorieLog(
                CalorieLogEntity(
                    foodName = "Avocado Toast with Poached Eggs",
                    calories = 420,
                    proteinGrams = 18.0,
                    carbsGrams = 32.0,
                    fatGrams = 24.0,
                    mealType = "Breakfast",
                    portionSize = "2 slices with 2 eggs",
                    timestampMillis = now - 3600000L * 5
                )
            )
            database.healthDao().insertCalorieLog(
                CalorieLogEntity(
                    foodName = "Grilled Chicken Breast with Brown Rice",
                    calories = 560,
                    proteinGrams = 46.0,
                    carbsGrams = 52.0,
                    fatGrams = 12.0,
                    mealType = "Lunch",
                    portionSize = "1 bowl (200g chicken, 150g rice)",
                    timestampMillis = now - 3600000L * 2
                )
            )

            database.healthDao().insertWorkoutLog(
                WorkoutLogEntity(
                    activityName = "Morning Outdoor Run",
                    caloriesBurned = 380,
                    durationMinutes = 35,
                    timestampMillis = now - 3600000L * 6
                )
            )

            // Seed Initial Planning Flowchart & Diagram
            val defaultDiagramId = "diagram_product_launch_001"
            val defaultDiagram = DiagramEntity(
                id = defaultDiagramId,
                title = "Product Launch & Sprint Plan",
                description = "End-to-end planning flowchart from concept to public release",
                style = DiagramStyle.CLASSIC_FLOWCHART.name,
                createdAt = now,
                updatedAt = now
            )
            database.diagramDao().insertDiagram(defaultDiagram)

            val n1 = DiagramNodeEntity(
                id = "node_1",
                diagramId = defaultDiagramId,
                title = "1. Idea & Scope",
                description = "Define product objectives & target audience",
                type = NodeType.START_END.name,
                posX = 200f,
                posY = 80f,
                colorHex = "#10B981",
                status = NodeStatus.COMPLETED.name,
                orderIndex = 1
            )
            val n2 = DiagramNodeEntity(
                id = "node_2",
                diagramId = defaultDiagramId,
                title = "2. Build MVP Core",
                description = "Develop core UI, offline caching & databases",
                type = NodeType.PROCESS.name,
                posX = 200f,
                posY = 220f,
                colorHex = "#3B82F6",
                status = NodeStatus.IN_PROGRESS.name,
                orderIndex = 2
            )
            val n3 = DiagramNodeEntity(
                id = "node_3",
                diagramId = defaultDiagramId,
                title = "3. Quality Gate Pass?",
                description = "Are all automated tests & security rules passing?",
                type = NodeType.DECISION.name,
                posX = 200f,
                posY = 360f,
                colorHex = "#F59E0B",
                status = NodeStatus.TODO.name,
                orderIndex = 3
            )
            val n4 = DiagramNodeEntity(
                id = "node_4",
                diagramId = defaultDiagramId,
                title = "4. Fix Issues & Refactor",
                description = "Address edge cases, optimize performance",
                type = NodeType.PROCESS.name,
                posX = 450f,
                posY = 360f,
                colorHex = "#EF4444",
                status = NodeStatus.TODO.name,
                orderIndex = 4
            )
            val n5 = DiagramNodeEntity(
                id = "node_5",
                diagramId = defaultDiagramId,
                title = "5. Deploy & Launch",
                description = "Ship APK release to production users",
                type = NodeType.MILESTONE.name,
                posX = 200f,
                posY = 500f,
                colorHex = "#8B5CF6",
                status = NodeStatus.TODO.name,
                orderIndex = 5
            )
            database.diagramDao().insertNodes(listOf(n1, n2, n3, n4, n5))

            database.diagramDao().insertConnections(
                listOf(
                    DiagramConnectionEntity(
                        id = "conn_1_2",
                        diagramId = defaultDiagramId,
                        fromNodeId = "node_1",
                        toNodeId = "node_2",
                        label = "Spec Approved",
                        colorHex = "#10B981"
                    ),
                    DiagramConnectionEntity(
                        id = "conn_2_3",
                        diagramId = defaultDiagramId,
                        fromNodeId = "node_2",
                        toNodeId = "node_3",
                        label = "Code Freeze",
                        colorHex = "#3B82F6"
                    ),
                    DiagramConnectionEntity(
                        id = "conn_3_4",
                        diagramId = defaultDiagramId,
                        fromNodeId = "node_3",
                        toNodeId = "node_4",
                        label = "No (Fails)",
                        colorHex = "#EF4444"
                    ),
                    DiagramConnectionEntity(
                        id = "conn_4_2",
                        diagramId = defaultDiagramId,
                        fromNodeId = "node_4",
                        toNodeId = "node_2",
                        label = "Re-test",
                        colorHex = "#F59E0B"
                    ),
                    DiagramConnectionEntity(
                        id = "conn_3_5",
                        diagramId = defaultDiagramId,
                        fromNodeId = "node_3",
                        toNodeId = "node_5",
                        label = "Yes (Passes)",
                        colorHex = "#8B5CF6"
                    )
                )
            )

            // Seed Initial Habits and Streak Completions
            val habitDao = database.habitDao()
            val h1Id = habitDao.insertHabit(
                HabitEntity(
                    title = "Drink 2.5L Water Daily",
                    description = "Stay consistently hydrated throughout the day",
                    category = "Health",
                    iconName = "WaterDrop",
                    colorHex = "#0EA5E9",
                    targetDaysPerWeek = 7
                )
            )
            val h2Id = habitDao.insertHabit(
                HabitEntity(
                    title = "Log Daily Income & Expenses",
                    description = "Track financial transactions before sleep",
                    category = "Finance",
                    iconName = "AccountBalanceWallet",
                    colorHex = "#10B981",
                    targetDaysPerWeek = 7
                )
            )
            val h3Id = habitDao.insertHabit(
                HabitEntity(
                    title = "30-Min Workout / Cardio",
                    description = "Exercise or outdoor walk to burn calories",
                    category = "Fitness",
                    iconName = "FitnessCenter",
                    colorHex = "#F97316",
                    targetDaysPerWeek = 5
                )
            )
            val h4Id = habitDao.insertHabit(
                HabitEntity(
                    title = "Complete Top 3 Priority Tasks",
                    description = "Deep focus on highest impact objectives",
                    category = "Productivity",
                    iconName = "CheckCircle",
                    colorHex = "#8B5CF6",
                    targetDaysPerWeek = 5
                )
            )

            // Seed past completion dates to light up heatmap streaks
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            for (daysAgo in 0..14) {
                cal.timeInMillis = now
                cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                val dStr = dateFormat.format(cal.time)

                // H1 & H2 daily completions for 14 days streak
                habitDao.recordCompletion(HabitCompletionEntity(habitId = h1Id, dateString = dStr, completedAtMillis = cal.timeInMillis))
                habitDao.recordCompletion(HabitCompletionEntity(habitId = h2Id, dateString = dStr, completedAtMillis = cal.timeInMillis))

                // H3 & H4 on weekdays
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                if (dayOfWeek != Calendar.SUNDAY) {
                    habitDao.recordCompletion(HabitCompletionEntity(habitId = h3Id, dateString = dStr, completedAtMillis = cal.timeInMillis))
                    habitDao.recordCompletion(HabitCompletionEntity(habitId = h4Id, dateString = dStr, completedAtMillis = cal.timeInMillis))
                }
            }

            // Seed Initial Milestones (Badges)
            val defaultMilestones = listOf(
                MilestoneEntity(
                    id = "streak_3",
                    title = "Flame Starter",
                    description = "Maintain a 3-day daily streak on any habit",
                    category = "HABIT",
                    tier = "BRONZE",
                    badgeColorHex = "#F97316",
                    iconName = "LocalFireDepartment",
                    isUnlocked = true,
                    unlockedAtMillis = now - 86400000L * 3,
                    currentProgress = 3,
                    targetProgress = 3
                ),
                MilestoneEntity(
                    id = "streak_7",
                    title = "Consistency Master",
                    description = "Hit a 7-day unbroken habit streak",
                    category = "HABIT",
                    tier = "SILVER",
                    badgeColorHex = "#38BDF8",
                    iconName = "LocalFireDepartment",
                    isUnlocked = true,
                    unlockedAtMillis = now - 86400000L,
                    currentProgress = 7,
                    targetProgress = 7
                ),
                MilestoneEntity(
                    id = "streak_21",
                    title = "Habit Virtuoso",
                    description = "Achieve the scientific 21-day neurological habit lock",
                    category = "HABIT",
                    tier = "GOLD",
                    badgeColorHex = "#EAB308",
                    iconName = "LocalFireDepartment",
                    isUnlocked = false,
                    currentProgress = 14,
                    targetProgress = 21
                ),
                MilestoneEntity(
                    id = "save_first",
                    title = "First Coin Saved",
                    description = "Record a positive net savings balance for the month",
                    category = "FINANCE",
                    tier = "BRONZE",
                    badgeColorHex = "#10B981",
                    iconName = "Savings",
                    isUnlocked = true,
                    unlockedAtMillis = now - 86400000L * 5,
                    currentProgress = 1,
                    targetProgress = 1
                ),
                MilestoneEntity(
                    id = "save_frugal_20",
                    title = "Frugal Titan",
                    description = "Attain a monthly savings rate of 20% or higher",
                    category = "FINANCE",
                    tier = "SILVER",
                    badgeColorHex = "#6366F1",
                    iconName = "TrendingUp",
                    isUnlocked = true,
                    unlockedAtMillis = now - 86400000L * 2,
                    currentProgress = 1,
                    targetProgress = 1
                ),
                MilestoneEntity(
                    id = "save_under_budget",
                    title = "Budget Guardian",
                    description = "Keep all discretionary spending categories strictly under limits",
                    category = "FINANCE",
                    tier = "GOLD",
                    badgeColorHex = "#F59E0B",
                    iconName = "Shield",
                    isUnlocked = false,
                    currentProgress = 0,
                    targetProgress = 1
                ),
                MilestoneEntity(
                    id = "fit_water_2500",
                    title = "Hydration Hero",
                    description = "Drink 2,500ml or more of water in a single day",
                    category = "FITNESS",
                    tier = "BRONZE",
                    badgeColorHex = "#0EA5E9",
                    iconName = "WaterDrop",
                    isUnlocked = true,
                    unlockedAtMillis = now - 86400000L,
                    currentProgress = 2500,
                    targetProgress = 2500
                ),
                MilestoneEntity(
                    id = "fit_workouts_3",
                    title = "Cardio Warrior",
                    description = "Log at least 3 workouts or cardio sessions in a week",
                    category = "FITNESS",
                    tier = "SILVER",
                    badgeColorHex = "#EF4444",
                    iconName = "FitnessCenter",
                    isUnlocked = true,
                    unlockedAtMillis = now - 86400000L * 4,
                    currentProgress = 3,
                    targetProgress = 3
                ),
                MilestoneEntity(
                    id = "fit_steps_10k",
                    title = "10K Step Crusher",
                    description = "Reach 10,000 steps logged in a single day",
                    category = "FITNESS",
                    tier = "GOLD",
                    badgeColorHex = "#EAB308",
                    iconName = "DirectionsRun",
                    isUnlocked = false,
                    currentProgress = 6200,
                    targetProgress = 10000
                ),
                MilestoneEntity(
                    id = "prod_pomodoro_4",
                    title = "Deep Work Pro",
                    description = "Complete 4 Pomodoro focus sessions in one day",
                    category = "PRODUCTIVITY",
                    tier = "SILVER",
                    badgeColorHex = "#F97316",
                    iconName = "HourglassTop",
                    isUnlocked = false,
                    currentProgress = 2,
                    targetProgress = 4
                ),
                MilestoneEntity(
                    id = "prod_diagram_1",
                    title = "Master Architect",
                    description = "Create or customize a visual flowchart or planning diagram",
                    category = "PRODUCTIVITY",
                    tier = "BRONZE",
                    badgeColorHex = "#8B5CF6",
                    iconName = "AccountTree",
                    isUnlocked = true,
                    unlockedAtMillis = now - 86400000L * 6,
                    currentProgress = 1,
                    targetProgress = 1
                ),
                MilestoneEntity(
                    id = "prod_tasks_10",
                    title = "Task Terminator",
                    description = "Complete 10 scheduled tasks",
                    category = "PRODUCTIVITY",
                    tier = "GOLD",
                    badgeColorHex = "#EC4899",
                    iconName = "CheckCircle",
                    isUnlocked = false,
                    currentProgress = 4,
                    targetProgress = 10
                )
            )
            database.milestoneDao().insertMilestones(defaultMilestones)
        }
    }
}
