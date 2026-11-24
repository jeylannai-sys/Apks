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
    fun testHydrationDefaultsAndEverydayMax() {
        val freshDay = com.example.data.DailyHealthSummaryEntity(dateString = "2026-11-22")
        assertEquals(0, freshDay.waterIntakeMl)
        assertEquals(2500, freshDay.waterTargetMl)
        assertEquals(3700, freshDay.waterMaxLimitMl)
    }

    @Test
    fun testFinanceAmountPrivacyMask() {
        val usdFormatted = AppCurrency.USD.format(1250.75)
        assertEquals("$******", AppCurrency.USD.mask(usdFormatted, hidden = true))
        assertEquals("$1,250.75", AppCurrency.USD.mask(usdFormatted, hidden = false))

        val phpExpense = "-" + AppCurrency.PHP.format(850.0)
        assertEquals("-₱******", AppCurrency.PHP.mask(phpExpense, hidden = true))

        val phpIncome = "+" + AppCurrency.PHP.format(5000.0)
        assertEquals("+₱******", AppCurrency.PHP.mask(phpIncome, hidden = true))

        val hkdFormatted = AppCurrency.HKD.format(99.5)
        assertEquals("HK$******", AppCurrency.mask(hkdFormatted, hidden = true))
    }

    @Test
    fun testReportRemainingBalanceCalculation() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.data.AppDatabase.getInstance(ctx)
        val repo = com.example.data.TaskPulseRepository(ctx, db)
        val now = System.currentTimeMillis()
        val txs = listOf(
            com.example.data.ExpenseEntity(title = "Salary", amount = 10000.0, category = "Salary & Work", isExpense = false, dateMillis = now),
            com.example.data.ExpenseEntity(title = "Lunch", amount = 600.0, category = "Food & Dining", isExpense = true, dateMillis = now),
            com.example.data.ExpenseEntity(title = "Bus", amount = 150.0, category = "Transport", isExpense = true, dateMillis = now)
        )
        val budgets = listOf(
            com.example.data.BudgetEntity(category = "Food & Dining", monthlyLimit = 2000.0),
            com.example.data.BudgetEntity(category = "Transport", monthlyLimit = 500.0)
        )
        val report = repo.generateMonthlyReport(txs, budgets)
        assertEquals(9250.0, report.netSavings, 0.001)
        assertEquals(2500.0, report.totalBudgetLimit, 0.001)
        assertEquals(1750.0, report.remainingBudgetBalance, 0.001)
        val food = report.categoryBreakdown.first { it.category == "Food & Dining" }
        assertEquals(1400.0, food.remainingBalance, 0.001)
    }

    @Test
    fun testFlowchartShapesAndRefreshRatePreferences() {
        val shapes = com.example.data.NodeType.entries
        assertTrue(shapes.contains(com.example.data.NodeType.CIRCLE))
        assertTrue(shapes.contains(com.example.data.NodeType.DECISION))
        assertTrue(shapes.contains(com.example.data.NodeType.START_END))
        assertTrue(shapes.contains(com.example.data.NodeType.MILESTONE))

        assertEquals(
            com.example.util.RefreshRatePreference.MAX_ULTRA,
            com.example.util.RefreshRatePreference.entries.first()
        )
    }

    @Test
    fun testPrivateFileSafeCategorizationAndPinHash() {
        assertEquals(
            com.example.data.VaultFileCategory.VIDEO,
            com.example.util.PrivateFileSafeHelper.resolveCategory("video/mp4", "clip.mp4")
        )
        assertEquals(
            com.example.data.VaultFileCategory.IMAGE,
            com.example.util.PrivateFileSafeHelper.resolveCategory("image/jpeg", "photo.jpg")
        )
        assertEquals(
            com.example.data.VaultFileCategory.DOCUMENT,
            com.example.util.PrivateFileSafeHelper.resolveCategory("application/pdf", "tax.pdf")
        )
        assertEquals(
            com.example.data.VaultFileCategory.OTHER,
            com.example.util.PrivateFileSafeHelper.resolveCategory("application/zip", "archive.zip")
        )
        val h1 = com.example.util.PrivateFileSafeHelper.hashPin("1234")
        val h2 = com.example.util.PrivateFileSafeHelper.hashPin("1234")
        val h3 = com.example.util.PrivateFileSafeHelper.hashPin("4321")
        assertEquals(h1, h2)
        assertTrue(h1 != h3)
    }

    @Test
    fun testReceiptScannerJsonParsing() {
        val sampleJson = """
            {
              "merchant": "Fresh Mart Supermarket",
              "totalAmount": 48.95,
              "category": "Groceries",
              "dateIso": "2026-11-20",
              "notes": "Organic apples, milk, whole wheat bread"
            }
        """.trimIndent()
        val parsed = com.example.service.ReceiptScannerService.parseReceiptJson(sampleJson)
        assertEquals("Fresh Mart Supermarket", parsed.merchant)
        assertEquals(48.95, parsed.totalAmount, 0.001)
        assertEquals("Groceries", parsed.category)
        assertEquals("2026-11-20", parsed.dateIso)
        assertTrue(parsed.notes.contains("Organic apples"))
        assertTrue(com.example.service.ReceiptScannerService.parseDateIsoToMillis(parsed.dateIso) > 0L)
    }

    @Test
    fun testPrivateFileSafeAutoHideToggleAndMediaUriResolution() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(com.example.util.PrivateFileSafeHelper.isAutoHideFromGalleryEnabled(ctx))
        com.example.util.PrivateFileSafeHelper.setAutoHideFromGalleryEnabled(ctx, false)
        assertEquals(false, com.example.util.PrivateFileSafeHelper.isAutoHideFromGalleryEnabled(ctx))
        com.example.util.PrivateFileSafeHelper.setAutoHideFromGalleryEnabled(ctx, true)
        assertTrue(com.example.util.PrivateFileSafeHelper.isAutoHideFromGalleryEnabled(ctx))

        val pickerUri = android.net.Uri.parse("content://media/picker/0/com.android.providers.media.photopicker/media/42")
        val resolved = com.example.util.PrivateFileSafeHelper.resolveMediaStoreContentUri(ctx, pickerUri, "image/jpeg")
        assertNotNull(resolved)
        assertTrue(resolved.toString().endsWith("/42"))
    }

    @Test
    fun testBackgroundMusicPreferencesPersistence() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val initial = com.example.service.BackgroundMusicManager.init(ctx)
        assertTrue(initial.autoPlayOnOpen)
        assertTrue(initial.loop)

        com.example.service.BackgroundMusicManager.setVolume(ctx, 0.72f)
        com.example.service.BackgroundMusicManager.setLoop(ctx, false)
        com.example.service.BackgroundMusicManager.setAutoPlayOnOpen(ctx, true)
        com.example.service.BackgroundMusicManager.setPauseWhenLeavingApp(ctx, false)

        val current = com.example.service.BackgroundMusicManager.state.value
        assertEquals(0.72f, current.volume, 0.001f)
        assertEquals(false, current.loop)
        assertTrue(current.autoPlayOnOpen)
        assertEquals(false, current.pauseWhenLeavingApp)
    }

    @Test
    fun testHydrationReminderEvaluationAndDndRespect() {
        val now = 1_760_000_000_000L
        val twoHoursAgo = now - (2L * 60L * 60L * 1000L)
        val fourHoursAgo = now - (4L * 60L * 60L * 1000L)
        val daily = com.example.data.DailyHealthSummaryEntity(
            dateString = "2026-11-23",
            waterIntakeMl = 500,
            waterTargetMl = 2500,
            waterMaxLimitMl = 3700,
            stepsCount = 4200
        )
        val workouts = listOf(
            com.example.data.WorkoutLogEntity(
                activityName = "Running",
                durationMinutes = 35,
                caloriesBurned = 320,
                timestampMillis = now - 3600_000L
            )
        )

        // Under 3 hours -> should NOT notify
        val recentEval = com.example.service.HydrationReminderManager.evaluateHydrationStatus(
            nowMillis = now,
            lastIntakeMillis = twoHoursAgo,
            lastNudgeMillis = 0L,
            remindersEnabled = true,
            isDndActive = false,
            dailyHealth = daily,
            todayWorkouts = workouts,
            todayCalories = emptyList()
        )
        assertEquals(false, recentEval.shouldNotify)

        // >= 3 hours with workout -> should notify and suggest 500 ml
        val dueEval = com.example.service.HydrationReminderManager.evaluateHydrationStatus(
            nowMillis = now,
            lastIntakeMillis = fourHoursAgo,
            lastNudgeMillis = 0L,
            remindersEnabled = true,
            isDndActive = false,
            dailyHealth = daily,
            todayWorkouts = workouts,
            todayCalories = emptyList()
        )
        assertTrue(dueEval.shouldNotify)
        assertEquals(500, dueEval.suggestedIntakeMl)
        assertTrue(dueEval.message.contains("320 kcal"))

        // >= 3 hours with DND active -> must respect DND and suppress
        val dndEval = com.example.service.HydrationReminderManager.evaluateHydrationStatus(
            nowMillis = now,
            lastIntakeMillis = fourHoursAgo,
            lastNudgeMillis = 0L,
            remindersEnabled = true,
            isDndActive = true,
            dailyHealth = daily,
            todayWorkouts = workouts,
            todayCalories = emptyList()
        )
        assertEquals(false, dndEval.shouldNotify)
        assertTrue(dndEval.isDoNotDisturbActive)
        assertTrue(dndEval.reason.contains("Do Not Disturb"))

        // Integration test with ShadowNotificationManager
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        shadowNm.setNotificationPolicyAccessGranted(true)
        nm.cancelAll()

        // Reset intake to 4 hours ago
        com.example.service.HydrationReminderManager.recordWaterLogged(ctx, nowMillis = fourHoursAgo)

        // Enable DND -> no notification posted & suppressedByDnd becomes true
        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertTrue(com.example.service.HydrationReminderManager.isDoNotDisturbActive(ctx))
        val dndCheck = com.example.service.HydrationReminderManager.checkAndNotifyIfDue(
            context = ctx,
            dailyHealth = daily,
            todayWorkouts = workouts,
            todayCalories = emptyList(),
            nowMillis = now
        )
        assertEquals(false, dndCheck.shouldNotify)
        assertTrue(com.example.service.HydrationReminderManager.state.value.suppressedByDnd)
        assertEquals(null, shadowNm.getNotification(com.example.service.HydrationReminderManager.NOTIFICATION_ID))

        // Disable DND -> notification is posted & suppressedByDnd clears
        nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        val activeCheck = com.example.service.HydrationReminderManager.checkAndNotifyIfDue(
            context = ctx,
            dailyHealth = daily,
            todayWorkouts = workouts,
            todayCalories = emptyList(),
            nowMillis = now
        )
        assertTrue(activeCheck.shouldNotify)
        assertEquals(false, com.example.service.HydrationReminderManager.state.value.suppressedByDnd)
        assertNotNull(shadowNm.getNotification(com.example.service.HydrationReminderManager.NOTIFICATION_ID))

        // Logging water clears active notification and updates lastWaterIntakeMillis
        com.example.service.HydrationReminderManager.recordWaterLogged(ctx, nowMillis = now + 1000L)
        assertEquals(now + 1000L, com.example.service.HydrationReminderManager.state.value.lastWaterIntakeMillis)
        assertEquals(null, shadowNm.getNotification(com.example.service.HydrationReminderManager.NOTIFICATION_ID))
    }

    @Test
    fun testBudgetThresholdEvaluationAndNotifications() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        nm.cancelAll()

        val now = System.currentTimeMillis()
        val budgets = listOf(
            com.example.data.BudgetEntity(category = "Food & Dining", monthlyLimit = 1000.0),
            com.example.data.BudgetEntity(category = "Transport", monthlyLimit = 500.0)
        )

        // 790/1000 = 79% (< 80% default) -> no alert
        val underList = listOf(
            com.example.data.ExpenseEntity(id = 1, title = "Groceries", amount = 790.0, category = "Food & Dining", isExpense = true, dateMillis = now)
        )
        val defaultState = com.example.service.BudgetThresholdState(alertsEnabled = true, defaultThresholdPercent = 80)
        assertTrue(
            com.example.service.BudgetThresholdManager.evaluateCategoryThresholds(underList, budgets, defaultState, now)
                .none { it.category == "Food & Dining" }
        )

        // Per-category override (50% on Food & Dining) -> 79% triggers alert!
        val customState = defaultState.copy(categoryThresholds = mapOf("Food & Dining" to 50))
        val customAlerts = com.example.service.BudgetThresholdManager.evaluateCategoryThresholds(underList, budgets, customState, now)
        val foodAlert = customAlerts.firstOrNull { it.category == "Food & Dining" }
        assertNotNull(foodAlert)
        assertEquals(50, foodAlert!!.thresholdPercent)
        assertEquals(false, foodAlert.isOverBudget)

        // Default 80% crossed (850/1000 = 85%) -> notification posted & deduplicated
        com.example.service.BudgetThresholdManager.setAlertsEnabled(ctx, true)
        com.example.service.BudgetThresholdManager.setDefaultThresholdPercent(ctx, 80)
        com.example.service.BudgetThresholdManager.setCategoryThresholdPercent(ctx, "Food & Dining", null)

        val crossedList = listOf(
            com.example.data.ExpenseEntity(id = 2, title = "Dining Out", amount = 850.0, category = "Food & Dining", isExpense = true, dateMillis = now)
        )
        val firstRun = com.example.service.BudgetThresholdManager.checkAndNotifyThresholds(
            context = ctx,
            transactions = crossedList,
            budgets = budgets,
            currency = AppCurrency.USD,
            targetCategory = "Food & Dining",
            nowMillis = now
        )
        assertEquals(1, firstRun.count { it.category == "Food & Dining" })
        val countAfterFirst = shadowNm.allNotifications.size
        assertTrue(countAfterFirst >= 1)

        // Repeat call in same tier -> deduplicated (notification count stays the same)
        nm.cancelAll()
        com.example.service.BudgetThresholdManager.checkAndNotifyThresholds(
            context = ctx,
            transactions = crossedList,
            budgets = budgets,
            currency = AppCurrency.USD,
            targetCategory = "Food & Dining",
            nowMillis = now
        )
        assertEquals(0, shadowNm.allNotifications.size)

        // Drop back under threshold -> dedupe clears, then crossing again notifies
        com.example.service.BudgetThresholdManager.checkAndNotifyThresholds(
            context = ctx,
            transactions = underList,
            budgets = budgets,
            currency = AppCurrency.USD,
            targetCategory = "Food & Dining",
            nowMillis = now
        )
        com.example.service.BudgetThresholdManager.checkAndNotifyThresholds(
            context = ctx,
            transactions = crossedList,
            budgets = budgets,
            currency = AppCurrency.USD,
            targetCategory = "Food & Dining",
            nowMillis = now
        )
        assertEquals(1, shadowNm.allNotifications.size)
    }

    @Test
    fun testPrivateFileSafeOriginalPathAndGalleryRestore() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. Original path extraction from SAF externalstorage & file:// URIs
        val safDocUri = android.net.Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ADCIM%2FCamera%2Fvacation.jpg"
        )
        assertEquals(
            "DCIM/Camera/",
            com.example.util.PrivateFileSafeHelper.extractOriginalRelativePath(
                ctx,
                safDocUri,
                null,
                com.example.data.VaultFileCategory.IMAGE
            )
        )

        val fileUri = android.net.Uri.parse("file:///storage/emulated/0/Pictures/Screenshots/snap.png")
        assertEquals(
            "Pictures/Screenshots/",
            com.example.util.PrivateFileSafeHelper.extractOriginalRelativePath(
                ctx,
                fileUri,
                null,
                com.example.data.VaultFileCategory.IMAGE
            )
        )

        // 2. Restore relative path resolution preserves original Gallery folders and falls back safely
        val camPhoto = com.example.data.VaultFileEntity(
            id = "test-photo-1",
            displayName = "vacation.jpg",
            storedFileName = "test-photo-1.vault",
            mimeType = "image/jpeg",
            fileCategory = com.example.data.VaultFileCategory.IMAGE.name,
            sizeBytes = 12L,
            originalRelativePath = "DCIM/Camera/"
        )
        assertEquals("DCIM/Camera/", com.example.util.PrivateFileSafeHelper.resolveRestoreRelativePath(camPhoto))

        val unknownPhoto = camPhoto.copy(id = "test-photo-2", originalRelativePath = "")
        assertEquals("Pictures/TaskPulse Restored/", com.example.util.PrivateFileSafeHelper.resolveRestoreRelativePath(unknownPhoto))

        val originalVideo = camPhoto.copy(
            id = "test-video-1",
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            fileCategory = com.example.data.VaultFileCategory.VIDEO.name,
            originalRelativePath = "Movies/Vlogs/"
        )
        assertEquals("Movies/Vlogs/", com.example.util.PrivateFileSafeHelper.resolveRestoreRelativePath(originalVideo))

        // 3. End-to-end MediaStore Gallery restore writes exact bytes and marks destination path
        val storedFile = com.example.util.PrivateFileSafeHelper.getStoredFile(ctx, camPhoto)
        val payload = "FAKE_JPEG_RESTORE_DATA".toByteArray(Charsets.UTF_8)
        storedFile.writeBytes(payload)

        val outcome = com.example.util.PrivateFileSafeHelper.restoreVaultFileToGallery(ctx, camPhoto)
        assertTrue("Expected RestoredToGallery, got $outcome", outcome is com.example.util.VaultRestoreOutcome.RestoredToGallery)
        val restored = outcome as com.example.util.VaultRestoreOutcome.RestoredToGallery
        assertEquals("DCIM/Camera", restored.destinationPath)
        val restoredBytes = ctx.contentResolver.openInputStream(restored.restoredUri)?.use { it.readBytes() }
        assertNotNull(restoredBytes)
        assertEquals(String(payload, Charsets.UTF_8), String(restoredBytes!!, Charsets.UTF_8))

        // Cleanup test vault file
        storedFile.delete()
    }

    @Test
    fun testTransactionRenameAndReEditInRepository() = kotlinx.coroutines.runBlocking {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.data.AppDatabase.getInstance(ctx)
        val repo = com.example.data.TaskPulseRepository(ctx, db)

        val original = com.example.data.ExpenseEntity(
            title = "Coffee Shop",
            amount = 180.0,
            category = "Food & Dining",
            isExpense = true,
            note = "Latte"
        )
        val insertedId = repo.saveExpense(original, AppCurrency.PHP)
        assertTrue(insertedId > 0L)

        // Rename and re-edit using same primary key id
        val edited = original.copy(
            id = insertedId,
            title = "Artisan Espresso Bar",
            amount = 240.0,
            category = "Groceries",
            note = "Beans & pastry"
        )
        repo.saveExpense(edited, AppCurrency.PHP)

        val stored = db.expenseDao().getExpenseById(insertedId)
        assertNotNull(stored)
        assertEquals("Artisan Espresso Bar", stored!!.title)
        assertEquals(240.0, stored.amount, 0.001)
        assertEquals("Groceries", stored.category)
        assertEquals("Beans & pastry", stored.note)

        // Clean up
        repo.deleteExpense(stored, AppCurrency.PHP)
    }

    @Test
    fun testSupportReportEmailIntentAndGoogleProfileCache() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

        assertEquals("jeylann.ai@gmail.com", com.example.util.SupportReportHelper.SUPPORT_EMAIL)

        // 1. Verify email intent automatically targets jeylann.ai@gmail.com with formatted subject and body
        val intent = com.example.util.SupportReportHelper.buildSupportEmailIntent(
            type = com.example.util.SupportReportType.PROBLEM,
            reporterName = "Alex Rivera",
            reporterEmail = "alex@gmail.com",
            message = "Alarm ringtone volume resets after restart."
        )
        assertEquals(android.content.Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto:jeylann.ai@gmail.com", intent.dataString)
        val recipients = intent.getStringArrayExtra(android.content.Intent.EXTRA_EMAIL)
        assertNotNull(recipients)
        assertEquals("jeylann.ai@gmail.com", recipients!![0])
        val subject = intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT) ?: ""
        assertTrue(subject.contains("Report a Problem"))
        assertTrue(subject.contains("Alex Rivera"))
        val body = intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        assertTrue(body.contains("Name: Alex Rivera"))
        assertTrue(body.contains("Email: alex@gmail.com"))
        assertTrue(body.contains("Alarm ringtone volume resets after restart."))

        // 2. Verify Google ID token claim fallback parsing
        val header = android.util.Base64.encodeToString(
            """{"alg":"RS256"}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val payload = android.util.Base64.encodeToString(
            """{"name":"Jeylann User","email":"jeylann.ai@gmail.com"}""".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val (parsedName, parsedEmail) = com.example.util.SupportReportHelper.parseGoogleIdTokenClaims("$header.$payload.sig")
        assertEquals("Jeylann User", parsedName)
        assertEquals("jeylann.ai@gmail.com", parsedEmail)

        // 3. Verify Google reporter profile caching
        com.example.util.SupportReportHelper.saveCachedProfile(
            ctx,
            com.example.util.GoogleReporterProfile(name = parsedName, email = parsedEmail, isFromGoogle = true)
        )
        val cached = com.example.util.SupportReportHelper.loadCachedProfile(ctx)
        assertEquals("Jeylann User", cached.name)
        assertEquals("jeylann.ai@gmail.com", cached.email)
        assertTrue(cached.isFromGoogle)
    }

    @Test
    fun testGmailComposeDirectIntentCreation() {
        val intent = com.example.util.SupportReportHelper.buildGmailDirectComposeIntent(
            type = com.example.util.SupportReportType.SUGGESTION,
            reporterName = "Alex Rivera",
            reporterEmail = "alex@gmail.com",
            message = "Add weekly summary export."
        )
        assertEquals(android.content.Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("com.google.android.gm", intent.`package`)
        val recipients = intent.getStringArrayExtra(android.content.Intent.EXTRA_EMAIL)
        assertNotNull(recipients)
        assertEquals("jeylann.ai@gmail.com", recipients!![0])
        val subject = intent.getStringExtra(android.content.Intent.EXTRA_SUBJECT) ?: ""
        assertTrue(subject.contains("Suggestion"))
        assertTrue(subject.contains("Alex Rivera"))
        val body = intent.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        assertTrue(body.contains("Name: Alex Rivera"))
        assertTrue(body.contains("Email: alex@gmail.com"))
        assertTrue(body.contains("Add weekly summary export."))
    }

    @Test
    fun testZeroAndReducedBudgetAffectsProjectedMonthEndSummary() = kotlinx.coroutines.runBlocking {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = com.example.data.AppDatabase.getInstance(ctx)
        val repo = com.example.data.TaskPulseRepository(ctx, db)

        // 1. Decreasing a category budget all the way to 0.0 persists cleanly in Room
        repo.setBudget("Food & Dining", 1200.0, AppCurrency.PHP)
        repo.setBudget("Food & Dining", 0.0, AppCurrency.PHP)
        val stored = db.budgetDao().getBudgetForCategory("Food & Dining")
        assertNotNull(stored)
        assertEquals(0.0, stored!!.monthlyLimit, 0.001)

        // 2. Projected month-end summary reacts directly to reduced & zeroed category budgets
        val initialSummary = com.example.ui.components.computeProjectedMonthEndSummary(
            budgets = listOf(
                com.example.data.BudgetEntity("Food & Dining", 2000.0),
                com.example.data.BudgetEntity("Groceries", 1000.0)
            ),
            totalSpentSoFar = 300.0,
            currentDayOfMonth = 15,
            daysInMonth = 30
        )
        assertEquals(3000.0, initialSummary.totalBudgetLimit, 0.001)
        assertTrue(initialSummary.hasBudget)
        assertEquals(600.0, initialSummary.projectedTotal, 0.001)
        assertEquals(-2400.0, initialSummary.projectedDelta, 0.001) // 2400 saved

        // Decrease Food & Dining to 0 -> total budget drops to 1000 and saved drops to 400
        val reducedSummary = com.example.ui.components.computeProjectedMonthEndSummary(
            budgets = listOf(
                com.example.data.BudgetEntity("Food & Dining", 0.0),
                com.example.data.BudgetEntity("Groceries", 1000.0)
            ),
            totalSpentSoFar = 300.0,
            currentDayOfMonth = 15,
            daysInMonth = 30
        )
        assertEquals(1000.0, reducedSummary.totalBudgetLimit, 0.001)
        assertEquals(-400.0, reducedSummary.projectedDelta, 0.001) // 400 saved

        // Decrease all budgets to 0 with 0 spending -> must stay 0.0 (no fake 1500.0 saved)
        val allZeroIdle = com.example.ui.components.computeProjectedMonthEndSummary(
            budgets = listOf(
                com.example.data.BudgetEntity("Food & Dining", 0.0),
                com.example.data.BudgetEntity("Groceries", 0.0)
            ),
            totalSpentSoFar = 0.0,
            currentDayOfMonth = 15,
            daysInMonth = 30
        )
        assertEquals(0.0, allZeroIdle.totalBudgetLimit, 0.001)
        assertTrue(!allZeroIdle.hasBudget)
        assertEquals(0.0, allZeroIdle.projectedDelta, 0.001)

        // Decrease all budgets to 0 with active spending -> projectedDelta is positive (over 0 budget)
        val allZeroWithSpend = com.example.ui.components.computeProjectedMonthEndSummary(
            budgets = listOf(com.example.data.BudgetEntity("Food & Dining", 0.0)),
            totalSpentSoFar = 300.0,
            currentDayOfMonth = 15,
            daysInMonth = 30
        )
        assertEquals(0.0, allZeroWithSpend.totalBudgetLimit, 0.001)
        assertTrue(allZeroWithSpend.projectedDelta > 0.0)

        // 3. Explicit 0.0 limit overrides defaultBudget in BudgetThresholdManager (no false alert)
        val alerts = com.example.service.BudgetThresholdManager.evaluateCategoryThresholds(
            transactions = listOf(
                com.example.data.ExpenseEntity(
                    id = 99,
                    title = "Lunch",
                    amount = 350.0,
                    category = "Food & Dining",
                    isExpense = true,
                    dateMillis = System.currentTimeMillis()
                )
            ),
            budgets = listOf(com.example.data.BudgetEntity("Food & Dining", 0.0)),
            state = com.example.service.BudgetThresholdState(alertsEnabled = true, defaultThresholdPercent = 80),
            nowMillis = System.currentTimeMillis()
        )
        assertTrue(alerts.none { it.category == "Food & Dining" })
    }
}
