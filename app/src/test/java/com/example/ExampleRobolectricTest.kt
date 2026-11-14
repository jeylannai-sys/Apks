package com.example

import com.example.data.TaskEntity
import com.example.model.AppCurrency
import com.example.model.ExpenseCategories
import com.example.model.Priority
import com.example.model.RingtoneOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskPulseLogicTest {

    @Test
    fun testPriorityFromString() {
        assertEquals(Priority.HIGH, Priority.fromString("HIGH"))
        assertEquals(Priority.LOW, Priority.fromString("low"))
        assertEquals(Priority.MEDIUM, Priority.fromString("unknown"))
    }

    @Test
    fun testRingtoneOptions() {
        val radar = RingtoneOption.fromId("RADAR_PULSE")
        assertEquals(RingtoneOption.RADAR_PULSE, radar)
        assertEquals("Radar Pulse", radar.displayName)

        val chime = RingtoneOption.fromId("DIGITAL_CHIME")
        assertEquals(RingtoneOption.DIGITAL_CHIME, chime)
    }

    @Test
    fun testExpenseCategories() {
        val food = ExpenseCategories.getCategory("Food & Dining")
        assertEquals("Food & Dining", food.name)
        assertTrue(food.defaultBudget > 0)
    }

    @Test
    fun testCurrencyFormatting() {
        val php = AppCurrency.PHP
        assertEquals("PHP", php.code)
        assertEquals("₱", php.symbol)
        assertTrue(php.format(1500.0).contains("₱"))

        val usd = AppCurrency.USD
        assertEquals("$", usd.symbol)

        val hkd = AppCurrency.HKD
        assertEquals("HK$", hkd.symbol)
    }

    @Test
    fun testTaskRepeatScheduling() {
        val dailyTask = TaskEntity(
            title = "Morning Workout",
            repeatMode = "DAILY",
            repeatDaysBitmask = 127
        )
        assertTrue(dailyTask.isRepeating())
        assertEquals("Daily", dailyTask.getRepeatLabel())
        assertNotNull(dailyTask.calculateNextDueDate())

        val customTask = TaskEntity(
            title = "Team Review",
            repeatMode = "CUSTOM_DAYS",
            repeatDaysBitmask = 2 or 16 // Monday & Thursday
        )
        assertTrue(customTask.isRepeating())
        assertTrue(customTask.getRepeatLabel().contains("Mon"))
    }

    @Test
    fun testDiagramStylesAndPlanningLogic() {
        val styles = com.example.data.DiagramStyle.values()
        assertEquals(5, styles.size)
        assertTrue(styles.contains(com.example.data.DiagramStyle.CLASSIC_FLOWCHART))
        assertTrue(styles.contains(com.example.data.DiagramStyle.MIND_MAP))
        assertTrue(styles.contains(com.example.data.DiagramStyle.AGILE_ROADMAP))
        assertTrue(styles.contains(com.example.data.DiagramStyle.CYBERPUNK_NEON))
        assertTrue(styles.contains(com.example.data.DiagramStyle.STICKY_NOTES))

        val node = com.example.data.DiagramNodeEntity(
            diagramId = "d1",
            title = "Test MVP Build",
            type = com.example.data.NodeType.PROCESS.name,
            status = com.example.data.NodeStatus.IN_PROGRESS.name
        )
        assertEquals("Test MVP Build", node.title)
        assertEquals(com.example.data.NodeStatus.IN_PROGRESS.name, node.status)
    }

    @Test
    fun testStepsAndWalkingCalculatorLogic() {
        val distanceMeters = 1500.0
        val strideMeters = 0.75
        val steps = (distanceMeters / strideMeters).toInt()
        assertEquals(2000, steps)

        val paceStepsPerMinute = 100 // normal walking speed
        val walkTimeMinutes = steps / paceStepsPerMinute
        assertEquals(20, walkTimeMinutes)

        val calories = (steps * 0.045).toInt()
        assertEquals(90, calories)
    }

    @Test
    fun testTasksAndFinanceWidgetsRegistration() {
        val tasksProvider = com.example.widget.TasksWidgetProvider()
        val financeProvider = com.example.widget.FinanceWidgetProvider()
        val glanceProvider = com.example.widget.TaskPulseWidgetProvider()
        assertNotNull(tasksProvider)
        assertNotNull(financeProvider)
        assertNotNull(glanceProvider)
    }

    @Test
    fun testAppCurrencyFormattingForWidgets() {
        val php = com.example.model.AppCurrency.PHP
        assertEquals("₱1,250.00", php.format(1250.0))

        val usd = com.example.model.AppCurrency.USD
        assertEquals("$500.50", usd.format(500.50))

        val hkd = com.example.model.AppCurrency.HKD
        assertEquals("HK$80.00", hkd.format(80.0))

        val eur = com.example.model.AppCurrency.EUR
        assertEquals("€120.00", eur.format(120.0))
    }

    @Test
    fun testHabitStreakCalculation() {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        val todayStr = dateFormat.format(cal.time)

        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yestStr = dateFormat.format(cal.time)

        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val twoDaysAgoStr = dateFormat.format(cal.time)

        // 3 consecutive days completed
        val dates = setOf(todayStr, yestStr, twoDaysAgoStr)
        val (currentStreak, bestStreak) = com.example.data.calculateHabitStreaks(dates)
        assertEquals(3, currentStreak)
        assertTrue(bestStreak >= 3)

        // Empty dates
        val (zeroCurrent, zeroBest) = com.example.data.calculateHabitStreaks(emptySet())
        assertEquals(0, zeroCurrent)
        assertEquals(0, zeroBest)
    }

    @Test
    fun testFinancialStatementSummaryGeneration() {
        val transactions = listOf(
            com.example.data.ExpenseEntity(id = 1, title = "Salary", amount = 5000.0, category = "Salary", isExpense = false),
            com.example.data.ExpenseEntity(id = 2, title = "Groceries", amount = 850.0, category = "Food & Dining", isExpense = true),
            com.example.data.ExpenseEntity(id = 3, title = "Internet", amount = 150.0, category = "Utilities", isExpense = true)
        )
        val summary = com.example.util.FinancialExportHelper.generateStatementSummary(transactions, com.example.model.AppCurrency.USD)
        assertTrue(summary.contains("TASKPULSE FINANCIAL STATEMENT"))
        assertTrue(summary.contains("Total Income:     +$5,000.00"))
        assertTrue(summary.contains("Total Expenses:   -$1,000.00"))
        assertTrue(summary.contains("Net Savings:      +$4,000.00"))
        assertTrue(summary.contains("Food & Dining"))
    }

    @Test
    fun testSmartGoalSuggestions() {
        val transactions = listOf(
            com.example.data.ExpenseEntity(id = 1, title = "Groceries", amount = 900.0, category = "Food & Dining", isExpense = true)
        )
        val budgets = listOf(
            com.example.data.BudgetEntity(category = "Food & Dining", monthlyLimit = 1000.0)
        )
        val suggestions = com.example.ui.components.generateSmartGoalSuggestions(transactions, budgets, com.example.model.AppCurrency.PHP)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.id == "goal_food_cap" })
        assertTrue(suggestions.any { it.id == "goal_50_30_20" })
        assertTrue(suggestions.any { it.id == "goal_water_habit" })
    }

    @Test
    fun testPomodoroPhases() {
        assertEquals(25, com.example.ui.components.PomodoroPhase.FOCUS.defaultMinutes)
        assertEquals(5, com.example.ui.components.PomodoroPhase.SHORT_BREAK.defaultMinutes)
        assertEquals(15, com.example.ui.components.PomodoroPhase.LONG_BREAK.defaultMinutes)
    }

    @Test
    fun testMilestoneTiersAndCategories() {
        val habitMilestone = com.example.data.MilestoneEntity(
            id = "habit_streak_7",
            title = "Habit Master",
            description = "Maintain a 7-day streak on any habit",
            category = "HABIT",
            tier = "SILVER",
            iconName = "Whatshot",
            badgeColorHex = "#9C27B0",
            targetProgress = 7,
            currentProgress = 7,
            isUnlocked = true
        )
        assertEquals("SILVER", habitMilestone.tier)
        assertEquals("HABIT", habitMilestone.category)
        assertTrue(habitMilestone.isUnlocked)
        assertEquals(7, habitMilestone.targetProgress)
    }
}
