package com.example.service

import com.example.data.AppDatabase
import com.example.data.BudgetEntity
import com.example.data.DailyHealthSummaryEntity
import com.example.data.DiagramEntity
import com.example.data.ExpenseEntity
import com.example.data.HabitWithStats
import com.example.data.MilestoneEntity
import com.example.data.TaskEntity
import com.example.data.WorkoutLogEntity
import kotlinx.coroutines.flow.Flow

class MilestoneEvaluator(private val database: AppDatabase) {

    val milestonesFlow: Flow<List<MilestoneEntity>> = database.milestoneDao().getAllMilestones()

    /**
     * Evaluates all user activity data and updates milestones progress / unlocks badges.
     */
    suspend fun evaluateAllMilestones(
        habits: List<HabitWithStats>,
        transactions: List<ExpenseEntity>,
        budgets: List<BudgetEntity>,
        dailyHealth: DailyHealthSummaryEntity?,
        workouts: List<WorkoutLogEntity>,
        tasks: List<TaskEntity>,
        diagrams: List<DiagramEntity>
    ) {
        val now = System.currentTimeMillis()

        // 1. Habit Streaks evaluation
        val maxStreak = habits.maxOfOrNull { it.currentStreak } ?: 0
        val bestStreak = habits.maxOfOrNull { it.bestStreak } ?: 0
        val effectiveStreak = maxOf(maxStreak, bestStreak)

        checkAndUpdateProgress("streak_3", effectiveStreak, 3, now)
        checkAndUpdateProgress("streak_7", effectiveStreak, 7, now)
        checkAndUpdateProgress("streak_21", effectiveStreak, 21, now)

        // 2. Savings & Financial Goals evaluation
        val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
        val totalExpenses = transactions.filter { it.isExpense }.sumOf { it.amount }
        val netSavings = totalIncome - totalExpenses
        val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100).toInt() else 0

        if (netSavings > 0) {
            checkAndUpdateProgress("save_first", 1, 1, now)
        }
        if (savingsRate >= 20) {
            checkAndUpdateProgress("save_frugal_20", 1, 1, now)
        }

        // Budget Guardian: check if all budgeted categories are under limits
        val spentByCat = transactions.filter { it.isExpense }.groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
        val allUnder = budgets.isNotEmpty() && budgets.all { b ->
            (spentByCat[b.category] ?: 0.0) <= b.monthlyLimit
        }
        if (allUnder) {
            checkAndUpdateProgress("save_under_budget", 1, 1, now)
        }

        // 3. Fitness Targets evaluation
        val waterIntake = dailyHealth?.waterIntakeMl ?: 0
        checkAndUpdateProgress("fit_water_2500", waterIntake, 2500, now)

        val workoutCount = workouts.size
        checkAndUpdateProgress("fit_workouts_3", workoutCount, 3, now)

        val steps = dailyHealth?.stepsCount ?: 0
        checkAndUpdateProgress("fit_steps_10k", steps, 10000, now)

        // 4. Productivity & Planning Targets evaluation
        val completedTasksCount = tasks.count { it.isCompleted }
        checkAndUpdateProgress("prod_tasks_10", completedTasksCount, 10, now)

        if (diagrams.isNotEmpty()) {
            checkAndUpdateProgress("prod_diagram_1", diagrams.size, 1, now)
        }
    }

    private suspend fun checkAndUpdateProgress(
        id: String,
        current: Int,
        target: Int,
        now: Long
    ) {
        val milestone = database.milestoneDao().getMilestoneById(id) ?: return
        val clampedProgress = current.coerceAtMost(target)
        val shouldUnlock = current >= target

        if (milestone.isUnlocked && milestone.currentProgress == clampedProgress) {
            return
        }

        val updated = milestone.copy(
            currentProgress = clampedProgress,
            isUnlocked = milestone.isUnlocked || shouldUnlock,
            unlockedAtMillis = milestone.unlockedAtMillis ?: if (shouldUnlock) now else null
        )
        database.milestoneDao().updateMilestone(updated)
    }
}
